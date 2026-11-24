package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reminder_deduplication_logs",
       uniqueConstraints = @UniqueConstraint(name = "uq_reminder_dedup",
               columnNames = {"target_type", "target_id", "reminder_type", "event_date", "recipient_user_id"}))
public class ReminderDeduplicationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false)
    private String targetType; // TASK, PROJECT

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reminder_type", nullable = false)
    private String reminderType; // DUE_24H, DUE_3D, OVERDUE, PROJECT_DEADLINE

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
