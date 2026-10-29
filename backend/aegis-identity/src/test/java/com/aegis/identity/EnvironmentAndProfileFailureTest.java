package com.aegis.identity;

import com.aegis.identity.controller.DevMailboxController;
import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.security.JwtAuthenticationFilter;
import com.aegis.identity.service.LocalMailboxNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentAndProfileFailureTest {

    @Test
    @DisplayName("LocalMailboxNotificationService throws IllegalStateException in tier2 profile")
    void testLocalMailboxRefusesTier2() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("tier2");

        LocalMailboxNotificationService service = new LocalMailboxNotificationService(env);
        assertThatThrownBy(service::validateProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly prohibited under tier2");
    }

    @Test
    @DisplayName("DevMailboxController throws IllegalStateException in tier2 profile")
    void testDevMailboxControllerRefusesTier2() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("tier2");

        DevMailboxController controller = new DevMailboxController(null, env);
        assertThatThrownBy(controller::validateProfile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly prohibited in tier2");
    }

    @Test
    @DisplayName("JwtAuthenticationFilter refuses aegis.auth.mode=dev-header in tier1 and tier2")
    void testDevHeaderRefusesTier1AndTier2() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("aegis.auth.mode", "dev-header");
        env.setActiveProfiles("tier1");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(null, null, env);
        assertThatThrownBy(filter::validateAuthMode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly prohibited under tier1 or tier2");
    }

    @Test
    @DisplayName("JwtService refuses startup in tier1/tier2 if signing keys are not configured")
    void testJwtServiceRefusesUnconfiguredKeysInTier1() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("tier1");

        JwtService jwtService = new JwtService(env);
        assertThatThrownBy(jwtService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured in tier1/tier2 profiles");
    }
}

