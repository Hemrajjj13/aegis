package com.aegis.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthAuditService {
    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordAuthEvent(
            UUID tenantId,
            String actorId,
            String actorRole,
            String clientIp,
            String eventType,
            String targetEntityType,
            String targetEntityId,
            Map<String, Object> details
    ) {
        if (tenantId == null) {
            log.warn("Cannot record audit management event without tenant_id: {}", eventType);
            return;
        }

        try {
            // Set local RLS tenant setting for audit_management_events policy
            jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");

            String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "127.0.0.1";
            if (safeIp.contains("%")) {
                safeIp = safeIp.substring(0, safeIp.indexOf('%'));
            }

            String diffJson = details != null ? objectMapper.writeValueAsString(details) : "{}";

            String sql = """
                INSERT INTO audit_management_events (
                    tenant_id, actor_id, actor_role, client_ip, event_type,
                    target_entity_type, target_entity_id, diff_payload
                ) VALUES (?, ?, ?, CAST(? AS inet), ?, ?, ?, CAST(? AS jsonb))
            """;

            jdbcTemplate.update(sql,
                    tenantId,
                    actorId != null ? actorId : "anonymous",
                    actorRole != null ? actorRole : "anonymous",
                    safeIp,
                    eventType,
                    targetEntityType,
                    targetEntityId != null ? targetEntityId : "unknown",
                    diffJson
            );
        } catch (Exception e) {
            log.error("Failed to record auth management audit event {}: {}", eventType, e.getMessage(), e);
        }
    }
}

