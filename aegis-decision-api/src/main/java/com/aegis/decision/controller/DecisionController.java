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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class DecisionController {
    private final EvaluationCore evaluationCore;

    public DecisionController(EvaluationCore evaluationCore) {
        this.evaluationCore = evaluationCore;
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
        EvaluationResult result = evaluationCore.evaluate(event, clock);

        DecisionResponse response = DecisionResponse.from(result);
        return ResponseEntity.ok(response);
    }

    private void scanForPan(String value, String fieldName) {
        if (value != null && LuhnPanScanner.containsLuhnValidPan(value)) {
            throw new RawPanProhibitedException(fieldName);
        }
    }
}
