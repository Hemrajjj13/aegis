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
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Docker daemon is unavailable on host; " + testName + " SKIPPED.");
        }
    }
}
