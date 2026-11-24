package com.prs.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TaskResourceRecommendationsResponse {
    private Long taskId;
    private String taskName;
    private Integer durationDays;
    private String requiredSkills;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private String currentAssignee;
    private String currentAssigneeType;
    private List<ResourceCandidateDto> candidateWorkers;
    private List<ResourceCandidateDto> candidateVendors;
}
