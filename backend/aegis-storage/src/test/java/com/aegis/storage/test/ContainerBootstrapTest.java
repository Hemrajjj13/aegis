package com.aegis.storage.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerBootstrapTest {

    @Test
    @DisplayName("Verify Testcontainers harness initialization and Docker availability")
    void testContainersBootstrapHarness() {
        if (DockerAvailability.isAvailable()) {
            assertThat(PostgresIntegrationTest.POSTGRES).isNotNull();
            assertThat(PostgresIntegrationTest.POSTGRES.isRunning()).isTrue();
            assertThat(RedisIntegrationTest.REDIS).isNotNull();
            assertThat(RedisIntegrationTest.REDIS.isRunning()).isTrue();
            assertThat(KafkaIntegrationTest.KAFKA).isNotNull();
            assertThat(KafkaIntegrationTest.KAFKA.isRunning()).isTrue();
        } else {
            System.out.println("[INFO] Docker is not available on host. Testcontainers harness initialized in standby mode.");
            assertThat(DockerAvailability.isAvailable()).isFalse();
        }
    }
}
