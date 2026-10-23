package com.aegis.storage.test;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class RedisIntegrationTest {
    protected static final GenericContainer<?> REDIS;

    static {
        if (DockerAvailability.isAvailable()) {
            REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            REDIS.start();
        } else {
            REDIS = null;
        }
    }

    @BeforeAll
    static void checkDocker() {
        DockerAvailability.requireDocker("RedisIntegrationTest");
    }

    public static String getRedisHost() {
        return REDIS != null ? REDIS.getHost() : null;
    }

    public static int getRedisPort() {
        return REDIS != null ? REDIS.getFirstMappedPort() : -1;
    }
}
