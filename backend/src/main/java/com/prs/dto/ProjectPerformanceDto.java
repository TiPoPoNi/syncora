package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPerformanceDto {
    private Long projectId;
    private String projectName;
    private Long clientId;
    private String clientName;
    private Long managerId;
    private String managerName;
    private String status;
    private String priority;

    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDate expectedCompletionDate;
    private LocalDate actualFinishDate;

    // Variance metrics
    private Integer scheduleVarianceDays;
    private String varianceType; // "ACTUAL_VARIANCE" or "FORECAST_VARIANCE"
    private String delayStatus;   // "ON_SCHEDULE", "AT_RISK", "CRITICAL_DELAY", "COMPLETED_ON_TIME", "COMPLETED_LATE", "ON_HOLD", "CANCELLED"

    // Task counts
    private int totalTasks;
    private int completedTasks;
    private int overdueTasks;
    private int progressPercent;

    // Risk indicators
    private boolean hasActiveConflict;
    private boolean hasCriticalPathDelay;
    private boolean hasVendorDelay;

    // ML Delay Risk (if available)
    private String mlRiskStatus;
    private Double mlRiskScore;
}
