package com.aegis.decision.context;

import java.util.UUID;

/**
 * ThreadLocal context holding authenticated tenant UUID for the current request.
 */
public final class TenantContextHolder {
    private static final ThreadLocal<UUID> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {}

    public static void setTenantId(UUID tenantId) {
        CONTEXT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
