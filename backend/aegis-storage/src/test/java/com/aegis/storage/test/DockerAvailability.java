package com.aegis.storage.test;

import org.testcontainers.DockerClientFactory;

public final class DockerAvailability {
    private static final boolean AVAILABLE;

    static {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            available = false;
        }
        AVAILABLE = available;
    }

    private DockerAvailability() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isNoDockerAllowed() {
        return "true".equalsIgnoreCase(System.getenv("AEGIS_ALLOW_NO_DOCKER"))
                || "true".equalsIgnoreCase(System.getProperty("AEGIS_ALLOW_NO_DOCKER"));
    }

    public static void requireDocker(String testName) {
        if (!AVAILABLE) {
            if (isNoDockerAllowed()) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false,
                        "Docker is missing for " + testName + " but AEGIS_ALLOW_NO_DOCKER=true; test SKIPPED");
            } else {
                org.junit.jupiter.api.Assertions.fail(
                        "Docker is required for " + testName + ", but Docker daemon is unavailable on host. " +
                                "Set environment variable AEGIS_ALLOW_NO_DOCKER=true to allow skipping Docker-dependent tests.");
            }
        }
    }
}
