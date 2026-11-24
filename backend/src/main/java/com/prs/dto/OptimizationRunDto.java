package com.prs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OptimizationRunDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private String initiatedBy;
    private String selectedObjective;
    private String status;
    private Integer candidatesEvaluated;
    private Integer feasibleCandidatesCount;
    private String inputScheduleVersion;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
