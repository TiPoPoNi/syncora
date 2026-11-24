package com.prs.service;

import com.prs.dto.NotificationDto;
import com.prs.dto.NotificationPreferencesDto;
import com.prs.entity.*;
import com.prs.repository.NotificationOutboxRepository;
import com.prs.repository.NotificationPreferenceRepository;
import com.prs.repository.NotificationRepository;
import com.prs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public Notification sendNotification(User recipient, NotificationEventType eventType, String title, String message, Long projectId, Long taskId) {
        if (recipient == null) {
            log.warn("Cannot send notification: recipient is null. Event: {}, Title: '{}'", eventType, title);
            return null;
        }

        NotificationPreference pref = getOrCreatePreferences(recipient);

        // 1. Verify category permission
        if (!isEventCategoryEnabled(pref, eventType)) {
            log.debug("Notification suppressed by user preference for user '{}', event: {}", recipient.getUsername(), eventType);
            return null;
        }

        Notification savedNotification = null;

        // 2. In-App Notification Delivery
        if (Boolean.TRUE.equals(pref.getInAppEnabled())) {
            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setEventType(eventType);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setRelatedProjectId(projectId);
            notification.setRelatedTaskId(taskId);
            notification.setIsRead(false);
            notification.setDeliveryChannel(Boolean.TRUE.equals(pref.getEmailEnabled()) ? NotificationDeliveryChannel.BOTH : NotificationDeliveryChannel.IN_APP);
            notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            savedNotification = notificationRepository.save(notification);
        }

        // 3. Email Delivery & Outbox
        if (Boolean.TRUE.equals(pref.getEmailEnabled()) && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
            NotificationOutbox outbox = new NotificationOutbox();
            outbox.setNotification(savedNotification);
            outbox.setRecipientEmail(recipient.getEmail());
            outbox.setSubject(title);
            outbox.setBody(message);
            outbox.setStatus(NotificationDeliveryStatus.PENDING);
            outbox = outboxRepository.save(outbox);

            // Attempt delivery
            NotificationDeliveryStatus status = emailService.sendEmail(recipient.getEmail(), title, message);
            outbox.setStatus(status);
            outbox.setLastAttemptAt(LocalDateTime.now());
            if (status == NotificationDeliveryStatus.FAILED) {
                outbox.setRetryCount(1);
                outbox.setErrorMessage("Initial email delivery attempt failed");
            }
            outboxRepository.save(outbox);
        }

        return savedNotification;
    }

    private boolean isEventCategoryEnabled(NotificationPreference pref, NotificationEventType eventType) {
        return switch (eventType) {
            case TASK_ASSIGNMENT, TASK_REASSIGNMENT -> Boolean.TRUE.equals(pref.getTaskAssignmentsEnabled());
            case TASK_DEADLINE_APPROACHING, TASK_OVERDUE, PROJECT_DEADLINE_APPROACHING -> Boolean.TRUE.equals(pref.getDeadlineRemindersEnabled());
            case VENDOR_DELAY, RESOURCE_CONFLICT -> Boolean.TRUE.equals(pref.getDelayAlertsEnabled());
            case RECOMMENDATION_APPLIED -> Boolean.TRUE.equals(pref.getRecommendationAlertsEnabled());
            case SCHEDULE_RECALCULATED, MILESTONE_COMPLETED, PROJECT_COMPLETED -> true;
            default -> true;
        };
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getUserNotifications(String username, Boolean unreadOnly) {
        User user = getUserByUsername(username);
        List<Notification> list;
        if (Boolean.TRUE.equals(unreadOnly)) {
            list = notificationRepository.findTop50ByUserAndIsReadOrderByCreatedAtDesc(user, false);
        } else {
            list = notificationRepository.findTop50ByUserOrderByCreatedAtDesc(user);
        }
        return list.stream().map(NotificationDto::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String username) {
        User user = getUserByUsername(username);
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public NotificationDto markAsRead(Long notificationId, String username) {
        User user = getUserByUsername(username);
        Notification notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found or access denied"));

        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        Notification updated = notificationRepository.save(notification);
        return NotificationDto.fromEntity(updated);
    }

    @Transactional
    public void markAllAsRead(String username) {
        User user = getUserByUsername(username);
        notificationRepository.markAllAsReadForUser(user);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(String username) {
        User user = getUserByUsername(username);
        return NotificationPreferencesDto.fromEntity(getOrCreatePreferences(user));
    }

    @Transactional
    public NotificationPreferencesDto updatePreferences(String username, NotificationPreferencesDto dto) {
        User user = getUserByUsername(username);
        NotificationPreference pref = getOrCreatePreferences(user);

        if (dto.getInAppEnabled() != null) pref.setInAppEnabled(dto.getInAppEnabled());
        if (dto.getEmailEnabled() != null) pref.setEmailEnabled(dto.getEmailEnabled());
        if (dto.getDeadlineRemindersEnabled() != null) pref.setDeadlineRemindersEnabled(dto.getDeadlineRemindersEnabled());
        if (dto.getTaskAssignmentsEnabled() != null) pref.setTaskAssignmentsEnabled(dto.getTaskAssignmentsEnabled());
        if (dto.getDelayAlertsEnabled() != null) pref.setDelayAlertsEnabled(dto.getDelayAlertsEnabled());
        if (dto.getRecommendationAlertsEnabled() != null) pref.setRecommendationAlertsEnabled(dto.getRecommendationAlertsEnabled());

        NotificationPreference updated = preferenceRepository.save(pref);
        return NotificationPreferencesDto.fromEntity(updated);
    }

    @Transactional
    public int processOutboxRetries() {
        List<NotificationOutbox> pending = outboxRepository.findByStatusAndRetryCountLessThan(
                NotificationDeliveryStatus.FAILED, 3);
        int successCount = 0;
        for (NotificationOutbox item : pending) {
            item.setRetryCount(item.getRetryCount() + 1);
            item.setLastAttemptAt(LocalDateTime.now());
            NotificationDeliveryStatus status = emailService.sendEmail(item.getRecipientEmail(), item.getSubject(), item.getBody());
            item.setStatus(status);
            if (status == NotificationDeliveryStatus.SENT) {
                item.setErrorMessage(null);
                successCount++;
            } else {
                item.setErrorMessage("Retry attempt " + item.getRetryCount() + " failed");
            }
            outboxRepository.save(item);
        }
        return successCount;
    }

    public NotificationPreference getOrCreatePreferences(User user) {
        return preferenceRepository.findByUser(user).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUser(user);
            p.setInAppEnabled(true);
            p.setEmailEnabled(false);
            p.setDeadlineRemindersEnabled(true);
            p.setTaskAssignmentsEnabled(true);
            p.setDelayAlertsEnabled(true);
            p.setRecommendationAlertsEnabled(true);
            return preferenceRepository.save(p);
        });
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
