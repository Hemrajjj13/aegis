package com.aegis.identity.service;

import com.aegis.identity.port.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Production email notification sender backed by the Resend REST API.
 * Active strictly under prod and tier2 profiles.
 */
@Service
@Profile({"prod", "tier2"})
public class ResendEmailSender implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private String apiKey;
    private String contactToEmail;
    private String contactFromEmail;

    public ResendEmailSender(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void validateConfiguration() {
        this.apiKey = environment.getProperty("RESEND_API_KEY");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException(
                "FATAL: RESEND_API_KEY environment variable is mandatory under prod/tier2 deployment profiles."
            );
        }

        this.contactToEmail = environment.getProperty("CONTACT_TO_EMAIL", "contact@projectaegis.in");
        this.contactFromEmail = environment.getProperty("CONTACT_FROM_EMAIL", "onboarding@resend.dev");

        log.info("[RESEND] Initialized ResendEmailSender. Target inbox: {}", contactToEmail);
    }

    @Override
    public void sendOtp(String recipient, String purpose, String code, int expirySeconds) {
        String subject = "[Aegis Security] Your verification code";
        String html = "<p>Your single-use Aegis verification code is: <strong>" + escapeHtml(code) + "</strong></p>" +
                      "<p>This code expires in " + (expirySeconds / 60) + " minutes.</p>";
        sendEmail(recipient, subject, html, null);
    }

    @Override
    public void sendInvite(String email, String inviteLink, String tenantName, String role) {
        String subject = "[Aegis Console] You have been invited to join " + sanitizeHeader(tenantName);
        String html = "<p>You have been invited to join <strong>" + escapeHtml(tenantName) +
                      "</strong> as <code>" + escapeHtml(role) + "</code>.</p>" +
                      "<p><a href=\"" + escapeHtml(inviteLink) + "\">Accept Invitation and Configure 2FA</a></p>";
        sendEmail(email, subject, html, null);
    }

    @Override
    public void sendContactMessage(String fromName, String fromEmail, String company, String topic, String message) {
        String safeName = sanitizeHeader(fromName);
        String safeEmail = sanitizeHeader(fromEmail);
        String safeTopic = sanitizeHeader(topic);
        String safeCompany = company != null ? sanitizeHeader(company) : "N/A";

        String subject = "[Aegis Contact: " + safeTopic + "] from " + safeName;
        String html = "<div style=\"font-family: sans-serif; font-size: 14px; line-height: 1.5;\">" +
                      "<h3>New Inbound Inquiry on Project Aegis</h3>" +
                      "<ul>" +
                      "<li><strong>From:</strong> " + escapeHtml(safeName) + " &lt;" + escapeHtml(safeEmail) + "&gt;</li>" +
                      "<li><strong>Company:</strong> " + escapeHtml(safeCompany) + "</li>" +
                      "<li><strong>Topic:</strong> " + escapeHtml(safeTopic) + "</li>" +
                      "</ul>" +
                      "<h4>Message:</h4>" +
                      "<div style=\"background: #f4f4f5; padding: 12px; border-radius: 4px; white-space: pre-wrap;\">" +
                      escapeHtml(message) +
                      "</div></div>";

        sendEmail(contactToEmail, subject, html, safeEmail);
    }

    private void sendEmail(String to, String subject, String html, String replyTo) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", contactFromEmail);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", replyTo);
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(body);
            executeWithRetry(jsonPayload);
        } catch (Exception e) {
            log.error("[RESEND] Failed to dispatch email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email dispatch failed", e);
        }
    }

    private void executeWithRetry(String jsonPayload) throws Exception {
        int maxAttempts = 2; // 1 initial + 1 retry on 5xx
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.info("[RESEND] Email dispatched successfully (status: {})", status);
                return;
            }

            if (status >= 500 && attempt < maxAttempts) {
                log.warn("[RESEND] Server error {} from Resend, retrying once...", status);
                Thread.sleep(500);
                continue;
            }

            log.error("[RESEND] Email API error: status={}", status);
            throw new RuntimeException("Resend API rejected request with status " + status);
        }
    }

    public static String sanitizeHeader(String value) {
        if (value == null) return "";
        return value.replace("\r", "").replace("\n", "").trim();
    }

    public static String escapeHtml(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

