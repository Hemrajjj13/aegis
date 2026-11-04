package com.aegis.identity;

import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.model.AuthDtos.InviteRequest;
import com.aegis.identity.model.Role;
import com.aegis.identity.model.TenantUser;
import com.aegis.identity.model.UserStatus;
import com.aegis.identity.repository.AuthUserRepository;
import com.aegis.identity.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class RoleGuardMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuthUserRepository userRepository;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Role guard matrix for POST /v1/tenant/invites: only owner and admin permitted")
    void testRoleGuardMatrixForInvites(Role role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID tenantId = UUID.randomUUID();
        tenantRepository.createTenant(tenantId, "rg-" + suffix, "Role Guard Tenant");

        UUID userId = UUID.randomUUID();
        String email = "user_" + role.name() + "_" + suffix + "@projectaegis.in";
        String mobile = "+9194" + suffix.replaceAll("[^0-9]", "4").substring(0, 5) + role.ordinal() + "0";
        TenantUser user = new TenantUser(
                userId, email, mobile, "Test User", role.name(), UserStatus.ACTIVE.name(), true
        );
        userRepository.createUser(userId, tenantId, user.email(), user.mobile(), user.full_name(), role.name(), UserStatus.ACTIVE.name(), true);

        String token = jwtService.issueAccessToken(user, tenantId);

        var inviteReq = new InviteRequest("new_invitee_" + suffix + "@projectaegis.in", "risk_analyst");

        var requestBuilder = post("/v1/tenant/invites")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inviteReq));

        if (role == Role.owner || role == Role.admin) {
            mockMvc.perform(requestBuilder).andExpect(status().isCreated());
        } else {
            mockMvc.perform(requestBuilder).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Cross-tenant isolation: User of Tenant A cannot see members of Tenant B")
    void testCrossTenantIsolation() throws Exception {
        UUID tenantA = UUID.randomUUID();
        tenantRepository.createTenant(tenantA, "tenant-a-" + tenantA.toString().substring(0, 8), "Tenant A");

        UUID tenantB = UUID.randomUUID();
        tenantRepository.createTenant(tenantB, "tenant-b-" + tenantB.toString().substring(0, 8), "Tenant B");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID userAId = UUID.randomUUID();
        String emailA = "alice_" + suffix + "@tenant-a.com";
        String mobileA = "+919411" + suffix.replaceAll("[^0-9]", "1").substring(0, 6);
        userRepository.createUser(userAId, tenantA, emailA, mobileA, "Alice A", "owner", "ACTIVE", true);

        UUID userBId = UUID.randomUUID();
        String emailB = "bob_" + suffix + "@tenant-b.com";
        String mobileB = "+919422" + suffix.replaceAll("[^0-9]", "2").substring(0, 6);
        userRepository.createUser(userBId, tenantB, emailB, mobileB, "Bob B", "owner", "ACTIVE", true);

        TenantUser userA = new TenantUser(userAId, emailA, mobileA, "Alice A", "owner", "ACTIVE", true);
        String tokenA = jwtService.issueAccessToken(userA, tenantA);

        MvcResult res = mockMvc.perform(get("/v1/tenant/members")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = res.getResponse().getContentAsString();
        assertThat(responseBody).contains(emailA);
        assertThat(responseBody).doesNotContain(emailB);
    }
}

