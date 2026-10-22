package com.aegis.decision.controller;

import com.aegis.core.DeterministicClock;
import com.aegis.core.EvaluationCore;
import com.aegis.core.EventClock;
import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;
import com.aegis.decision.context.TenantContextHolder;
import com.aegis.decision.exception.RawPanProhibitedException;
import com.aegis.decision.model.DecisionResponse;
import com.aegis.decision.security.LuhnPanScanner;
import com.aegis.storage.service.DecisionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class DecisionController {
    private final EvaluationCore evaluationCore;
    private final DecisionPersistenceService persistenceService;
    private final com.aegis.decision.service.DecisionStreamService streamService;
    private final ObjectMapper objectMapper;

    public DecisionController(EvaluationCore evaluationCore,
                              DecisionPersistenceService persistenceService,
                              com.aegis.decision.service.DecisionStreamService streamService,
                              ObjectMapper objectMapper) {
        this.evaluationCore = evaluationCore;
        this.persistenceService = persistenceService;
        this.streamService = streamService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/decisions")
    public ResponseEntity<DecisionResponse> evaluateDecision(
            @RequestBody PaymentEvent event,
            HttpServletRequest request) {

        UUID currentTenant = TenantContextHolder.getTenantId();
        if (currentTenant != null && !currentTenant.equals(event.tenantId())) {
            event = new PaymentEvent(
                    event.eventId(),
                    event.eventTime(),
                    event.sequenceNumber(),
                    currentTenant,
                    event.accountIdHash(),
                    event.cardToken(),
                    event.amountMinorUnits(),
                    event.currency(),
                    event.paymentMethod(),
                    event.merchantId(),
                    event.merchantCategoryCode(),
                    event.merchantName(),
                    event.channel(),
                    event.ipAddress(),
                    event.deviceId(),
                    event.userAgent(),
                    event.upiVpaHash(),
                    event.customFields()
            );
        }

        // Secondary Control: Scan free-text and extension fields for Luhn-valid PANs (doc 01 §5.1)
        scanForPan(event.merchantName(), "merchant_name");
        if (event.customFields() != null) {
            for (var entry : event.customFields().entrySet()) {
                scanForPan(entry.getValue(), "custom_fields." + entry.getKey());
            }
        }

        EventClock clock = new DeterministicClock(event.eventTime());
        EvaluationResult evaluationResult = evaluationCore.evaluate(event, clock);

        String payloadHash = computePayloadHash(event);
        EvaluationResult finalResult = persistenceService.persistDecisionAtomically(
                event.tenantId(),
                event,
                evaluationResult,
                payloadHash
        );

        streamService.publishDecision(event.tenantId(), finalResult);

        DecisionResponse response = DecisionResponse.from(finalResult);
        return ResponseEntity.ok(response);
    }

    private void scanForPan(String value, String fieldName) {
        if (value != null && LuhnPanScanner.containsLuhnValidPan(value)) {
            throw new RawPanProhibitedException(fieldName);
        }
    }

    private String computePayloadHash(PaymentEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return "sha256_" + event.eventId();
        }
    }
}
