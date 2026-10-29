package com.aegis.identity.controller;

import com.aegis.core.context.TenantContextHolder;
import com.aegis.identity.crypto.Argon2Service;
import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.crypto.TotpService;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.model.InviteStatus;
import com.aegis.identity.model.OtpPurpose;
import com.aegis.identity.model.Role;
import com.aegis.identity.model.TenantUser;
import com.aegis.identity.model.UserStatus;
import com.aegis.identity.port.IdentityProvider;
import com.aegis.identity.port.NotificationService;
import com.aegis.identity.port.RateLimiter;
import com.aegis.identity.repository.*;
import com.aegis.identity.security.JwtAuthenticationToken;
import com.aegis.identity.service.AuthAuditService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping({"/v1/auth", "/auth"})
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final IdentityProvider identityProvider;
    private final AuthUserRepository userRepository;
    private final CredentialsRepository credentialsRepository;
    private final TotpSecretRepository totpRepository;
    private final InviteRepository inviteRepository;
    private final OtpRepository otpRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final TotpService totpService;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final AuthAuditService auditService;
    private final Argon2Service argon2Service;

    // Cache unverified signup initiation states
    private final ConcurrentHashMap<String, SignupInitiateRequest> pendingSignups = new ConcurrentHashMap<>();
    // Cache pending TOTP setup secrets before verify
    private final ConcurrentHashMap<UUID, String> pendingTotpSecrets = new ConcurrentHashMap<>();

    public AuthController(
            IdentityProvider identityProvider,
            AuthUserRepository userRepository,
            CredentialsRepository credentialsRepository,
            TotpSecretRepository totpRepository,
            InviteRepository inviteRepository,
            OtpRepository otpRepository,
            TenantRepository tenantRepository,
            JwtService jwtService,
            TotpService totpService,
            NotificationService notificationService,
            RateLimiter rateLimiter,
            AuthAuditService auditService,
            Argon2Service argon2Service
    ) {
        this.identityProvider = identityProvider;
        this.userRepository = userRepository;
        this.credentialsRepository = credentialsRepository;
        this.totpRepository = totpRepository;
        this.inviteRepository = inviteRepository;
        this.otpRepository = otpRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.totpService = totpService;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.argon2Service = argon2Service;
    }

    @PostMapping("/signup/initiate")
    public ResponseEntity<?> initiateSignup(@RequestBody SignupInitiateRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        if (!rateLimiter.tryAcquire("signup:ip:" + ip, 3, Duration.ofHours(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Maximum 3 signups per hour."));
        }

        if (request.password() == null || request.password().length() < 12) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 12 characters."));
        }

        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        String normalizedMobile = request.mobile() != null ? request.mobile().trim() : "";

        if (userRepository.existsByEmail(normalizedEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already in use."));
        }
        if (userRepository.existsByMobile(normalizedMobile)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mobile number already in use."));
        }

        String challengeId = UUID.randomUUID().toString();
        pendingSignups.put(challengeId, request);

        String otp = CryptoUtils.generate6DigitCode();
        String otpHash = CryptoUtils.sha256Hex(otp);
        Instant exp = Instant.now().plus(5, ChronoUnit.MINUTES);

        otpRepository.createOtp(null, normalizedMobile, OtpPurpose.SIGNUP_MOBILE_VERIFY.name(), otpHash, exp);
        notificationService.sendOtp(normalizedMobile, "SIGNUP_MOBILE_VERIFY", otp, 300);

        return ResponseEntity.ok(new SignupInitiateResponse(challengeId, 60));
    }

    @PostMapping("/signup/verify-otp")
    public ResponseEntity<?> verifySignupOtp(@RequestBody SignupVerifyOtpRequest request) {
        SignupInitiateRequest signupData = pendingSignups.get(request.challenge_id());
        if (signupData == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired signup challenge."));
        }

        String normalizedMobile = signupData.mobile().trim();
        Optional<OtpRepository.OtpRecord> otpOpt = otpRepository.findLatestActive(normalizedMobile, OtpPurpose.SIGNUP_MOBILE_VERIFY.name());
        if (otpOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP."));
        }

        var otpRecord = otpOpt.get();
        otpRepository.incrementAttempt(otpRecord.otpId());
        if (otpRecord.attemptCount() + 1 > otpRecord.maxAttempts()) {
            otpRepository.markConsumed(otpRecord.otpId());
            pendingSignups.remove(request.challenge_id());
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum OTP attempts exceeded."));
        }

        String providedHash = CryptoUtils.sha256Hex(request.otp_code().trim());
        if (!CryptoUtils.constantTimeEquals(providedHash, otpRecord.otpHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP code."));
        }

        otpRepository.markConsumed(otpRecord.otpId());
        pendingSignups.remove(request.challenge_id());

        // Create tenant and owner user
        UUID tenantId = UUID.randomUUID();
        tenantRepository.createTenant(tenantId, signupData.organization_name().toLowerCase().replaceAll("[^a-z0-9]", "-"), signupData.organization_name());

        UUID userId = UUID.randomUUID();
        var user = userRepository.createUser(
                userId, tenantId, signupData.email().trim().toLowerCase(),
                normalizedMobile, signupData.owner_name().trim(),
                Role.owner.name(), UserStatus.PENDING_ONBOARDING.name(), false
        );

        String pwdHash = argon2Service.hashPassword(signupData.password());
        credentialsRepository.save(userId, pwdHash);

        String setupToken = jwtService.issueSetupToken(userId, tenantId);
        return ResponseEntity.ok(new SignupVerifyOtpResponse(setupToken, "MFA_SETUP_REQUIRED"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        if (!rateLimiter.tryAcquire("login:ip:" + ip, 10, Duration.ofMinutes(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Too many login attempts."));
        }

        try {
            AuthResult result = identityProvider.authenticate(null, request.identifier(), request.password());
            return ResponseEntity.ok(new LoginResponse(result.mfaRequired(), result.mfaToken(), null, null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "urn:problem:unauthorized", "title", "Unauthorized", "status", 401, "detail", e.getMessage()));
        }
    }

    @PostMapping("/totp/challenge")
    public ResponseEntity<?> verifyTotpChallenge(@RequestBody TotpChallengeRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        JwtService.TokenClaims claims;
        try {
            claims = jwtService.verifyAndParse(request.mfa_token());
            if (!claims.mfaPending()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid MFA token."));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid MFA token: " + e.getMessage()));
        }

        UUID userId = claims.userId();
        String code = request.totp_code() != null ? request.totp_code() : request.recovery_code();

        boolean valid = identityProvider.verifyTotp(userId, code);
        if (!valid) {
            int failures = rateLimiter.recordFailure("totp:user:" + userId);
            if (failures >= 5) {
                rateLimiter.resetFailures("totp:user:" + userId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Maximum TOTP attempts exceeded. Please login again."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid TOTP or recovery code."));
        }

        rateLimiter.resetFailures("totp:user:" + userId);

        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found."));
        }
        var user = userOpt.get();

        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        UserSession session = identityProvider.createSession(userId, user.tenantId(), ip, userAgent);

        setRefreshTokenCookie(httpResponse, session.refresh_token_hash()); // session record holds raw token in refresh_token_hash during creation

        String accessToken = jwtService.issueAccessToken(user.toTenantUser(), user.tenantId());
        auditService.recordAuthEvent(user.tenantId(), userId.toString(), user.role(),
                ip, "LOGIN_SUCCESS", "USER", userId.toString(), Map.of("method", "PASSWORD_AND_TOTP"));

        return ResponseEntity.ok(new AuthSuccess(accessToken, "Bearer", 900, user.toTenantUser()));
    }

    @PostMapping("/totp/setup")
    public ResponseEntity<?> setupTotp(Authentication auth, HttpServletRequest request) {
        UUID userId = extractUserId(auth);
        if (userId == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    var claims = jwtService.verifyAndParse(authHeader.substring(7).trim());
                    userId = claims.userId();
                } catch (Exception ignored) {}
            }
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
        }

        String secret = totpService.generateSecret();
        pendingTotpSecrets.put(userId, secret);
        String uri = totpService.buildOtpAuthUri(secret, userOpt.get().email());

        return ResponseEntity.ok(new TotpSetupResponse(secret, uri));
    }

    @PostMapping("/totp/verify")
    public ResponseEntity<?> confirmTotpSetup(@RequestBody TotpVerifyRequest request, Authentication auth, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(auth);
        if (userId == null) {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    var claims = jwtService.verifyAndParse(authHeader.substring(7).trim());
                    userId = claims.userId();
                } catch (Exception ignored) {}
            }
        }
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String secret = pendingTotpSecrets.get(userId);
        if (secret == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "TOTP setup not initiated."));
        }

        if (!totpService.verifyCodeString(secret, request.totp_code())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid TOTP code."));
        }

        var encrypted = totpService.encryptSecret(secret);
        var recoveryBundle = totpService.generateRecoveryCodes();

        totpRepository.save(userId, encrypted.ciphertext(), encrypted.iv(), recoveryBundle.hashedCodes());
        userRepository.updateStatusAndTotp(userId, UserStatus.ACTIVE.name(), true);
        pendingTotpSecrets.remove(userId);

        return ResponseEntity.ok(new TotpVerifyResponse("VERIFIED"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = "X-Aegis-CSRF", required = false) String aegisCsrf,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        // CSRF defence: custom header required
        if ((aegisCsrf == null || aegisCsrf.isBlank()) && (requestedWith == null || requestedWith.isBlank())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("type", "urn:problem:forbidden", "title", "Forbidden", "status", 403, "detail", "Missing mandatory CSRF header (X-Aegis-CSRF or X-Requested-With)."));
        }

        String rawRefreshToken = extractRefreshTokenFromCookie(httpRequest);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "urn:problem:unauthorized", "title", "Unauthorized", "status", 401, "detail", "Missing refresh token cookie."));
        }

        try {
            UserSession session = identityProvider.refreshSession(rawRefreshToken);
            var userOpt = userRepository.findById(session.user_id());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            var user = userOpt.get();
            setRefreshTokenCookie(httpResponse, session.refresh_token_hash()); // holds new raw token

            String accessToken = jwtService.issueAccessToken(user.toTenantUser(), user.tenantId());
            return ResponseEntity.ok(new AuthSuccess(accessToken, "Bearer", 900, user.toTenantUser()));
        } catch (SecurityException e) {
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("type", "urn:problem:unauthorized", "title", "Unauthorized", "status", 401, "detail", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawRefreshToken = extractRefreshTokenFromCookie(httpRequest);
        if (rawRefreshToken != null) {
            identityProvider.revokeSession(rawRefreshToken);
        }
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        var userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        return ResponseEntity.ok(userOpt.get().toTenantUser());
    }

    @GetMapping("/invite/verify")
    public ResponseEntity<?> verifyInvite(@RequestParam("token") String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Missing token"));
        }

        String hash = CryptoUtils.sha256Hex(token.trim());
        var inviteOpt = inviteRepository.findByTokenHash(hash);

        if (inviteOpt.isEmpty() || !InviteStatus.PENDING.name().equals(inviteOpt.get().status()) || Instant.now().isAfter(inviteOpt.get().expiresAt())) {
            return ResponseEntity.ok(new InviteVerifyResponse(false, null, null, null));
        }

        var invite = inviteOpt.get();
        String tenantName = tenantRepository.findById(invite.tenantId()).map(TenantRepository.TenantRecord::name).orElse("Aegis Tenant");

        return ResponseEntity.ok(new InviteVerifyResponse(true, invite.email(), invite.role(), tenantName));
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<?> acceptInvite(@RequestBody InviteAcceptRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String hash = CryptoUtils.sha256Hex(request.token().trim());
        var inviteOpt = inviteRepository.findByTokenHash(hash);

        if (inviteOpt.isEmpty() || !InviteStatus.PENDING.name().equals(inviteOpt.get().status()) || Instant.now().isAfter(inviteOpt.get().expiresAt())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired invitation token."));
        }

        if (request.password() == null || request.password().length() < 12) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 12 characters."));
        }

        var invite = inviteOpt.get();
        String secret = request.totp_secret() != null ? request.totp_secret() : totpService.generateSecret();

        if (!totpService.verifyCodeString(secret, request.totp_code())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid TOTP verification code."));
        }

        UUID userId = UUID.randomUUID();
        var user = userRepository.createUser(
                userId, invite.tenantId(), invite.email(),
                request.mobile().trim(), request.full_name().trim(),
                invite.role(), UserStatus.ACTIVE.name(), true
        );

        String pwdHash = argon2Service.hashPassword(request.password());
        credentialsRepository.save(userId, pwdHash);

        var encrypted = totpService.encryptSecret(secret);
        var recoveryBundle = totpService.generateRecoveryCodes();
        totpRepository.save(userId, encrypted.ciphertext(), encrypted.iv(), recoveryBundle.hashedCodes());

        inviteRepository.markAccepted(invite.inviteId());

        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        UserSession session = identityProvider.createSession(userId, invite.tenantId(), ip, userAgent);

        setRefreshTokenCookie(httpResponse, session.refresh_token_hash());
        String accessToken = jwtService.issueAccessToken(user.toTenantUser(), invite.tenantId());

        auditService.recordAuthEvent(invite.tenantId(), userId.toString(), invite.role(),
                ip, "INVITE_ACCEPTED", "USER", userId.toString(), Map.of("email", invite.email()));

        return ResponseEntity.ok(new AuthSuccess(accessToken, "Bearer", 900, user.toTenantUser()));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        identityProvider.initiatePasswordReset(request.identifier());
        // Always return generic timing-equalized message
        return ResponseEntity.ok(new ForgotPasswordResponse("If the account exists, a verification code has been dispatched."));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(
            @RequestBody ResetPasswordRequest request,
            @RequestHeader(value = "X-Aegis-TOTP", required = false) String totpHeader
    ) {
        try {
            var userOpt = userRepository.findByIdentifier(request.identifier());
            if (userOpt.isPresent()) {
                var user = userOpt.get();
                if (Role.owner.name().equalsIgnoreCase(user.role()) || Role.admin.name().equalsIgnoreCase(user.role())) {
                    if (totpHeader == null || !identityProvider.verifyTotp(user.userId(), totpHeader)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("error", "MFA confirmation code required for owner/admin password reset."));
                    }
                }
            }

            identityProvider.completePasswordReset(request.identifier(), request.otp_code(), request.new_password());
            return ResponseEntity.ok(new ResetPasswordResponse("PASSWORD_RESET_SUCCESSFUL"));
        } catch (SecurityException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", rawToken)
                .httpOnly(true)
                .secure(false) // allows localhost dev over http
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("refresh_token".equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserId();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

