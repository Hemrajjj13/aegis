package com.aegis.storage.service;

import com.aegis.core.domain.EvaluationResult;
import com.aegis.core.domain.PaymentEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service SPI for synchronous decision persistence, RLS context scoping, and idempotency checking.
 */
public interface DecisionPersistenceService {

    /**
     * Sets RLS session variable for the current database transaction.
     */
    void setTenantContext(UUID tenantId);

    /**
     * Persists decision, processed_events idempotency record, and outbox event in ONE transaction.
     * If event_id has already been processed with matching payload, returns existing decision.
     */
    EvaluationResult persistDecisionAtomically(UUID tenantId, PaymentEvent event, EvaluationResult result, String payloadHash);

    /**
     * Finds decision by tenant and decision ID.
     */
    Optional<EvaluationResult> findDecisionById(UUID tenantId, UUID decisionId);

    /**
     * Finds all decisions for a given tenant.
     */
    List<EvaluationResult> findAllDecisionsForTenant(UUID tenantId);
}
