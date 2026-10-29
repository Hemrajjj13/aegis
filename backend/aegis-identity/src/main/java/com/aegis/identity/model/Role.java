package com.aegis.identity.model;

public enum Role {
    owner,
    admin,
    risk_manager,
    rule_author,
    risk_analyst,
    auditor,
    developer;

    public static Role fromString(String val) {
        if (val == null) return null;
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(val)) return r;
        }
        throw new IllegalArgumentException("Unknown role: " + val);
    }
}

