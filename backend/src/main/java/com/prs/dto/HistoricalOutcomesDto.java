package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalOutcomesDto {
    private long totalCompletedTasks;
    private long onTimeTasks;
    private long delayedTasks;
    private double onTimeRatePercent;
    private double averageVarianceDays;
    private Map<String, Long> delayReasonsDistribution;
    private List<WorkerReliabilityDto> workerReliability;
    private List<TaskOutcomeSummaryDto> recentOutcomes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkerReliabilityDto {
        private String workerName;
        private long totalTasks;
        private long delayedTasks;
        private double onTimeRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskOutcomeSummaryDto {
        private Long id;
        private Long taskId;
        private String taskName;
        private String projectName;
        private LocalDate plannedFinishDate;
        private LocalDate actualFinishDate;
        private int varianceWorkingDays;
        private boolean delayed;
        private String delayReason;
        private String workerName;
    }
}
