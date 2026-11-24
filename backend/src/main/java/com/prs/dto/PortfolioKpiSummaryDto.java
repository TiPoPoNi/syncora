package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioKpiSummaryDto {
    private int totalProjects;
    private int activeProjects;
    private int completedProjects;
    private int onHoldProjects;
    private int cancelledProjects;

    private int projectsForecastToMissDeadlines;
    private int totalOverdueTasks;
    private int activeConflictsCount;

    // Historical metrics (null if denominator is zero)
    private Double historicalOnTimeDeliveryRatePercent;
    private Double averageScheduleVarianceDays;
    private int completedProjectsWithRecordedOutcome;

    // Resource & Vendor metrics
    private Double resourceUtilizationPercent;
    private Double vendorDeliveryReliabilityPercent;
    private int totalVendorsEvaluated;
    private int totalWorkersEvaluated;

    private String reportingPeriod;
    private Map<String, Long> projectStatusCounts;
}
