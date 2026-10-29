package com.aegis.identity.controller;

import com.aegis.identity.service.LocalMailboxNotificationService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/dev/mailbox")
@Profile({"tier0", "tier1", "default", "test"})
public class DevMailboxController {

    private final LocalMailboxNotificationService mailboxService;
    private final Environment environment;

    public DevMailboxController(LocalMailboxNotificationService mailboxService, Environment environment) {
        this.mailboxService = mailboxService;
        this.environment = environment;
    }

    @PostConstruct
    public void validateProfile() {
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("tier2".equalsIgnoreCase(profile)) {
                    throw new IllegalStateException(
                        "FATAL: DevMailboxController is strictly prohibited in tier2 deployment profiles."
                    );
                }
            }
        }
    }

    @GetMapping
    public ResponseEntity<List<LocalMailboxNotificationService.MailboxMessage>> getAll() {
        return ResponseEntity.ok(mailboxService.getAllMessages());
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatest(@RequestParam("recipient") String recipient) {
        return mailboxService.getLatestForRecipient(recipient)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        mailboxService.clear();
        return ResponseEntity.noContent().build();
    }
}

