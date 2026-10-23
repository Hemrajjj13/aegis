package com.aegis.decision.filter;

import com.aegis.decision.context.TenantContextHolder;
import com.aegis.decision.model.ProblemDetailResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter extracting tenant identity from X-Tenant-Id header.
 * Permitted only under tier0. Refuses startup or throws under tier1/tier2 profiles.
 */
@Component
public class TenantHeaderFilter extends OncePerRequestFilter {
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantHeaderFilter(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProfile() {
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("tier1".equalsIgnoreCase(profile) || "tier2".equalsIgnoreCase(profile)) {
                    throw new IllegalStateException(
                        "FATAL: TenantHeaderFilter (X-Tenant-Id header authentication) is strictly prohibited under tier1 or tier2 deployment profiles."
                    );
                }
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        // Allow unauthenticated bypass for healthchecks and swagger/actuator
        if (path.startsWith("/actuator") || path.equals("/health") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (tenantHeader == null || tenantHeader.isBlank()) {
            writeProblemResponse(response, HttpServletResponse.SC_BAD_REQUEST, "X-Tenant-Id",
                    "Missing mandatory X-Tenant-Id header for multi-tenant isolation.", path);
            return;
        }

        UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantHeader.trim());
        } catch (IllegalArgumentException e) {
            writeProblemResponse(response, HttpServletResponse.SC_BAD_REQUEST, "X-Tenant-Id",
                    "X-Tenant-Id must be a valid UUID format.", path);
            return;
        }

        try {
            TenantContextHolder.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void writeProblemResponse(HttpServletResponse response, int status, String paramName, String detail, String instance)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        var problem = new ProblemDetailResponse(
                "urn:problem:bad-request",
                "Bad Request",
                status,
                detail,
                instance,
                List.of(new ProblemDetailResponse.InvalidParameter(paramName, detail))
        );
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
