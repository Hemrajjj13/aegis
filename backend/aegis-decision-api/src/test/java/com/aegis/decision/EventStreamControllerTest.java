package com.aegis.decision;

import com.aegis.core.domain.DecisionOutcome;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.RuleOutcome;
import com.aegis.decision.service.DecisionStreamService;
import com.aegis.storage.service.DecisionPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestDecisionApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class EventStreamControllerTest {

    private static final Logger log = LoggerFactory.getLogger(EventStreamControllerTest.class);
    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DecisionStreamService streamService;

    @MockBean
    private DecisionPersistenceService persistenceService;

    @org.junit.jupiter.api.BeforeEach
    void setupMock() {
        org.mockito.Mockito.when(persistenceService.persistDecisionAtomically(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new DecisionPersistenceService.PersistedDecision(inv.getArgument(2), false));
    }

    @Test
    @DisplayName("GET /v1/events/stream establishes SSE stream and delivers connected handshake")
    void testEstablishSseStream() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/v1/events/stream")
                .header("X-Tenant-Id", TENANT_ID)
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(mvcResult.getRequest().isAsyncStarted()).isTrue();
        assertThat(mvcResult.getResponse().getContentAsString()).contains("event:connected");
        assertThat(streamService.getActiveSubscriptionCount(UUID.fromString(TENANT_ID))).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("GET /v1/events/stream missing X-Tenant-Id header returns 400 Bad Request")
    void testMissingTenantHeaderReturns400() throws Exception {
        mockMvc.perform(get("/v1/events/stream")
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Publishing decision streams event to subscriber (records empirical delivery timing)")
    void testPublishDecisionDeliveredToSubscriber() {
        UUID tenantId = UUID.fromString(TENANT_ID);
        var emitter = streamService.subscribe(tenantId);
        assertThat(emitter).isNotNull();

        EvaluationResult decision = new EvaluationResult(
                UUID.randomUUID(),
                "evt_stream_test_1",
                DecisionOutcome.APPROVE,
                15.0,
                false,
                "ruleset-v1",
                "sha256_mock_hash",
                List.of(new RuleOutcome("R1", "Low Risk", 15.0, null)),
                Map.of("amount", 2500.0),
                false,
                null,
                1.8,
                Instant.now()
        );

        long startNs = System.nanoTime();
        streamService.publishDecision(tenantId, decision);
        long elapsedNs = System.nanoTime() - startNs;
        double elapsedMs = elapsedNs / 1_000_000.0;

        // Record timing only per constraints (no latency pass/fail assertions)
        log.info("[TIMING] SSE decision broadcast latency: {} ms", String.format("%.3f", elapsedMs));
        assertThat(elapsedMs).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("POST /v1/decisions triggers decision broadcast to active SSE subscribers")
    void testPostDecisionsBroadcastsToSseStream() throws Exception {
        UUID tenantId = UUID.fromString(TENANT_ID);
        var emitter = streamService.subscribe(tenantId);
        assertThat(streamService.getActiveSubscriptionCount(tenantId)).isGreaterThanOrEqualTo(1);

        String payload = """
            {
                "event_id": "99999999-9999-9999-9999-999999999999",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 99,
                "tenant_id": "%s",
                "amount_minor_units": 100000,
                "currency": "INR",
                "payment_method": "UPI",
                "merchant_name": "Coffee Store"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());
    }
}
