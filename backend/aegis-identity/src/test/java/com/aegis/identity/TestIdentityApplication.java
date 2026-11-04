package com.aegis.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.aegis.identity", "com.aegis.storage"})
public class TestIdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestIdentityApplication.class, args);
    }
}

