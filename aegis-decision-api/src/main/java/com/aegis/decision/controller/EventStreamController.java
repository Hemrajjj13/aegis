package com.aegis.decision.controller;

import com.aegis.decision.context.TenantContextHolder;
import com.aegis.decision.service.DecisionStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Server-Sent Events endpoint streaming evaluated decisions in real time per Document 06 §3.5 and Document 18 §6.
 */
@RestController
@RequestMapping("/v1")
public class EventStreamController {

    private final DecisionStreamService streamService;

    public EventStreamController(DecisionStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        UUID tenantId = TenantContextHolder.getTenantId();
        return streamService.subscribe(tenantId);
    }
}
