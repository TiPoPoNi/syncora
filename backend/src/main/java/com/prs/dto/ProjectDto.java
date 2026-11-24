package com.prs.dto;

import com.prs.entity.Priority;
import com.prs.entity.ProjectStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectDto {
    private Long id;
    private String name;
    private String description;
    private Long clientId;
    private String clientName;
    private Long managerId;
    private String managerName;
    private LocalDate startDate;
    private LocalDate deadline;
    private Priority priority;
    private ProjectStatus status;
    private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
