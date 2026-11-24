package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "task_delays")
public class TaskDelay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "delay_duration_days", nullable = false)
    private Integer delayDurationDays;

    @Column(name = "delay_reason", nullable = false)
    private String delayReason;

    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate;

    @Column(name = "updated_expected_finish")
    private LocalDate updatedExpectedFinish;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reported_by")
    private String reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DelayStatus status = DelayStatus.REPORTED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (reportedDate == null) {
            reportedDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
