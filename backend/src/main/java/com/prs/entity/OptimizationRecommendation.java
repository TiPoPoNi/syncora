package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "optimization_recommendations")
public class OptimizationRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "optimization_run_id")
    private OptimizationRun optimizationRun;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "alternative_id")
    private ScheduleAlternative scheduleAlternative;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationCategory category;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "estimated_impact", nullable = false, columnDefinition = "TEXT")
    private String estimatedImpact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationStatus status = RecommendationStatus.PROPOSED;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
