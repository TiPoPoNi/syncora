package com.prs.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class DashboardDto {
    private long totalProjects;
    private long activeProjects;
    private long completedProjects;
    private long delayedProjects;
    private long projectsAtRisk;
    private long totalWorkers;
    private long totalVendors;
    private long upcomingDeadlines;
    private long overdueTasks;
    private long resourceConflictsCount;

    private List<DashboardProjectSummaryDto> recentProjects;
    private List<DashboardProjectSummaryDto> projectsRequiringAttention;
    private List<DashboardMilestoneDto> upcomingMilestones;
    private List<DashboardWorkerWorkloadDto> resourceWorkload;
    private List<DashboardActivityDto> recentActivities;

    @Data
    public static class DashboardActivityDto {
        private Long id;
        private String action;
        private String projectName;
        private String performedBy;
        private String details;
        private String timestamp;
    }

    @Data
    public static class DashboardProjectSummaryDto {
        private Long id;
        private String name;
        private String clientName;
        private String status;
        private int progress;
        private LocalDate deadline;
        private LocalDate expectedCompletionDate;
        private boolean isDelayed;
        private boolean hasResourceConflict;
        private boolean atRisk;
        private String riskReason;
        private List<String> conflictDetails;
    }

    @Data
    public static class DashboardMilestoneDto {
        private Long id;
        private String taskName;
        private String projectName;
        private LocalDate deadline;
        private LocalDate plannedFinishDate;
        private String priority;
        private String status;
        private boolean deadlineExceeded;
        private Long delayDays;
    }

    @Data
    public static class DashboardWorkerWorkloadDto {
        private Long workerId;
        private String name;
        private String skills;
        private String status; // Deprecated backwards compatibility: keeps roster status
        private String rosterStatus; // Roster deployment availability: AVAILABLE, BUSY, UNAVAILABLE
        private int activeAssignedTasks;
        private boolean hasConflict;
        private String workloadStatus; // OVERALLOCATED, ACTIVE, AVAILABLE, UNAVAILABLE
        private String workloadSummary; // Human-readable description
    }
}
