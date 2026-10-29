package com.aegis.identity.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"type":"urn:problem:unauthorized","title":"Unauthorized","status":401,"detail":"Authentication token required"}
                    """.trim());
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"type":"urn:problem:forbidden","title":"Forbidden","status":403,"detail":"Access denied"}
                    """.trim());
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/v1/auth/login", "/auth/login",
                    "/v1/auth/totp/challenge", "/auth/totp/challenge",
                    "/v1/auth/totp/setup", "/auth/totp/setup",
                    "/v1/auth/totp/verify", "/auth/totp/verify",
                    "/v1/auth/refresh", "/auth/refresh",
                    "/v1/auth/logout", "/auth/logout",
                    "/v1/auth/signup/initiate", "/auth/signup/initiate",
                    "/v1/auth/signup/verify-otp", "/auth/signup/verify-otp",
                    "/v1/auth/invite/verify", "/auth/invite/verify",
                    "/v1/auth/invite/accept", "/auth/invite/accept",
                    "/v1/auth/password/forgot", "/auth/password/forgot",
                    "/v1/auth/password/reset", "/auth/password/reset",
                    "/dev/mailbox/**",
                    "/actuator/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-resources/**",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

