package com.aegis.decision.service;

import com.aegis.core.domain.EvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages active Server-Sent Events (SSE) connections for real-time decision telemetry.
 * Partitions streams by tenant ID and provides graceful subscription lifecycle.
 */
@Service
public class DecisionStreamService {
    private static final Logger log = LoggerFactory.getLogger(DecisionStreamService.class);

    private final Map<UUID, List<SseEmitter>> tenantEmitters = new ConcurrentHashMap<>();

    /**
     * Subscribes a client to the real-time decision event feed for the given tenant.
     */
    public SseEmitter subscribe(UUID tenantId) {
        // 30 minute timeout
        SseEmitter emitter = new SseEmitter(1800_000L);

        List<SseEmitter> emitters = tenantEmitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError(e -> removeEmitter(tenantId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE connection established for tenant " + tenantId));
        } catch (IOException e) {
            removeEmitter(tenantId, emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts an evaluated decision to all active subscribers for the tenant.
     */
    public void publishDecision(UUID tenantId, EvaluationResult decision) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("decision")
                        .id(decision.decisionId().toString())
                        .data(decision, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.debug("Failed to deliver SSE event to subscriber, removing emitter", e);
                removeEmitter(tenantId, emitter);
            }
        }
    }

    private void removeEmitter(UUID tenantId, SseEmitter emitter) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                tenantEmitters.remove(tenantId);
            }
        }
    }

    public int getActiveSubscriptionCount(UUID tenantId) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        return emitters != null ? emitters.size() : 0;
    }
}
