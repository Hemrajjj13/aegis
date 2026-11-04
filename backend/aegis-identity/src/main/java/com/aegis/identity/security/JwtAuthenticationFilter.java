package com.aegis.identity.security;

import com.aegis.core.context.TenantContextHolder;
import com.aegis.identity.crypto.JwtService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JdbcTemplate jdbcTemplate,
            Environment environment
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void validateAuthMode() {
        String authMode = environment.getProperty("aegis.auth.mode", "jwt");
        if ("dev-header".equalsIgnoreCase(authMode)) {
            if (isProfileActive("tier1") || isProfileActive("tier2")) {
                throw new IllegalStateException(
                    "FATAL: aegis.auth.mode=dev-header is strictly prohibited under tier1 or tier2 deployment profiles."
                );
            }
        }
    }

    private boolean isProfileActive(String profile) {
        if (environment == null || environment.getActiveProfiles() == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        UUID tenantIdToSet = null;

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                try {
                    JwtService.TokenClaims claims = jwtService.verifyAndParse(token);

                    // MFA challenge token only valid for TOTP challenge endpoint
                    String path = request.getRequestURI();
                    if (claims.mfaPending() && !path.endsWith("/auth/totp/challenge")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"MFA challenge token only valid for /auth/totp/challenge\"}");
                        return;
                    }

                    JwtAuthenticationToken authToken = JwtAuthenticationToken.of(
                            claims.userId(),
                            claims.tenantId(),
                            claims.role(),
                            claims.email()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    if (claims.tenantId() != null) {
                        tenantIdToSet = claims.tenantId();
                    }
                } catch (SecurityException e) {
                    log.debug("JWT verification failed: {}", e.getMessage());
                    // Allow security entry point to handle unauthenticated requests
                }
            } else {
                // Check dev-header mode (tier0 only)
                String authMode = environment.getProperty("aegis.auth.mode", "jwt");
                if ("dev-header".equalsIgnoreCase(authMode) && isProfileActive("tier0")) {
                    String path = request.getRequestURI();
                    if (!path.startsWith("/actuator") && !path.equals("/health") && !path.startsWith("/swagger") && !path.startsWith("/v3/api-docs") && !path.equals("/error") && !path.contains("/auth/")) {
                        String tenantHeader = request.getHeader("X-Tenant-Id");
                        if (tenantHeader == null || tenantHeader.isBlank()) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                "{\"type\":\"urn:problem:bad-request\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Missing mandatory X-Tenant-Id header for multi-tenant isolation.\",\"instance\":\"" + path + "\",\"invalid_params\":[{\"name\":\"X-Tenant-Id\",\"reason\":\"Missing mandatory X-Tenant-Id header for multi-tenant isolation.\"}]}"
                            );
                            return;
                        }

                        try {
                            tenantIdToSet = UUID.fromString(tenantHeader.trim());
                            JwtAuthenticationToken devAuth = JwtAuthenticationToken.of(
                                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                    tenantIdToSet,
                                    "owner",
                                    "dev@projectaegis.in"
                            );
                            SecurityContextHolder.getContext().setAuthentication(devAuth);
                        } catch (IllegalArgumentException e) {
                            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                "{\"type\":\"urn:problem:bad-request\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"X-Tenant-Id must be a valid UUID format.\",\"instance\":\"" + path + "\",\"invalid_params\":[{\"name\":\"X-Tenant-Id\",\"reason\":\"X-Tenant-Id must be a valid UUID format.\"}]}"
                            );
                            return;
                        }
                    }
                }
            }

            if (tenantIdToSet != null) {
                TenantContextHolder.setTenantId(tenantIdToSet);
                if (jdbcTemplate != null) {
                    try {
                        jdbcTemplate.execute("SET LOCAL app.current_tenant_id = '" + tenantIdToSet + "'");
                    } catch (Exception e) {
                        log.trace("Could not set local app.current_tenant_id: {}", e.getMessage());
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}

