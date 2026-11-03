package com.aegis.identity;

import com.aegis.identity.service.InMemoryRateLimiter;
import com.aegis.identity.service.LocalMailboxNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestIdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("tier0")
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @Autowired
    private LocalMailboxNotificationService mailboxService;

    @BeforeEach
    void setup() {
        rateLimiter.resetAll();
        mailboxService.clear();
    }

    @Test
    @DisplayName("Valid contact submission responds 303 See Other to /contact/thanks and stores message")
    void testValidSubmission() throws Exception {
        mockMvc.perform(post("/v1/public/contact")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Alice Engineer")
                        .param("email", "alice@company.com")
                        .param("company", "Acme Inc")
                        .param("topic", "Pricing")
                        .param("message", "We are interested in evaluating Aegis for our payment flow."))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:4321/contact/thanks"));

        var messages = mailboxService.getAllMessages();
        assertThat(messages).hasSize(1);
        var msg = messages.getFirst();
        assertThat(msg.type()).isEqualTo("CONTACT");
        assertThat(msg.recipient()).isEqualTo("alice@company.com");
        assertThat(msg.code()).isEqualTo("Pricing");
        assertThat(msg.purpose()).contains("CONTACT_Pricing");
    }

    @Test
    @DisplayName("Honeypot filled submission responds 303 See Other without storing message")
    void testHoneypotDiscarded() throws Exception {
        mockMvc.perform(post("/v1/public/contact")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Spam Bot")
                        .param("email", "bot@spammer.org")
                        .param("topic", "General")
                        .param("message", "Cheap watches!")
                        .param("_hp_company", "Gotcha Bot"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:4321/contact/thanks"));

        assertThat(mailboxService.getAllMessages()).isEmpty();
    }

    @Test
    @DisplayName("Invalid email format redirects to /contact/error")
    void testInvalidEmail() throws Exception {
        mockMvc.perform(post("/v1/public/contact")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Bob")
                        .param("email", "not-an-email")
                        .param("topic", "General")
                        .param("message", "Hello"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:4321/contact/error"));

        assertThat(mailboxService.getAllMessages()).isEmpty();
    }

    @Test
    @DisplayName("Unknown topic redirects to /contact/error")
    void testInvalidTopic() throws Exception {
        mockMvc.perform(post("/v1/public/contact")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Bob")
                        .param("email", "bob@example.com")
                        .param("topic", "InvalidTopic")
                        .param("message", "Hello"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:4321/contact/error"));

        assertThat(mailboxService.getAllMessages()).isEmpty();
    }

    @Test
    @DisplayName("Rate limit triggers redirect to /contact/error?reason=rate_limit after 5 submissions")
    void testRateLimitPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/v1/public/contact")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.42");
                                return req;
                            })
                            .param("name", "Bob " + i)
                            .param("email", "bob@example.com")
                            .param("topic", "General")
                            .param("message", "Inquiry " + i))
                    .andExpect(status().isSeeOther())
                    .andExpect(header().string("Location", "http://localhost:4321/contact/thanks"));
        }

        // 6th call from same IP should be blocked by rate limiter
        mockMvc.perform(post("/v1/public/contact")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .with(req -> {
                            req.setRemoteAddr("198.51.100.42");
                            return req;
                        })
                        .param("name", "Bob 6")
                        .param("email", "bob@example.com")
                        .param("topic", "General")
                        .param("message", "Inquiry 6"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:4321/contact/error?reason=rate_limit"));
    }
}
