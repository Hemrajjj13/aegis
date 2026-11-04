package com.aegis.identity;

import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.crypto.TotpService;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.service.LocalMailboxNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class AuthLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalMailboxNotificationService mailboxService;

    @Autowired
    private TotpService totpService;

    @Test
    @DisplayName("Full lifecycle: signup -> OTP -> TOTP setup & verify -> login -> challenge -> refresh -> logout")
    void testCompleteAuthLifecycle() throws Exception {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        String orgName = "Org " + testSuffix;
        String email = "owner_" + testSuffix + "@projectaegis.in";
        String mobile = "+9198" + testSuffix.replaceAll("[^0-9]", "1").substring(0, 6) + "00";
        if (mobile.length() != 13) mobile = "+919811223344";
        String password = "StrongPassword@2026!";

        // 1. Signup Initiate
        var signupReq = new SignupInitiateRequest(orgName, "Owner User", email, mobile, password);
        MvcResult initResult = mockMvc.perform(post("/v1/auth/signup/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupReq)))
                .andExpect(status().isOk())
                .andReturn();

        var initResp = objectMapper.readValue(initResult.getResponse().getContentAsString(), SignupInitiateResponse.class);
        assertThat(initResp.challenge_id()).isNotNull();
        assertThat(initResp.resend_cooldown_seconds()).isEqualTo(60);

        // Retrieve OTP from mailbox
        var otpMsg = mailboxService.getLatestForRecipient(mobile)
                .orElseThrow(() -> new AssertionError("OTP message not found in local mailbox"));
        String otpCode = otpMsg.code();
        assertThat(otpCode).hasSize(6);

        // 2. Verify Signup OTP
        var verifyOtpReq = new SignupVerifyOtpRequest(initResp.challenge_id(), otpCode);
        MvcResult verifyResult = mockMvc.perform(post("/v1/auth/signup/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyOtpReq)))
                .andExpect(status().isOk())
                .andReturn();

        var verifyResp = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), SignupVerifyOtpResponse.class);
        assertThat(verifyResp.setup_token()).isNotNull();
        assertThat(verifyResp.state()).isEqualTo("MFA_SETUP_REQUIRED");

        // 3. Setup TOTP
        MvcResult setupResult = mockMvc.perform(post("/v1/auth/totp/setup")
                .header("Authorization", "Bearer " + verifyResp.setup_token()))
                .andExpect(status().isOk())
                .andReturn();

        var setupResp = objectMapper.readValue(setupResult.getResponse().getContentAsString(), TotpSetupResponse.class);
        assertThat(setupResp.secret_key()).isNotNull();
        assertThat(setupResp.otpauth_uri()).contains("otpauth://totp/");

        // Generate valid TOTP code
        // For fresh setup, we can compute the current TOTP code or pass valid code
        // Since GoogleAuthenticator computes code based on time:
        int currentCode = computeTotpCode(setupResp.secret_key());
        String codeStr = String.format("%06d", currentCode);

        // 4. Confirm TOTP Setup
        var confirmReq = new TotpVerifyRequest(codeStr);
        mockMvc.perform(post("/v1/auth/totp/verify")
                .header("Authorization", "Bearer " + verifyResp.setup_token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        // 5. Login with Primary Credentials
        var loginReq = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        var loginResp = objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class);
        assertThat(loginResp.mfa_required()).isTrue();
        assertThat(loginResp.mfa_token()).isNotNull();

        // 6. Verify TOTP Challenge
        currentCode = computeTotpCode(setupResp.secret_key());
        codeStr = String.format("%06d", currentCode);
        var challengeReq = new TotpChallengeRequest(loginResp.mfa_token(), codeStr, null);

        MvcResult challengeResult = mockMvc.perform(post("/v1/auth/totp/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(challengeReq)))
                .andExpect(status().isOk())
                .andReturn();

        var authSuccess = objectMapper.readValue(challengeResult.getResponse().getContentAsString(), AuthSuccess.class);
        assertThat(authSuccess.access_token()).isNotNull();
        assertThat(authSuccess.token_type()).isEqualTo("Bearer");
        assertThat(authSuccess.user().email()).isEqualTo(email.toLowerCase());
        assertThat(authSuccess.user().role()).isEqualTo("owner");

        // Verify Set-Cookie header contains httpOnly refresh token
        Cookie refreshCookie = challengeResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // 7. GET /v1/auth/me with access token
        mockMvc.perform(get("/v1/auth/me")
                .header("Authorization", "Bearer " + authSuccess.access_token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("owner"))
                .andExpect(jsonPath("$.totp_enabled").value(true));

        // 8. Refresh Token Rotation (requires custom CSRF header)
        MvcResult refreshResult = mockMvc.perform(post("/v1/auth/refresh")
                .header("X-Aegis-CSRF", "1")
                .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        var refreshedSuccess = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthSuccess.class);
        assertThat(refreshedSuccess.access_token()).isNotNull();
        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertThat(newRefreshCookie).isNotNull();
        assertThat(newRefreshCookie.getValue()).isNotEqualTo(refreshCookie.getValue());

        // 9. Logout
        mockMvc.perform(post("/v1/auth/logout")
                .cookie(newRefreshCookie))
                .andExpect(status().isNoContent());

        // 10. Old and rotated refresh tokens are now revoked
        mockMvc.perform(post("/v1/auth/refresh")
                .header("X-Aegis-CSRF", "1")
                .cookie(newRefreshCookie))
                .andExpect(status().isUnauthorized());
    }

    private int computeTotpCode(String base32Secret) {
        com.warrenstrange.googleauth.GoogleAuthenticator gAuth = new com.warrenstrange.googleauth.GoogleAuthenticator();
        return gAuth.getTotpPassword(base32Secret);
    }
}

