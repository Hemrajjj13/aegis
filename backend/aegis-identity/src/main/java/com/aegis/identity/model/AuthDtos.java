package com.aegis.identity.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(String identifier, String password) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
        boolean mfa_required,
        String mfa_token,
        String access_token,
        Integer expires_in
    ) {}

    public record TotpChallengeRequest(
        String mfa_token,
        String totp_code,
        String recovery_code
    ) {}

    public record TotpSetupResponse(
        String secret_key,
        String otpauth_uri
    ) {}

    public record TotpVerifyRequest(
        String totp_code
    ) {}

    public record TotpVerifyResponse(
        String status
    ) {}

    public record AuthSuccess(
        String access_token,
        String token_type,
        int expires_in,
        TenantUser user
    ) {}

    public record SignupInitiateRequest(
        String organization_name,
        String owner_name,
        String email,
        String mobile,
        String password
    ) {}

    public record SignupInitiateResponse(
        String challenge_id,
        int resend_cooldown_seconds
    ) {}

    public record SignupVerifyOtpRequest(
        String challenge_id,
        String otp_code
    ) {}

    public record SignupVerifyOtpResponse(
        String setup_token,
        String state
    ) {}

    public record InviteRequest(
        String email,
        String role
    ) {}

    public record InviteResponse(
        UUID invite_id,
        String email,
        String status
    ) {}

    public record InviteVerifyResponse(
        boolean valid,
        String email,
        String role,
        String tenant_name
    ) {}

    public record InviteAcceptRequest(
        String token,
        String full_name,
        String mobile,
        String password,
        String totp_code,
        String totp_secret
    ) {}

    public record ForgotPasswordRequest(
        String identifier
    ) {}

    public record ForgotPasswordResponse(
        String message
    ) {}

    public record ResetPasswordRequest(
        String identifier,
        String otp_code,
        String new_password
    ) {}

    public record ResetPasswordResponse(
        String status
    ) {}

    public record UserSession(
        UUID session_id,
        UUID user_id,
        UUID tenant_id,
        String refresh_token_hash,
        String ip_address,
        String user_agent,
        boolean is_revoked
    ) {}

    public record AuthResult(
        boolean authenticated,
        boolean mfaRequired,
        String mfaToken,
        TenantUser user,
        String rawRefreshToken,
        String accessToken
    ) {}
}

