package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "optimization_runs")
public class OptimizationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;

    @Column(name = "selected_objective")
    private String selectedObjective = "BALANCED";

    @Column(name = "status")
    private String status = "COMPLETED";

    @Column(name = "candidates_evaluated")
    private Integer candidatesEvaluated = 0;

    @Column(name = "feasible_candidates_count")
    private Integer feasibleCandidatesCount = 0;

    @Column(name = "input_schedule_version")
    private String inputScheduleVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
}
