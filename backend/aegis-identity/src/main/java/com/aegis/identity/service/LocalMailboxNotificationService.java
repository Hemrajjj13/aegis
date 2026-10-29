package com.aegis.identity.service;

import com.aegis.identity.port.NotificationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@Profile({"tier0", "tier1", "default", "test"})
public class LocalMailboxNotificationService implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(LocalMailboxNotificationService.class);

    private final Environment environment;
    private final Deque<MailboxMessage> messages = new ConcurrentLinkedDeque<>();

    public record MailboxMessage(
        String type, // "OTP" or "INVITE"
        String recipient,
        String code,
        String link,
        String purpose,
        Instant timestamp
    ) {}

    public LocalMailboxNotificationService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProfile() {
        if (environment != null && environment.getActiveProfiles() != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("tier2".equalsIgnoreCase(profile)) {
                    throw new IllegalStateException(
                        "FATAL: LocalMailboxNotificationService is strictly prohibited under tier2 deployment profiles."
                    );
                }
            }
        }
    }

    @Override
    public void sendOtp(String recipient, String purpose, String code, int expirySeconds) {
        MailboxMessage msg = new MailboxMessage("OTP", recipient, code, null, purpose, Instant.now());
        messages.addFirst(msg);
        while (messages.size() > 100) {
            messages.removeLast();
        }

        log.info("""
            [LOCAL-MAILBOX-DEV] ======================================================
            [LOCAL-MAILBOX-DEV] DISPATCHED OTP TO: {}
            [LOCAL-MAILBOX-DEV] PURPOSE: {}
            [LOCAL-MAILBOX-DEV] 6-DIGIT CODE: {} (Expires in {} seconds)
            [LOCAL-MAILBOX-DEV] ======================================================""",
                recipient, purpose, code, expirySeconds);
    }

    @Override
    public void sendInvite(String email, String inviteLink, String tenantName, String role) {
        MailboxMessage msg = new MailboxMessage("INVITE", email, null, inviteLink, "INVITE_" + role, Instant.now());
        messages.addFirst(msg);
        while (messages.size() > 100) {
            messages.removeLast();
        }

        log.info("""
            [LOCAL-MAILBOX-DEV] ======================================================
            [LOCAL-MAILBOX-DEV] DISPATCHED INVITATION TO: {}
            [LOCAL-MAILBOX-DEV] TENANT: {} | ROLE: {}
            [LOCAL-MAILBOX-DEV] INVITE LINK: {}
            [LOCAL-MAILBOX-DEV] ======================================================""",
                email, tenantName, role, inviteLink);
    }

    public List<MailboxMessage> getAllMessages() {
        return new ArrayList<>(messages);
    }

    public Optional<MailboxMessage> getLatestForRecipient(String recipient) {
        if (recipient == null) return Optional.empty();
        return messages.stream()
                .filter(m -> recipient.equalsIgnoreCase(m.recipient()))
                .findFirst();
    }

    public void clear() {
        messages.clear();
    }
}

