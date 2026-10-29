package com.aegis.identity.controller;

import com.aegis.core.context.TenantContextHolder;
import com.aegis.identity.crypto.CryptoUtils;
import com.aegis.identity.model.AuthDtos.InviteRequest;
import com.aegis.identity.model.AuthDtos.InviteResponse;
import com.aegis.identity.model.Role;
import com.aegis.identity.model.TenantUser;
import com.aegis.identity.port.NotificationService;
import com.aegis.identity.repository.AuthUserRepository;
import com.aegis.identity.repository.InviteRepository;
import com.aegis.identity.repository.TenantRepository;
import com.aegis.identity.security.JwtAuthenticationToken;
import com.aegis.identity.service.AuthAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/v1/tenant", "/tenant"})
public class TenantController {

    private final InviteRepository inviteRepository;
    private final AuthUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final NotificationService notificationService;
    private final AuthAuditService auditService;

    public TenantController(
            InviteRepository inviteRepository,
            AuthUserRepository userRepository,
            TenantRepository tenantRepository,
            NotificationService notificationService,
            AuthAuditService auditService
    ) {
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @PostMapping("/invites")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<?> inviteMember(@RequestBody InviteRequest request, Authentication auth) {
        if (request.email() == null || request.email().isBlank() || request.role() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and role are required."));
        }

        Role role;
        try {
            role = Role.fromString(request.role());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + request.role()));
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        UUID callerUserId = extractUserId(auth);
        String callerRole = auth.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_USER");

        if (tenantId == null || callerUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Tenant context missing"));
        }

        String rawToken = CryptoUtils.secureRandomHex(32);
        String tokenHash = CryptoUtils.sha256Hex(rawToken);
        Instant exp = Instant.now().plus(24, ChronoUnit.HOURS);

        var inviteRecord = inviteRepository.createInvite(tenantId, request.email(), role.name(), callerUserId, tokenHash, exp);

        String tenantName = tenantRepository.findById(tenantId).map(TenantRepository.TenantRecord::name).orElse("Aegis Tenant");
        String inviteLink = "http://localhost:5173/invite/accept?token=" + rawToken;
        notificationService.sendInvite(request.email().trim().toLowerCase(), inviteLink, tenantName, role.name());

        auditService.recordAuthEvent(tenantId, callerUserId.toString(), callerRole,
                null, "INVITE_CREATED", "USER_INVITE", inviteRecord.inviteId().toString(),
                Map.of("email", request.email(), "role", role.name()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InviteResponse(inviteRecord.inviteId(), inviteRecord.email(), inviteRecord.status()));
    }

    @GetMapping("/members")
    public ResponseEntity<List<TenantUser>> getMembers(Authentication auth) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null && auth instanceof JwtAuthenticationToken jwtAuth) {
            tenantId = jwtAuth.getTenantId();
        }

        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<TenantUser> members = userRepository.findByTenantId(tenantId);
        return ResponseEntity.ok(members);
    }

    private UUID extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserId();
        }
        return null;
    }
}

