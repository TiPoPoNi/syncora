package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "in_app_enabled")
    private Boolean inAppEnabled = true;

    @Column(name = "email_enabled")
    private Boolean emailEnabled = false;

    @Column(name = "deadline_reminders_enabled")
    private Boolean deadlineRemindersEnabled = true;

    @Column(name = "task_assignments_enabled")
    private Boolean taskAssignmentsEnabled = true;

    @Column(name = "delay_alerts_enabled")
    private Boolean delayAlertsEnabled = true;

    @Column(name = "recommendation_alerts_enabled")
    private Boolean recommendationAlertsEnabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
