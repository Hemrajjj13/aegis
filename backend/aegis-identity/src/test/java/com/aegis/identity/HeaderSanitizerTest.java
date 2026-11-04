package com.aegis.identity;

import com.aegis.identity.controller.ContactController;
import com.aegis.identity.service.ResendEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderSanitizerTest {

    @Test
    @DisplayName("stripCrLf removes CR and LF characters to prevent header injection")
    void testStripCrLf() {
        String tainted = "alice@example.com\r\nBcc: victim@example.com\r\nSubject: Injected";
        String clean = ContactController.stripCrLf(tainted);

        assertThat(clean).doesNotContain("\r");
        assertThat(clean).doesNotContain("\n");
        assertThat(clean).isEqualTo("alice@example.comBcc: victim@example.comSubject: Injected");
    }

    @Test
    @DisplayName("stripCrLf handles null and whitespace cleanly")
    void testStripCrLfNullAndWhitespace() {
        assertThat(ContactController.stripCrLf(null)).isEmpty();
        assertThat(ContactController.stripCrLf("  ")).isEmpty();
        assertThat(ContactController.stripCrLf("Normal String")).isEqualTo("Normal String");
    }

    @Test
    @DisplayName("ResendEmailSender.sanitizeHeader removes CR and LF")
    void testResendSanitizeHeader() {
        String tainted = "Acme Corp\r\nContent-Type: text/html";
        String clean = ResendEmailSender.sanitizeHeader(tainted);

        assertThat(clean).doesNotContain("\r");
        assertThat(clean).doesNotContain("\n");
        assertThat(clean).isEqualTo("Acme CorpContent-Type: text/html");
    }

    @Test
    @DisplayName("ResendEmailSender.escapeHtml escapes HTML metacharacters")
    void testResendEscapeHtml() {
        String dirty = "<script>alert('xss')</script> & \"quotes\"";
        String escaped = ResendEmailSender.escapeHtml(dirty);

        assertThat(escaped).doesNotContain("<script>");
        assertThat(escaped).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
        assertThat(escaped).contains("&amp;");
        assertThat(escaped).contains("&quot;quotes&quot;");
    }
}

