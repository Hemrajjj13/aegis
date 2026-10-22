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
}
