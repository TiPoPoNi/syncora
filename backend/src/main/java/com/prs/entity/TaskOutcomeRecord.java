package com.prs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_outcome_records")
public class TaskOutcomeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_finish_date")
    private LocalDate plannedFinishDate;

    @Column(name = "actual_start_date", nullable = false)
    private LocalDate actualStartDate;

    @Column(name = "actual_finish_date", nullable = false)
    private LocalDate actualFinishDate;

    @Column(name = "planned_duration")
    private Integer plannedDuration;

    @Column(name = "actual_duration")
    private Integer actualDuration;

    @Column(name = "variance_working_days")
    private Integer varianceWorkingDays;

    @Column(name = "is_delayed", nullable = false)
    private Boolean isDelayed;

    @Column(name = "primary_delay_reason")
    private String primaryDelayReason;

    @Column(name = "total_delays_reported")
    @Builder.Default
    private Integer totalDelaysReported = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private Worker assignedWorker;

    @Column(name = "was_critical_path")
    @Builder.Default
    private Boolean wasCriticalPath = false;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
