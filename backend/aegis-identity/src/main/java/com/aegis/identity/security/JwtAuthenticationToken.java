package com.aegis.identity.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final UUID userId;
    private final UUID tenantId;
    private final String role;
    private final String email;

    public JwtAuthenticationToken(UUID userId, UUID tenantId, String role, String email, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
        this.email = email;
        setAuthenticated(true);
    }

    public static JwtAuthenticationToken of(UUID userId, UUID tenantId, String role, String email) {
        String authority = "ROLE_" + (role != null ? role.toUpperCase() : "ANONYMOUS");
        return new JwtAuthenticationToken(userId, tenantId, role, email, List.of(new SimpleGrantedAuthority(authority)));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }
}

