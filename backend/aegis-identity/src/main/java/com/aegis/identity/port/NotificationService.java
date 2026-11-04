package com.aegis.identity.port;

public interface NotificationService {
    void sendOtp(String recipient, String purpose, String code, int expirySeconds);
    void sendInvite(String email, String inviteLink, String tenantName, String role);
    void sendContactMessage(String fromName, String fromEmail, String company, String topic, String message);
}
