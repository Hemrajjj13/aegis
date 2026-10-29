package com.aegis.identity;

import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.crypto.TotpService;
import com.aegis.identity.model.AuthDtos.*;
import com.aegis.identity.model.Role;
import com.aegis.identity.model.TenantUser;
import com.aegis.identity.model.UserStatus;
import com.aegis.identity.repository.AuthUserRepository;
import com.aegis.identity.repository.InviteRepository;
import com.aegis.identity.repository.TenantRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private LocalMailboxNotificationService mailboxService;

    @Test
    @DisplayName("OpenAPI Contract: POST /auth/signup/initiate response matches SignupInitiateResponse schema")
    void testSignupInitiateContract() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var req = new SignupInitiateRequest("Org " + suffix, "Owner", "c_" + suffix + "@projectaegis.in", "+9193" + suffix.replaceAll("[^0-9]", "6").substring(0, 6) + "05", "ValidPassword@2026!");

        mockMvc.perform(post("/v1/auth/signup/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge_id").isString())
                .andExpect(jsonPath("$.resend_cooldown_seconds").isNumber());
    }

    @Test
    @DisplayName("OpenAPI Contract: POST /auth/password/forgot response matches schema")
    void testForgotPasswordContract() throws Exception {
        var req = new ForgotPasswordRequest("someone@projectaegis.in");
        mockMvc.perform(post("/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("OpenAPI Contract: GET /auth/invite/verify matches schema")
    void testInviteVerifyContract() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = UUID.randomUUID();
        tenantRepository.createTenant(tenantId, "t-inv-" + suffix, "Invite Tenant");
        UUID ownerId = UUID.randomUUID();
        userRepository.createUser(ownerId, tenantId, "owner_" + suffix + "@inv.com", "+919311" + suffix.replaceAll("[^0-9]", "1").substring(0, 6), "Owner", "owner", "ACTIVE", true);

        String rawToken = CryptoUtils.secureRandomHex(32);
        String inviteeEmail = "analyst_" + suffix + "@inv.com";
        inviteRepository.createInvite(tenantId, inviteeEmail, "risk_analyst", ownerId, CryptoUtils.sha256Hex(rawToken), Instant.now().plus(24, ChronoUnit.HOURS));

        mockMvc.perform(get("/v1/auth/invite/verify")
                .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value(inviteeEmail))
                .andExpect(jsonPath("$.role").value("risk_analyst"))
                .andExpect(jsonPath("$.tenant_name").value("Invite Tenant"));
    }

    @Test
    @DisplayName("OpenAPI Contract: GET /auth/me matches TenantUser schema")
    void testAuthMeContract() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = UUID.randomUUID();
        tenantRepository.createTenant(tenantId, "t-me-" + suffix, "Me Tenant");
        UUID userId = UUID.randomUUID();
        String email = "me_" + suffix + "@projectaegis.in";
        String mobile = "+919322" + suffix.replaceAll("[^0-9]", "2").substring(0, 6);
        userRepository.createUser(userId, tenantId, email, mobile, "Me User", "developer", "ACTIVE", true);

        TenantUser user = new TenantUser(userId, email, mobile, "Me User", "developer", "ACTIVE", true);
        String token = jwtService.issueAccessToken(user, tenantId);

        mockMvc.perform(get("/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("developer"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totp_enabled").value(true));
    }

    @Test
    @DisplayName("OpenAPI Contract: GET /tenant/members matches array of TenantUser")
    void testTenantMembersContract() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = UUID.randomUUID();
        tenantRepository.createTenant(tenantId, "t-mem-" + suffix, "Members Tenant");
        UUID userId = UUID.randomUUID();
        String email = "member_" + suffix + "@projectaegis.in";
        String mobile = "+919333" + suffix.replaceAll("[^0-9]", "3").substring(0, 6);
        userRepository.createUser(userId, tenantId, email, mobile, "Member User", "owner", "ACTIVE", true);

        TenantUser user = new TenantUser(userId, email, mobile, "Member User", "owner", "ACTIVE", true);
        String token = jwtService.issueAccessToken(user, tenantId);

        mockMvc.perform(get("/v1/tenant/members")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").value(email));
    }
}

