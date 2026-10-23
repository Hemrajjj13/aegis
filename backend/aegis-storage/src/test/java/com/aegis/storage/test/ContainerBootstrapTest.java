package com.aegis.storage.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerBootstrapTest {

    @Test
    @DisplayName("Verify Testcontainers harness initialization and Docker availability")
    void testContainersBootstrapHarness() {
        DockerAvailability.requireDocker("ContainerBootstrapTest");
        assertThat(PostgresIntegrationTest.isUsingDocker()).isTrue();
        assertThat(RedisIntegrationTest.REDIS).isNotNull();
        assertThat(RedisIntegrationTest.REDIS.isRunning()).isTrue();
        assertThat(KafkaIntegrationTest.KAFKA).isNotNull();
        assertThat(KafkaIntegrationTest.KAFKA.isRunning()).isTrue();
    }
}
