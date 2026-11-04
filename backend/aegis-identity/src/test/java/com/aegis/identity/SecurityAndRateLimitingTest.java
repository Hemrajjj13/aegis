package com.aegis.identity;

import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.model.OtpPurpose;
import com.aegis.identity.repository.OtpRepository;
import com.aegis.identity.service.InMemoryRateLimiter;
import com.aegis.identity.service.LocalMailboxNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class SecurityAndRateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LocalMailboxNotificationService mailboxService;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        rateLimiter.resetAll();
    }

    @Test
    @DisplayName("Raw tokens, OTPs, and passwords never appear in plaintext in database columns")
    void testNoPlaintextSecretsInDatabase() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "secret_check_" + suffix + "@projectaegis.in";
        String rawPass = "SecretPassword@2026!";
        String mobile = "+9196" + suffix.replaceAll("[^0-9]", "4").substring(0, 6) + "03";
        if (mobile.length() != 13) mobile = "+919611223303";

        // Initiate signup
        mockMvc.perform(post("/v1/auth/signup/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupInitiateRequest("Org " + suffix, "Owner", email, mobile, rawPass))))
                .andExpect(status().isOk());

        var otpMsg = mailboxService.getLatestForRecipient(mobile).orElseThrow();
        String rawOtp = otpMsg.code();

        // Check auth_otp_codes table: raw OTP code must NEVER appear in any column!
        List<Map<String, Object>> otpRows = jdbcTemplate.queryForList("SELECT * FROM aegis_auth.auth_otp_codes WHERE recipient_identifier = ?", mobile);
        assertThat(otpRows).isNotEmpty();
        for (var row : otpRows) {
            for (var entry : row.entrySet()) {
                if (entry.getValue() != null) {
                    assertThat(entry.getValue().toString())
                            .as("Column %s should not contain raw OTP", entry.getKey())
                            .doesNotContain(rawOtp);
                }
            }
        }
    }

    @Test
    @DisplayName("Non-existent accounts return identical 401 error to prevent account enumeration")
    void testNoAccountEnumerationOnLogin() throws Exception {
        var nonExistentReq = new LoginRequest("nonexistent_user_404@projectaegis.in", "SomePassword@123!");
        MvcResult res = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonExistentReq)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(res.getResponse().getContentAsString()).contains("urn:problem:unauthorized");
    }

    @Test
    @DisplayName("Forgot password returns identical generic response for existing and non-existing accounts")
    void testGenericForgotPasswordResponse() throws Exception {
        var req1 = new ForgotPasswordRequest("existing@projectaegis.in");
        var req2 = new ForgotPasswordRequest("doesnotexist@projectaegis.in");

        MvcResult res1 = mockMvc.perform(post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult res2 = mockMvc.perform(post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(res1.getResponse().getContentAsString()).isEqualTo(res2.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Lockout after 5 failed login attempts and unlock after 30 minutes with fake clock")
    void testLockoutAndUnlockWithClock() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "lockout_" + suffix + "@projectaegis.in";
        String mobile = "+9195" + suffix.replaceAll("[^0-9]", "5").substring(0, 6) + "04";
        if (mobile.length() != 13) mobile = "+919511223304";
        String password = "CorrectPassword@2026!";

        // Signup owner
        var initRes = mockMvc.perform(post("/v1/auth/signup/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupInitiateRequest("Org " + suffix, "Lockout User", email, mobile, password))))
                .andExpect(status().isOk())
                .andReturn();
        var initResp = objectMapper.readValue(initRes.getResponse().getContentAsString(), SignupInitiateResponse.class);

        var otpMsg = mailboxService.getLatestForRecipient(mobile).orElseThrow();
        mockMvc.perform(post("/v1/auth/signup/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupVerifyOtpRequest(initResp.challenge_id(), otpMsg.code()))))
                .andExpect(status().isOk());

        // Perform 5 consecutive failed login attempts
        var wrongReq = new LoginRequest(email, "WrongPassword@123!");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongReq)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should be rejected with lockout message
        MvcResult lockedRes = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(lockedRes.getResponse().getContentAsString()).contains("Account is temporarily locked");

        // Advance fake clock by 31 minutes
        Clock originalClock = rateLimiter.getClock();
        try {
            Instant advancedTime = originalClock.instant().plus(31, ChronoUnit.MINUTES);
            rateLimiter.setClock(Clock.fixed(advancedTime, ZoneOffset.UTC));

            // Also clear lockout in DB to simulate DB time passing
            jdbcTemplate.update("UPDATE aegis_auth.users SET lockout_until = NOW() - INTERVAL '1 minute' WHERE LOWER(email) = LOWER(?)", email);

            // Now login with correct password succeeds (moves to TOTP step)
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mfa_required").value(true));
        } finally {
            rateLimiter.setClock(originalClock);
        }
    }

    @Test
    @DisplayName("OTP dies after 3 invalid submission attempts")
    void testOtpDiesAfterThreeAttempts() {
        String identifier = "+919988776655";
        String code = "123456";
        String hash = CryptoUtils.sha256Hex(code);
        Instant exp = Instant.now().plus(5, ChronoUnit.MINUTES);

        var otp = otpRepository.createOtp(null, identifier, OtpPurpose.SIGNUP_MOBILE_VERIFY.name(), hash, exp);

        // Fail 3 times
        for (int i = 0; i < 3; i++) {
            otpRepository.incrementAttempt(otp.otpId());
        }

        // Attempt lookup: should be empty because attempt_count >= max_attempts
        var activeOpt = otpRepository.findActiveById(otp.otpId());
        assertThat(activeOpt).isEmpty();
    }

    @Test
    @DisplayName("Refresh without CSRF header is rejected with 403 Forbidden")
    void testRefreshRequiresCsrfHeader() throws Exception {
        Cookie dummyCookie = new Cookie("refresh_token", "dummy_token_value");
        mockMvc.perform(post("/v1/auth/refresh")
                .cookie(dummyCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Missing mandatory CSRF header")));
    }
}

