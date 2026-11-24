package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelayImpactDto {
    private Long projectId;
    private String projectName;
    private LocalDate projectStartDate;
    private LocalDate baselineCompletionDate;
    private LocalDate forecastCompletionDate;
    private LocalDate clientDeadline;
    private int varianceFromBaselineWorkingDays;
    private int varianceFromDeadlineWorkingDays;
    private String deadlineStatus; // ON_TRACK, AT_RISK, DELAYED
    private List<TaskDelayDto> reportedDelays;
    private List<String> affectedTasks;
    private List<String> unaffectedTasks;
    private List<String> criticalTasksAffected;
    private List<String> primaryDelayReasons;
    private String explanation;
    private boolean criticalPathChanged;
    private List<String> originalCriticalPath;
    private List<String> revisedCriticalPath;
}
