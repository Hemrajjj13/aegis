package com.aegis.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = {"com.aegis.decision", "com.aegis.core"},
    exclude = {DataSourceAutoConfiguration.class}
)
public class TestDecisionApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestDecisionApiApplication.class, args);
    }
}
