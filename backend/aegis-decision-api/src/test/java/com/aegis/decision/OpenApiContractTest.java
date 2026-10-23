package com.aegis.decision;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.RuleOutcome;
import com.aegis.decision.service.DecisionStreamService;
import com.aegis.storage.service.DecisionPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestDecisionApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
public class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DecisionStreamService streamService;

    @MockBean
    private DecisionPersistenceService persistenceService;

    private static Map<String, Object> openApiSpec;
    private static Map<String, Object> schemas;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadOpenApiSpecification() throws Exception {
        Path specPath = List.of(
                Path.of("../../api/openapi.yaml"),
                Path.of("../api/openapi.yaml"),
                Path.of("api/openapi.yaml"),
                Path.of("C:/dev/aegis/api/openapi.yaml")
        ).stream().filter(Files::exists).findFirst()
         .orElseThrow(() -> new IllegalStateException("Cannot locate api/openapi.yaml"));

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(specPath)) {
            openApiSpec = yaml.load(in);
        }

        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        schemas = (Map<String, Object>) components.get("schemas");
        assertThat(schemas).isNotNull();
    }

    @BeforeEach
    void setupMock() {
        when(persistenceService.persistDecisionAtomically(any(), any(), any(), any()))
                .thenAnswer(inv -> new DecisionPersistenceService.PersistedDecision(inv.getArgument(2), false));
    }

    @Test
    @DisplayName("Contract Test: POST /v1/decisions 200 response strictly matches #/components/schemas/DecisionResponse")
    void testPostDecisions200ConformsToContract() throws Exception {
        String payload = """
            {
                "event_id": "a0000000-0000-0000-0000-000000000001",
                "event_time": "2026-10-23T10:00:00Z",
                "sequence_number": 101,
                "tenant_id": "%s",
                "account_id_hash": "acc_contract_01",
                "card_token": "tok_visa_contract",
                "amount_minor_units": 7500000,
                "currency": "INR",
                "payment_method": "CARD",
                "merchant_id": "merch_contract_01",
                "merchant_category_code": "5094",
                "merchant_name": "Contract Jewelry",
                "channel": "WEB"
            }
            """.formatted(TENANT_ID);

        MvcResult result = mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        String responseJson = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseJson);

        validateAgainstSchema(jsonNode, "DecisionResponse");
    }

    @Test
    @DisplayName("Contract Test: POST /v1/decisions 400 for missing tenant matches #/components/schemas/ErrorResponse")
    void testPostDecisions400MissingTenantConformsToContract() throws Exception {
        String payload = """
            {
                "event_id": "b0000000-0000-0000-0000-000000000002",
                "event_time": "2026-10-23T10:00:00Z",
                "sequence_number": 102,
                "tenant_id": "%s",
                "amount_minor_units": 10000,
                "currency": "INR",
                "payment_method": "UPI"
            }
            """.formatted(TENANT_ID);

        MvcResult result = mockMvc.perform(post("/v1/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        validateAgainstSchema(jsonNode, "ErrorResponse");
    }

    @Test
    @DisplayName("Contract Test: POST /v1/decisions 400 for non-UUID tenant matches #/components/schemas/ErrorResponse")
    void testPostDecisions400InvalidTenantConformsToContract() throws Exception {
        String payload = """
            {
                "event_id": "c0000000-0000-0000-0000-000000000003",
                "event_time": "2026-10-23T10:00:00Z",
                "sequence_number": 103,
                "tenant_id": "%s",
                "amount_minor_units": 10000,
                "currency": "INR",
                "payment_method": "UPI"
            }
            """.formatted(TENANT_ID);

        MvcResult result = mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", "invalid_tenant_slug")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        validateAgainstSchema(jsonNode, "ErrorResponse");
    }

    @Test
    @DisplayName("Contract Test: POST /v1/decisions 400 for raw PAN matches #/components/schemas/ErrorResponse")
    void testPostDecisions400RawPanConformsToContract() throws Exception {
        String payload = """
            {
                "event_id": "d0000000-0000-0000-0000-000000000004",
                "event_time": "2026-10-23T10:00:00Z",
                "sequence_number": 104,
                "tenant_id": "%s",
                "amount_minor_units": 10000,
                "currency": "INR",
                "payment_method": "CARD",
                "merchant_name": "Card note 4532015112830366"
            }
            """.formatted(TENANT_ID);

        MvcResult result = mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        validateAgainstSchema(jsonNode, "ErrorResponse");
    }

    @Test
    @DisplayName("Contract Test: SSE 'decision' event data payload matches #/components/schemas/DecisionResponse")
    void testSseDecisionPayloadConformsToContract() throws Exception {
        UUID tenantId = UUID.fromString(TENANT_ID);
        var emitter = streamService.subscribe(tenantId);
        assertThat(emitter).isNotNull();

        EvaluationResult sampleDecision = new EvaluationResult(
                UUID.randomUUID(),
                "e0000000-0000-0000-0000-000000000005",
                DecisionOutcome.REVIEW,
                75.0,
                true,
                "default-ruleset",
                "0000000000000000000000000000000000000000000000000000000000000000",
                List.of(new RuleOutcome("STUB-HIGH-AMOUNT", "High amount threshold", 75.0, "AMOUNT_GE_50K")),
                Map.of("amount_inr", 75000.0),
                false,
                null,
                1.23,
                Instant.now()
        );

        // Convert the broadcast object to JSON as the SSE controller would
        var responseDto = com.aegis.decision.model.DecisionResponse.from(sampleDecision);
        String sseDataJson = objectMapper.writeValueAsString(responseDto);
        JsonNode jsonNode = objectMapper.readTree(sseDataJson);

        validateAgainstSchema(jsonNode, "DecisionResponse");
    }

    @SuppressWarnings("unchecked")
    private void validateAgainstSchema(JsonNode json, String schemaName) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        assertThat(schema)
                .withFailMessage("Schema '%s' not found in openapi.yaml", schemaName)
                .isNotNull();

        List<String> requiredFields = (List<String>) schema.get("required");
        if (requiredFields != null) {
            for (String field : requiredFields) {
                assertThat(json.has(field))
                        .withFailMessage("JSON missing required field '%s' for schema '%s': %s", field, schemaName, json)
                        .isTrue();
                assertThat(json.get(field).isNull())
                        .withFailMessage("Required field '%s' must not be null in schema '%s'", field, schemaName)
                        .isFalse();
            }
        }

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties != null) {
            properties.forEach((fieldName, propObj) -> {
                if (json.has(fieldName) && !json.get(fieldName).isNull()) {
                    validateProperty(json.get(fieldName), (Map<String, Object>) propObj, fieldName);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void validateProperty(JsonNode value, Map<String, Object> propDef, String fieldName) {
        String type = (String) propDef.get("type");
        String format = (String) propDef.get("format");
        List<String> enumValues = (List<String>) propDef.get("enum");

        if (type != null) {
            switch (type) {
                case "string" -> {
                    assertThat(value.isTextual())
                            .withFailMessage("Field '%s' expected string but got: %s", fieldName, value)
                            .isTrue();
                    if ("uuid".equals(format)) {
                        assertThat(isValidUuid(value.asText()))
                                .withFailMessage("Field '%s' with value '%s' is not a valid UUID", fieldName, value.asText())
                                .isTrue();
                    } else if ("date-time".equals(format)) {
                        assertThat(isValidDateTime(value.asText()))
                                .withFailMessage("Field '%s' with value '%s' is not a valid ISO date-time", fieldName, value.asText())
                                .isTrue();
                    }
                    if (enumValues != null) {
                        assertThat(enumValues).contains(value.asText());
                    }
                }
                case "integer" -> assertThat(value.isIntegralNumber())
                        .withFailMessage("Field '%s' expected integer: %s", fieldName, value)
                        .isTrue();
                case "number" -> {
                    assertThat(value.isNumber())
                            .withFailMessage("Field '%s' expected number: %s", fieldName, value)
                            .isTrue();
                    if (propDef.containsKey("minimum")) {
                        double min = ((Number) propDef.get("minimum")).doubleValue();
                        assertThat(value.asDouble()).isGreaterThanOrEqualTo(min);
                    }
                    if (propDef.containsKey("maximum")) {
                        double max = ((Number) propDef.get("maximum")).doubleValue();
                        assertThat(value.asDouble()).isLessThanOrEqualTo(max);
                    }
                }
                case "boolean" -> assertThat(value.isBoolean())
                        .withFailMessage("Field '%s' expected boolean: %s", fieldName, value)
                        .isTrue();
                case "array" -> {
                    assertThat(value.isArray())
                            .withFailMessage("Field '%s' expected array: %s", fieldName, value)
                            .isTrue();
                    Map<String, Object> items = (Map<String, Object>) propDef.get("items");
                    if (items != null) {
                        if (items.containsKey("$ref")) {
                            String ref = (String) items.get("$ref");
                            String targetSchema = ref.substring(ref.lastIndexOf('/') + 1);
                            for (JsonNode item : value) {
                                validateAgainstSchema(item, targetSchema);
                            }
                        } else if (items.containsKey("properties")) {
                            Map<String, Object> itemProps = (Map<String, Object>) items.get("properties");
                            for (JsonNode item : value) {
                                itemProps.forEach((pName, pDef) -> {
                                    if (item.has(pName)) {
                                        validateProperty(item.get(pName), (Map<String, Object>) pDef, pName);
                                    }
                                });
                            }
                        }
                    }
                }
                case "object" -> assertThat(value.isObject())
                        .withFailMessage("Field '%s' expected object: %s", fieldName, value)
                        .isTrue();
            }
        }
    }

    private boolean isValidUuid(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidDateTime(String str) {
        try {
            Instant.parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
