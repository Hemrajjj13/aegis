package com.aegis.identity.controller;

import com.aegis.identity.port.RateLimiter;
import com.aegis.identity.service.InMemoryRateLimiter;
import com.aegis.identity.service.LocalMailboxNotificationService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/dev/mailbox")
@Profile({"tier0", "tier1", "default", "test"})
public class DevMailboxController {

    private final LocalMailboxNotificationService mailboxService;
    private final RateLimiter rateLimiter;
    private final Environment environment;

    public DevMailboxController(
            LocalMailboxNotificationService mailboxService,
            RateLimiter rateLimiter,
            Environment environment
    ) {
        this.mailboxService = mailboxService;
        this.rateLimiter = rateLimiter;
        this.environment = environment;
    }

    @PostConstruct
    public void validateProfile() {
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("tier2".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                    throw new IllegalStateException(
                        "FATAL: DevMailboxController is strictly prohibited in tier2 or prod deployment profiles."
                    );
                }
            }
        }
    }

    public record MailboxResponseItem(
        String id,
        String destination,
        String purpose,
        String code,
        String link,
        String createdAt
    ) {}

    @GetMapping
    public ResponseEntity<List<MailboxResponseItem>> getAll() {
        var rawMessages = mailboxService.getAllMessages();
        List<MailboxResponseItem> items = new ArrayList<>();
        for (var m : rawMessages) {
            items.add(new MailboxResponseItem(
                UUID.randomUUID().toString(),
                m.recipient(),
                m.purpose(),
                m.code(),
                m.link(),
                m.timestamp().toString()
            ));
        }
        return ResponseEntity.ok(items);
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatest(@RequestParam("recipient") String recipient) {
        return mailboxService.getLatestForRecipient(recipient)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetMailboxAndRateLimits() {
        mailboxService.clear();
        if (rateLimiter instanceof InMemoryRateLimiter mem) {
            mem.resetAll();
        }
        return ResponseEntity.ok(Map.of("status", "RESET_SUCCESSFUL"));
    }
}
