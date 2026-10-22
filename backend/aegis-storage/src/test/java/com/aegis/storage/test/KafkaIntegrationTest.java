package com.aegis.storage.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class KafkaIntegrationTest {
    protected static final KafkaContainer KAFKA;

    static {
        if (DockerAvailability.isAvailable()) {
            KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
            KAFKA.start();
        } else {
            KAFKA = null;
        }
    }

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(DockerAvailability.isAvailable(), "Docker daemon unavailable; skipping live container test");
    }

    public static String getBootstrapServers() {
        return KAFKA != null ? KAFKA.getBootstrapServers() : null;
    }
}
