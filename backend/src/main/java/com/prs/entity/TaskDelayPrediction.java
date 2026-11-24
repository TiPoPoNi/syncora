package com.prs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_delay_predictions")
public class TaskDelayPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "prediction_target")
    @Builder.Default
    private String predictionTarget = "TASK_DELAY_RISK";

    @Column(name = "delay_risk_score", nullable = false)
    private Double delayRiskScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel; // 'LOW', 'MEDIUM', 'HIGH'

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "features_snapshot", columnDefinition = "TEXT")
    private String featuresSnapshot;

    @Column(name = "contributing_factors", columnDefinition = "TEXT")
    private String contributingFactors;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
