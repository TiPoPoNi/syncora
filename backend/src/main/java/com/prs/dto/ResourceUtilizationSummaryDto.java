package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUtilizationSummaryDto {
    private int totalWorkers;
    private int availableWorkers;
    private int unavailableWorkers;
    private int overallocatedWorkers;
    private int underutilizedWorkers;
    private int idleWorkers;

    private Double overallUtilizationPercent;
    private int totalAssignedHours;
    private int totalAvailableHours;

    private List<WorkerUtilizationItemDto> workerDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkerUtilizationItemDto {
        private Long workerId;
        private String workerName;
        private String email;
        private String skills;
        private String status;
        private Integer dailyCapacityHours;

        private int activeAssignmentsCount;
        private int assignedWorkingDays;
        private int availableWorkingDaysInPeriod;
        private int assignedHours;
        private int availableHours;

        private Double utilizationPercent;
        private String utilizationStatus; // OVERALLOCATED, OPTIMAL, UNDERUTILIZED, IDLE, UNAVAILABLE
        private int activeConflictCount;
        private List<String> assignedProjectNames;
    }
}
