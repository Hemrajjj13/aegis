package com.aegis.decision.context;

import java.util.UUID;

/**
 * Delegates to com.aegis.core.context.TenantContextHolder for backward compatibility.
 */
public final class TenantContextHolder {
    private TenantContextHolder() {}

    public static void setTenantId(UUID tenantId) {
        com.aegis.core.context.TenantContextHolder.setTenantId(tenantId);
    }

    public static UUID getTenantId() {
        return com.aegis.core.context.TenantContextHolder.getTenantId();
    }

    public static void clear() {
        com.aegis.core.context.TenantContextHolder.clear();
    }
}
