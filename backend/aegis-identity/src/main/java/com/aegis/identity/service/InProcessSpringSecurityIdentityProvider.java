package com.aegis.identity.service;

import com.aegis.identity.crypto.Argon2Service;
import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.crypto.TotpService;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.model.OtpPurpose;
import com.aegis.identity.model.TenantUser;
import com.aegis.identity.port.IdentityProvider;
import com.aegis.identity.port.NotificationService;
import com.aegis.identity.port.RateLimiter;
import com.aegis.identity.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Primary
public class InProcessSpringSecurityIdentityProvider implements IdentityProvider {
    private static final Logger log = LoggerFactory.getLogger(InProcessSpringSecurityIdentityProvider.class);

    private final AuthUserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final TotpSecretRepository totpRepository;
    private final SessionRepository sessionRepository;
    private final OtpRepository otpRepository;
    private final Argon2Service argon2Service;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final AuthAuditService auditService;

    public InProcessSpringSecurityIdentityProvider(
            AuthUserRepository userRepository,
            CredentialsRepository credentialsRepository,
            TotpSecretRepository totpRepository,
            SessionRepository sessionRepository,
            OtpRepository otpRepository,
            Argon2Service argon2Service,
            JwtService jwtService,
            TotpService totpService,
            NotificationService notificationService,
            RateLimiter rateLimiter,
            AuthAuditService auditService
    ) {
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.totpRepository = totpRepository;
        this.sessionRepository = sessionRepository;
        this.otpRepository = otpRepository;
        this.argon2Service = argon2Service;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public AuthResult authenticate(String tenantId, String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null) {
            throw new IllegalArgumentException("Missing credentials");
        }

        String normalizedId = identifier.trim().toLowerCase();

        // Check rate limiter lockout
        if (rateLimiter.isLocked("login:id:" + normalizedId)) {
            throw new SecurityException("Account is temporarily locked. Please try again later.");
        }

        Optional<AuthUserRepository.UserRecord> userOpt = userRepository.findByIdentifier(normalizedId);

        if (userOpt.isEmpty()) {
            // Equalize work for non-existent account
            argon2Service.verifyDummy(password);
            rateLimiter.recordFailure("login:id:" + normalizedId);
            throw new SecurityException("Invalid credentials");
        }

        var user = userOpt.get();

        // Check DB lockout
        if (user.lockoutUntil() != null && Instant.now().isBefore(user.lockoutUntil())) {
            throw new SecurityException("Account is temporarily locked. Please try again later.");
        }

        Optional<String> hashOpt = credentialsRepository.findPasswordHashByUserId(user.userId());
        if (hashOpt.isEmpty() || !argon2Service.matches(password, hashOpt.get())) {
            int failures = rateLimiter.recordFailure("login:id:" + normalizedId);
            Instant lockoutUntil = null;
            if (failures >= 5) {
                lockoutUntil = Instant.now().plus(30, ChronoUnit.MINUTES);
                rateLimiter.lock("login:id:" + normalizedId, Duration.ofMinutes(30));
                auditService.recordAuthEvent(user.tenantId(), user.userId().toString(), user.role(),
                        null, "USER_LOCKED", "USER", user.userId().toString(),
                        Map.of("reason", "Consecutive login failures"));
            }
            userRepository.recordFailedLogin(user.userId(), lockoutUntil);
            auditService.recordAuthEvent(user.tenantId(), user.userId().toString(), user.role(),
                    null, "LOGIN_FAILURE", "USER", user.userId().toString(),
                    Map.of("identifier", normalizedId));
            throw new SecurityException("Invalid credentials");
        }

        // Reset failures on successful password verification
        rateLimiter.resetFailures("login:id:" + normalizedId);
        userRepository.resetFailedLogins(user.userId());

        TenantUser tenantUser = user.toTenantUser();

        // If TOTP is enabled or user is ACTIVE/owner, issue MFA challenge token
        String mfaToken = jwtService.issueMfaChallengeToken(user.userId(), user.tenantId());
        return new AuthResult(true, true, mfaToken, tenantUser, null, null);
    }

    @Override
    @Transactional
    public boolean verifyTotp(UUID userId, String totpCode) {
        if (userId == null || totpCode == null || totpCode.isBlank()) {
            return false;
        }

        Optional<TotpSecretRepository.TotpSecretRecord> secretOpt = totpRepository.findByUserId(userId);
        if (secretOpt.isEmpty()) {
            return false;
        }

        var secretRecord = secretOpt.get();
        String base32Secret = totpService.decryptSecret(secretRecord.encryptedSecret(), secretRecord.iv());

        // Check standard 6-digit TOTP
        if (totpService.verifyCodeString(base32Secret, totpCode)) {
            return true;
        }

        // Check 10 hashed recovery codes
        String codeHash = CryptoUtils.sha256Hex(totpCode.trim().toUpperCase());
        for (String storedHash : secretRecord.recoveryCodeHashes()) {
            if (CryptoUtils.constantTimeEquals(codeHash, storedHash)) {
                totpRepository.removeRecoveryCode(userId, storedHash);
                log.info("Recovery code used and removed for user {}", userId);
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional
    public UserSession createSession(UUID userId, UUID tenantId, String ipAddress, String userAgent) {
        String rawRefreshToken = CryptoUtils.secureRandomHex(32); // 64 hex chars
        String hash = CryptoUtils.sha256Hex(rawRefreshToken);
        Instant exp = Instant.now().plus(7, ChronoUnit.DAYS);

        var sessionRecord = sessionRepository.createSession(userId, tenantId, hash, ipAddress, userAgent, exp);
        return new UserSession(
                sessionRecord.sessionId(),
                sessionRecord.userId(),
                sessionRecord.tenantId(),
                rawRefreshToken, // return raw token only in-memory to caller for setting cookie
                sessionRecord.ipAddress(),
                sessionRecord.userAgent(),
                sessionRecord.isRevoked()
        );
    }

    @Override
    @Transactional
    public UserSession refreshSession(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new SecurityException("Missing refresh token");
        }

        String hash = CryptoUtils.sha256Hex(rawRefreshToken.trim());
        Optional<SessionRepository.SessionRecord> sessionOpt = sessionRepository.findByRefreshTokenHash(hash);

        if (sessionOpt.isEmpty()) {
            throw new SecurityException("Invalid refresh token");
        }

        var session = sessionOpt.get();

        // Reuse detection: If presented refresh token was already revoked, revoke ALL user sessions!
        if (session.isRevoked()) {
            sessionRepository.revokeAllUserSessions(session.userId());
            auditService.recordAuthEvent(session.tenantId(), session.userId().toString(), "user",
                    null, "SESSION_REUSE_DETECTED", "SESSION", session.sessionId().toString(),
                    Map.of("action", "Revoked all active sessions due to token reuse"));
            throw new SecurityException("Token reuse detected: all active sessions invalidated");
        }

        if (Instant.now().isAfter(session.expiresAt())) {
            throw new SecurityException("Refresh token has expired");
        }

        // Rotate token
        String newRawToken = CryptoUtils.secureRandomHex(32);
        String newHash = CryptoUtils.sha256Hex(newRawToken);
        Instant newExp = Instant.now().plus(7, ChronoUnit.DAYS);

        sessionRepository.rotateRefreshToken(session.sessionId(), newHash, newExp);

        return new UserSession(
                session.sessionId(),
                session.userId(),
                session.tenantId(),
                newRawToken,
                session.ipAddress(),
                session.userAgent(),
                false
        );
    }

    @Override
    @Transactional
    public void revokeSession(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = CryptoUtils.sha256Hex(rawRefreshToken.trim());
        sessionRepository.findByRefreshTokenHash(hash).ifPresent(s -> {
            sessionRepository.revokeSession(s.sessionId());
        });
    }

    @Override
    @Transactional
    public void initiatePasswordReset(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String normalized = identifier.trim().toLowerCase();

        // Rate limit 1 request / 60 seconds
        if (!rateLimiter.tryAcquire("pwd_reset:" + normalized, 1, Duration.ofSeconds(60))) {
            log.warn("Password reset cooldown active for {}", normalized);
            return;
        }

        Optional<AuthUserRepository.UserRecord> userOpt = userRepository.findByIdentifier(normalized);
        if (userOpt.isEmpty()) {
            // Do not enumerate account existence
            return;
        }

        var user = userOpt.get();
        String code = CryptoUtils.generate6DigitCode();
        String codeHash = CryptoUtils.sha256Hex(code);
        Instant exp = Instant.now().plus(15, ChronoUnit.MINUTES);

        otpRepository.createOtp(user.tenantId(), normalized, OtpPurpose.PASSWORD_RESET_EMAIL.name(), codeHash, exp);
        notificationService.sendOtp(normalized, "PASSWORD_RESET", code, 900);
    }

    @Override
    @Transactional
    public void completePasswordReset(String identifier, String otpCode, String newPassword) {
        if (identifier == null || otpCode == null || newPassword == null) {
            throw new IllegalArgumentException("Missing required password reset fields");
        }

        if (newPassword.length() < 12) {
            throw new IllegalArgumentException("Password must be at least 12 characters");
        }

        String normalized = identifier.trim().toLowerCase();
        Optional<OtpRepository.OtpRecord> otpOpt = otpRepository.findLatestActive(normalized, OtpPurpose.PASSWORD_RESET_EMAIL.name());

        if (otpOpt.isEmpty()) {
            throw new SecurityException("Invalid or expired OTP");
        }

        var otp = otpOpt.get();
        otpRepository.incrementAttempt(otp.otpId());

        if (otp.attemptCount() + 1 > otp.maxAttempts()) {
            otpRepository.markConsumed(otp.otpId());
            throw new SecurityException("Maximum OTP attempts exceeded");
        }

        String providedHash = CryptoUtils.sha256Hex(otpCode.trim());
        if (!CryptoUtils.constantTimeEquals(providedHash, otp.otpHash())) {
            throw new SecurityException("Invalid or expired OTP");
        }

        otpRepository.markConsumed(otp.otpId());

        Optional<AuthUserRepository.UserRecord> userOpt = userRepository.findByIdentifier(normalized);
        if (userOpt.isEmpty()) {
            throw new SecurityException("User not found");
        }

        var user = userOpt.get();
        String newHash = argon2Service.hashPassword(newPassword);
        credentialsRepository.updatePassword(user.userId(), newHash);

        // Revocation cascade: immediately revoke all active sessions
        sessionRepository.revokeAllUserSessions(user.userId());

        auditService.recordAuthEvent(user.tenantId(), user.userId().toString(), user.role(),
                null, "PASSWORD_RESET", "USER", user.userId().toString(),
                Map.of("action", "Password reset successfully; all active sessions revoked"));
    }
}

