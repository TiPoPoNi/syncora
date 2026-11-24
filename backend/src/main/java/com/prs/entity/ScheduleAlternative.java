package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "schedule_alternatives")
public class ScheduleAlternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "optimization_run_id", nullable = false)
    private OptimizationRun optimizationRun;

    @Column(name = "alternative_rank", nullable = false)
    private Integer alternativeRank;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "proposed_reassignments", columnDefinition = "TEXT")
    private String proposedReassignments;

    @Column(name = "proposed_schedule_changes", columnDefinition = "TEXT")
    private String proposedScheduleChanges;

    @Column(name = "project_completion_forecast", nullable = false)
    private LocalDate projectCompletionForecast;

    @Column(name = "deadline_status", nullable = false)
    private String deadlineStatus;

    @Column(name = "working_days_saved")
    private Integer workingDaysSaved = 0;

    @Column(name = "conflicts_resolved")
    private Integer conflictsResolved = 0;

    @Column(name = "remaining_conflicts")
    private Integer remainingConflicts = 0;

    @Column(name = "disruption_count")
    private Integer disruptionCount = 0;

    @Column(name = "objective_score", nullable = false)
    private Double objectiveScore;

    @Column(name = "constraint_status")
    private String constraintStatus = "VALID";

    @Column(name = "trade_offs", columnDefinition = "TEXT")
    private String tradeOffs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
