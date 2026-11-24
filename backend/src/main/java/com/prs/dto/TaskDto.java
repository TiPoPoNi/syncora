package com.prs.dto;

import com.prs.entity.Priority;
import com.prs.entity.TaskStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate deadline;
    private Integer estimatedDuration;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private Priority priority;
    private TaskStatus status;
    private Integer progress;
    private Long assignedWorkerId;
    private String assignedWorkerName;
    private String assignedWorkerSkills;
    private Integer predecessorCount;
    private Integer successorCount;
    private Boolean isBlocked;
    private String blockedReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
