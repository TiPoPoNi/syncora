package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelayRiskDto {
    private Long taskId;
    private String taskName;
    private Long projectId;

    // Strict separation: Deterministic Schedule vs. ML Risk Estimate
    private String deterministicScheduleStatus; // e.g. "ON_SCHEDULE", "PROJECTED_DELAY"
    private Integer deterministicSlackDays;
    private Boolean isOnCriticalPath;

    // ML Advisory Layer
    private String mlPredictionStatus; // "NOT_READY", "EVALUATION", "ENABLED"
    private Double delayRiskScore;     // Probability estimate (0.0 - 1.0) if evaluated
    private String riskLevel;          // "LOW", "MEDIUM", "HIGH", "UNASSESSED"
    private String modelVersion;
    private List<String> contributingFactors;
    private String limitations;
    private LocalDateTime assessedAt;
}
