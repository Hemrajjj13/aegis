package com.aegis.identity.model;

import java.util.UUID;

public record TenantUser(
    UUID user_id,
    String email,
    String mobile,
    String full_name,
    String role,
    String status,
    boolean totp_enabled
) {}

