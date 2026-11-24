package com.prs.dto;

import com.prs.scheduler.ResourceConflict;
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
public class ScheduleAnalysisDto {
    private Long projectId;
    private String projectName;
    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDate expectedCompletionDate;
    private int minimumDurationWorkingDays;
    private String cpmAnalysisStatus;
    private String deadlineStatus; // ON_TRACK, AT_RISK, DELAYED
    private int delayDays;
    private List<CpmTaskDto> taskAnalysis;
    private List<String> criticalPath;
    private List<ResourceConflict> resourceConflicts;
    private List<String> schedulingWarnings;
}
