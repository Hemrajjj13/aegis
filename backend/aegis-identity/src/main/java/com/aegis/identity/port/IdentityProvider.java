package com.aegis.identity.port;

import com.aegis.identity.model.AuthDtos.AuthResult;
import com.aegis.identity.model.AuthDtos.UserSession;
import java.util.UUID;

public interface IdentityProvider {
    AuthResult authenticate(String tenantId, String identifier, String password);
    boolean verifyTotp(UUID userId, String totpCode);
    UserSession createSession(UUID userId, UUID tenantId, String ipAddress, String userAgent);
    UserSession refreshSession(String rawRefreshToken);
    void revokeSession(String rawRefreshToken);
    void initiatePasswordReset(String identifier);
    void completePasswordReset(String identifier, String otpCode, String newPassword);
}

