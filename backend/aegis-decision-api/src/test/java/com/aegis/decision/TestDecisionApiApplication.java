package com.aegis.decision;

import com.aegis.identity.crypto.JwtService;
import com.aegis.identity.security.JwtAuthenticationFilter;
import com.aegis.identity.security.SecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
    scanBasePackages = {"com.aegis.decision", "com.aegis.core"},
    exclude = {DataSourceAutoConfiguration.class}
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
public class TestDecisionApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestDecisionApiApplication.class, args);
    }
}
