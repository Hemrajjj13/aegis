package com.aegis.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class DevMailboxProdProfileTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("RESEND_API_KEY", () -> "re_test_prod_safety_key");
        registry.add("AEGIS_TOTP_ENC_KEY", () -> "dGhpcy1pcy1hLXRlc3QtMzItYnl0ZS1lbmMta2V5MSE=");

        Path privPath = Path.of(".dev-keys", "jwt-private.pem");
        Path pubPath = Path.of(".dev-keys", "jwt-public.pem");
        if (Files.exists(privPath) && Files.exists(pubPath)) {
            String privStr = Files.readString(privPath);
            String pubStr = Files.readString(pubPath);
            registry.add("AEGIS_JWT_PRIVATE_KEY", () -> privStr);
            registry.add("AEGIS_JWT_PUBLIC_KEY", () -> pubStr);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            String privPem = "-----BEGIN PRIVATE KEY-----\n" +
                    Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPrivate().getEncoded()) +
                    "\n-----END PRIVATE KEY-----\n";
            String pubPem = "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPublic().getEncoded()) +
                    "\n-----END PUBLIC KEY-----\n";
            registry.add("AEGIS_JWT_PRIVATE_KEY", () -> privPem);
            registry.add("AEGIS_JWT_PUBLIC_KEY", () -> pubPem);
        }
    }

    @Autowired(required = false)
    private com.aegis.identity.controller.DevMailboxController devMailboxController;

    @Autowired(required = false)
    private com.aegis.identity.service.LocalMailboxNotificationService localMailboxService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Dev mailbox controller bean is NOT registered in prod profile")
    void testDevMailboxControllerBeanAbsentInProd() {
        org.assertj.core.api.Assertions.assertThat(devMailboxController).isNull();
        org.assertj.core.api.Assertions.assertThat(localMailboxService).isNull();
    }

    @Test
    @DisplayName("GET /dev/mailbox returns 404 in prod profile")
    void testGetDevMailboxReturns404InProd() throws Exception {
        mockMvc.perform(get("/dev/mailbox"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /dev/mailbox/reset returns 404 in prod profile")
    void testPostDevMailboxResetReturns404InProd() throws Exception {
        mockMvc.perform(post("/dev/mailbox/reset"))
                .andExpect(status().isNotFound());
    }
}
