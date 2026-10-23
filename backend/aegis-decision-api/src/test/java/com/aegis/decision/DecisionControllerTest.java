package com.aegis.decision;

import com.aegis.decision.filter.TenantHeaderFilter;
import com.aegis.decision.service.DecisionStreamService;
import com.aegis.storage.service.DecisionPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestDecisionApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DecisionPersistenceService persistenceService;

    @MockBean
    private DecisionStreamService streamService;

    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setupMock() {
        when(persistenceService.persistDecisionAtomically(any(), any(), any(), any()))
                .thenAnswer(inv -> new DecisionPersistenceService.PersistedDecision(inv.getArgument(2), false));
    }

    @Test
    @DisplayName("POST /v1/decisions with amount >= 50,000 INR returns 200 OK with REVIEW outcome")
    void testAmountGreaterEqual50kReturnsReview() throws Exception {
        String payload = """
            {
                "event_id": "11111111-1111-1111-1111-111111111111",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 1,
                "tenant_id": "%s",
                "account_id_hash": "acc_hash_1",
                "card_token": "tok_visa_1234",
                "amount_minor_units": 6000000,
                "currency": "INR",
                "payment_method": "CARD",
                "merchant_id": "m_1",
                "merchant_name": "Electronics Hub",
                "channel": "WEB"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.outcome").value("REVIEW"))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.decision_id").isNotEmpty())
                .andExpect(jsonPath("$.hard_rule_fired").value(true))
                .andExpect(jsonPath("$.rules_fired[0].rule_id").value("STUB-HIGH-AMOUNT"));
    }

    @Test
    @DisplayName("POST /v1/decisions with amount < 50,000 INR returns 200 OK with APPROVE outcome")
    void testAmountLessThan50kReturnsApprove() throws Exception {
        String payload = """
            {
                "event_id": "22222222-2222-2222-2222-222222222222",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 2,
                "tenant_id": "%s",
                "account_id_hash": "acc_hash_2",
                "card_token": "tok_visa_5678",
                "amount_minor_units": 4500000,
                "currency": "INR",
                "payment_method": "CARD",
                "merchant_id": "m_2",
                "merchant_name": "Grocery Market",
                "channel": "POS"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.outcome").value("APPROVE"))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.decision_id").isNotEmpty());
    }

    @Test
    @DisplayName("POST /v1/decisions with raw PAN in free-text field is rejected with HTTP 400 and RFC 7807")
    void testRawPanInFreeTextIsRejected() throws Exception {
        // 4532015112830366 is a valid Visa test card number passing Luhn checksum
        String payload = """
            {
                "event_id": "33333333-3333-3333-3333-333333333333",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 3,
                "tenant_id": "%s",
                "account_id_hash": "acc_hash_3",
                "card_token": "tok_visa_9999",
                "amount_minor_units": 100000,
                "currency": "INR",
                "payment_method": "CARD",
                "merchant_id": "m_3",
                "merchant_name": "Store Note: card 4532015112830366",
                "channel": "WEB"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem:pan-prohibited"))
                .andExpect(jsonPath("$.title").value("PAN Prohibited"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Primary Account Numbers (PANs) are strictly prohibited in transaction payloads."))
                .andExpect(jsonPath("$.invalid_params[0].name").value("merchant_name"));
    }

    @Test
    @DisplayName("POST /v1/decisions missing X-Tenant-Id header returns HTTP 400 RFC 7807")
    void testMissingTenantHeaderReturns400() throws Exception {
        String payload = """
            {
                "event_id": "44444444-4444-4444-4444-444444444444",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 4,
                "tenant_id": "%s",
                "amount_minor_units": 10000,
                "currency": "INR",
                "payment_method": "UPI"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(post("/v1/decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem:bad-request"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Missing mandatory X-Tenant-Id header for multi-tenant isolation."))
                .andExpect(jsonPath("$.invalid_params[0].name").value("X-Tenant-Id"));
    }

    @Test
    @DisplayName("POST /v1/decisions with non-UUID X-Tenant-Id header returns RFC 7807 400")
    void testNonUuidTenantHeaderReturns400() throws Exception {
        String payload = """
            {
                "event_id": "55555555-5555-5555-5555-555555555555",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 5,
                "tenant_id": "%s",
                "amount_minor_units": 10000,
                "currency": "INR",
                "payment_method": "UPI"
            }
            """.formatted(TENANT_ID);

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", "not-a-valid-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem:bad-request"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("X-Tenant-Id must be a valid UUID format."))
                .andExpect(jsonPath("$.invalid_params[0].name").value("X-Tenant-Id"));
    }

    @Test
    @DisplayName("Duplicate POST (same event_id) must NOT broadcast a second SSE event")
    void testDuplicatePostDoesNotBroadcastSecondSseEvent() throws Exception {
        String payload = """
            {
                "event_id": "66666666-6666-6666-6666-666666666666",
                "event_time": "2026-10-22T12:00:00Z",
                "sequence_number": 6,
                "tenant_id": "%s",
                "amount_minor_units": 6000000,
                "currency": "INR",
                "payment_method": "CARD"
            }
            """.formatted(TENANT_ID);

        // First POST: new decision -> duplicate = false
        when(persistenceService.persistDecisionAtomically(any(), any(), any(), any()))
                .thenAnswer(inv -> new DecisionPersistenceService.PersistedDecision(inv.getArgument(2), false));

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        verify(streamService, times(1)).publishDecision(eq(UUID.fromString(TENANT_ID)), any());

        // Second POST: duplicate event -> duplicate = true
        when(persistenceService.persistDecisionAtomically(any(), any(), any(), any()))
                .thenAnswer(inv -> new DecisionPersistenceService.PersistedDecision(inv.getArgument(2), true));

        mockMvc.perform(post("/v1/decisions")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        // Verify broadcast count is still 1 (NOT called again on duplicate)
        verify(streamService, times(1)).publishDecision(eq(UUID.fromString(TENANT_ID)), any());
    }

    @Test
    @DisplayName("TenantHeaderFilter refuses to start under tier1 or tier2 profile")
    void testTenantHeaderFilterRefusesTier1OrTier2() {
        MockEnvironment tier1Env = new MockEnvironment();
        tier1Env.setActiveProfiles("tier1");
        TenantHeaderFilter filter1 = new TenantHeaderFilter(tier1Env);
        assertThatThrownBy(filter1::validateProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly prohibited under tier1 or tier2");

        MockEnvironment tier2Env = new MockEnvironment();
        tier2Env.setActiveProfiles("tier2");
        TenantHeaderFilter filter2 = new TenantHeaderFilter(tier2Env);
        assertThatThrownBy(filter2::validateProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly prohibited under tier1 or tier2");
    }
}
