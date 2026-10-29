package com.aegis.identity;

import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.crypto.TotpService;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.service.LocalMailboxNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class InviteLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalMailboxNotificationService mailboxService;

    @Autowired
    private TotpService totpService;

    @Test
    @DisplayName("Invite lifecycle: invite creation -> verify -> accept with TOTP -> second accept rejected")
    void testInviteLifecycle() throws Exception {
        // 1. Create owner to invite members
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgName = "Org " + suffix;
        String ownerEmail = "owner_" + suffix + "@projectaegis.in";
        String ownerMobile = "+9197" + suffix.replaceAll("[^0-9]", "2").substring(0, 6) + "01";
        if (ownerMobile.length() != 13) ownerMobile = "+919711223301";
        String password = "OwnerPassword@2026!";

        // Signup initiate
        var initResult = mockMvc.perform(post("/v1/auth/signup/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupInitiateRequest(orgName, "Owner", ownerEmail, ownerMobile, password))))
                .andExpect(status().isOk())
                .andReturn();
        var initResp = objectMapper.readValue(initResult.getResponse().getContentAsString(), SignupInitiateResponse.class);

        var otpMsg = mailboxService.getLatestForRecipient(ownerMobile).orElseThrow();
        var verifyRes = mockMvc.perform(post("/v1/auth/signup/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupVerifyOtpRequest(initResp.challenge_id(), otpMsg.code()))))
                .andExpect(status().isOk())
                .andReturn();
        var verifyResp = objectMapper.readValue(verifyRes.getResponse().getContentAsString(), SignupVerifyOtpResponse.class);

        // TOTP Setup
        var setupRes = mockMvc.perform(post("/v1/auth/totp/setup")
                .header("Authorization", "Bearer " + verifyResp.setup_token()))
                .andExpect(status().isOk())
                .andReturn();
        var setupResp = objectMapper.readValue(setupRes.getResponse().getContentAsString(), TotpSetupResponse.class);
        int totpCode = new com.warrenstrange.googleauth.GoogleAuthenticator().getTotpPassword(setupResp.secret_key());

        mockMvc.perform(post("/v1/auth/totp/verify")
                .header("Authorization", "Bearer " + verifyResp.setup_token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TotpVerifyRequest(String.format("%06d", totpCode)))))
                .andExpect(status().isOk());

        // Login as owner to get access token
        var loginRes = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(ownerEmail, password))))
                .andExpect(status().isOk())
                .andReturn();
        var loginResp = objectMapper.readValue(loginRes.getResponse().getContentAsString(), LoginResponse.class);

        totpCode = new com.warrenstrange.googleauth.GoogleAuthenticator().getTotpPassword(setupResp.secret_key());
        var chalRes = mockMvc.perform(post("/v1/auth/totp/challenge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TotpChallengeRequest(loginResp.mfa_token(), String.format("%06d", totpCode), null))))
                .andExpect(status().isOk())
                .andReturn();
        var ownerAuth = objectMapper.readValue(chalRes.getResponse().getContentAsString(), AuthSuccess.class);

        // 2. Owner invites a risk analyst
        String inviteeEmail = "analyst_" + suffix + "@projectaegis.in";
        var inviteReq = new InviteRequest(inviteeEmail, "risk_analyst");

        MvcResult inviteRes = mockMvc.perform(post("/v1/tenant/invites")
                .header("Authorization", "Bearer " + ownerAuth.access_token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andReturn();

        var inviteResp = objectMapper.readValue(inviteRes.getResponse().getContentAsString(), InviteResponse.class);
        assertThat(inviteResp.invite_id()).isNotNull();
        assertThat(inviteResp.email()).isEqualTo(inviteeEmail);

        // Retrieve invite link from mailbox
        var inviteMsg = mailboxService.getLatestForRecipient(inviteeEmail)
                .orElseThrow(() -> new AssertionError("Invite message not found in local mailbox"));
        String rawToken = inviteMsg.link().substring(inviteMsg.link().indexOf("token=") + 6);
        assertThat(rawToken).hasSize(64);

        // 3. Verify Invite Token
        MvcResult tokenVerifyRes = mockMvc.perform(get("/v1/auth/invite/verify")
                .param("token", rawToken))
                .andExpect(status().isOk())
                .andReturn();

        var tokenVerifyResp = objectMapper.readValue(tokenVerifyRes.getResponse().getContentAsString(), InviteVerifyResponse.class);
        assertThat(tokenVerifyResp.valid()).isTrue();
        assertThat(tokenVerifyResp.email()).isEqualTo(inviteeEmail);
        assertThat(tokenVerifyResp.role()).isEqualTo("risk_analyst");

        // 4. Accept Invite
        String analystSecret = totpService.generateSecret();
        int analystTotp = new com.warrenstrange.googleauth.GoogleAuthenticator().getTotpPassword(analystSecret);
        String analystMobile = "+9197" + suffix.replaceAll("[^0-9]", "3").substring(0, 6) + "02";
        if (analystMobile.length() != 13) analystMobile = "+919711223302";

        var acceptReq = new InviteAcceptRequest(
                rawToken, "Risk Analyst User", analystMobile,
                "AnalystPassword@2026!", String.format("%06d", analystTotp), analystSecret
        );

        MvcResult acceptRes = mockMvc.perform(post("/v1/auth/invite/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andReturn();

        var analystAuth = objectMapper.readValue(acceptRes.getResponse().getContentAsString(), AuthSuccess.class);
        assertThat(analystAuth.access_token()).isNotNull();
        assertThat(analystAuth.user().email()).isEqualTo(inviteeEmail);
        assertThat(analystAuth.user().role()).isEqualTo("risk_analyst");

        // 5. Attempting to accept the same token a second time is rejected (single-use)
        mockMvc.perform(post("/v1/auth/invite/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isBadRequest());

        // 6. Token verification now returns valid=false
        mockMvc.perform(get("/v1/auth/invite/verify")
                .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}

