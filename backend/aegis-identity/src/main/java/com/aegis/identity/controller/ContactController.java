package com.aegis.identity.controller;

import com.aegis.identity.port.NotificationService;
import com.aegis.identity.port.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Public contact form endpoint.
 * Accepts standard URL-encoded form submissions from the static marketing website.
 */
@RestController
@RequestMapping("/v1/public")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Set<String> ALLOWED_TOPICS = Set.of(
            "General", "Pricing", "Demo", "Security", "Careers"
    );

    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final String siteUrl;

    public ContactController(NotificationService notificationService,
                             RateLimiter rateLimiter,
                             Environment environment) {
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
        String rawSiteUrl = environment.getProperty("PUBLIC_SITE_URL", "http://localhost:4321");
        this.siteUrl = rawSiteUrl.endsWith("/") ? rawSiteUrl.substring(0, rawSiteUrl.length() - 1) : rawSiteUrl;
    }

    @PostMapping(value = "/contact", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> submitContactForm(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "company", required = false) String company,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "_hp_company", required = false) String honeypot,
            HttpServletRequest request
    ) {
        // 1. Honeypot check: If bot filled the hidden honeypot field, pretend success without sending
        if (honeypot != null && !honeypot.isBlank()) {
            log.info("[CONTACT-FORM] Honeypot triggered; discarding spam submission silently.");
            return redirect(siteUrl + "/contact/thanks");
        }

        // 2. Rate limiting check (5 per hour per client IP)
        String clientIp = getClientIp(request);
        if (!rateLimiter.tryAcquire("contact:ip:" + clientIp, 5, Duration.ofHours(1))) {
            log.warn("[CONTACT-FORM] Rate limit exceeded for IP: {}", clientIp);
            return redirect(siteUrl + "/contact/error?reason=rate_limit");
        }

        // 3. Validation
        if (name == null || name.isBlank() || name.length() > 100) {
            return redirect(siteUrl + "/contact/error");
        }

        if (email == null || email.isBlank() || email.length() > 255 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return redirect(siteUrl + "/contact/error");
        }

        if (company != null && company.length() > 100) {
            return redirect(siteUrl + "/contact/error");
        }

        if (topic == null || !ALLOWED_TOPICS.contains(topic.trim())) {
            return redirect(siteUrl + "/contact/error");
        }

        if (message == null || message.isBlank() || message.length() > 2000) {
            return redirect(siteUrl + "/contact/error");
        }

        // 4. Header Injection Defense: strip CR/LF from all fields used in email headers
        String cleanName = stripCrLf(name);
        String cleanEmail = stripCrLf(email);
        String cleanCompany = company != null ? stripCrLf(company) : null;
        String cleanTopic = stripCrLf(topic);

        // 5. Dispatch via Notification abstraction
        try {
            notificationService.sendContactMessage(cleanName, cleanEmail, cleanCompany, cleanTopic, message.trim());
            log.info("[CONTACT-FORM] Inbound message processed for: {}", cleanEmail);
            return redirect(siteUrl + "/contact/thanks");
        } catch (Exception e) {
            log.error("[CONTACT-FORM] Failed to dispatch contact notification: {}", e.getMessage());
            return redirect(siteUrl + "/contact/error");
        }
    }

    private ResponseEntity<Void> redirect(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(destination));
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    public static String stripCrLf(String input) {
        if (input == null) return "";
        return input.replace("\r", "").replace("\n", "").trim();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}

