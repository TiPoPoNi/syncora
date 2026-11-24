package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTrendsDto {
    private List<MonthlyTrendPointDto> monthlyTrends;
    private Map<String, Long> delayReasonsBreakdown;
    private Map<String, Long> projectStatusDistribution;
    private Map<String, Long> priorityDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrendPointDto {
        private String month; // e.g. "2026-05"
        private int completedProjects;
        private int onTimeProjects;
        private Double avgVarianceDays;
        private int completedTasks;
    }
}
