package com.prs.dto;

import com.prs.entity.ProjectStatus;
import com.prs.scheduler.ResourceConflict;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectScheduleDto {
    private Long projectId;
    private String projectName;
    private LocalDate startDate;
    private LocalDate deadline;
    private LocalDate expectedCompletionDate;
    private ProjectStatus status;
    private String deadlineStatus; // ON_TRACK, AT_RISK, DELAYED
    private Integer delayDays;
    
    private List<TaskScheduleDto> tasks;
    private List<ResourceConflict> conflicts;
}
