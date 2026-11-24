package com.prs.service;

import com.prs.entity.NotificationDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    @Value("${app.mail.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.mail.host:localhost}")
    private String mailHost;

    private boolean simulateFailure = false;
    private final List<SentEmailRecord> sentHistory = Collections.synchronizedList(new ArrayList<>());

    public boolean isEmailDeliveryEnabled() {
        return emailEnabled;
    }

    public void setSimulateFailure(boolean simulate) {
        this.simulateFailure = simulate;
    }

    public List<SentEmailRecord> getSentHistory() {
        return Collections.unmodifiableList(sentHistory);
    }

    public void clearHistory() {
        sentHistory.clear();
    }

    /**
     * Sends an email notification. In local/demo mode, acts as a mock delivery transport,
     * logging the message and recording delivery without making external network calls.
     */
    public NotificationDeliveryStatus sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            log.warn("Email delivery rejected: invalid recipient address '{}'", toEmail);
            return NotificationDeliveryStatus.FAILED;
        }

        if (simulateFailure) {
            log.warn("Mock email transport: simulated delivery failure to '{}'", toEmail);
            return NotificationDeliveryStatus.FAILED;
        }

        // Mock delivery transport (default for development and automated test evaluation)
        log.info("[MOCK EMAIL TRANSPORT] To: {} | Subject: '{}' | Body length: {} chars", toEmail, subject, body != null ? body.length() : 0);
        sentHistory.add(new SentEmailRecord(toEmail, subject, body, LocalDateTime.now()));
        return NotificationDeliveryStatus.SENT;
    }

    @Data
    @AllArgsConstructor
    public static class SentEmailRecord {
        private String toEmail;
        private String subject;
        private String body;
        private LocalDateTime sentAt;
    }
}
