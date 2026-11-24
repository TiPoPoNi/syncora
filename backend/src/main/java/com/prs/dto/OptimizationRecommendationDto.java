package com.prs.dto;

import com.prs.entity.RecommendationCategory;
import com.prs.entity.RecommendationStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OptimizationRecommendationDto {
    private Long id;
    private Long projectId;
    private Long optimizationRunId;
    private Long alternativeId;
    private RecommendationCategory category;
    private String summary;
    private String explanation;
    private String estimatedImpact;
    private RecommendationStatus status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private ScheduleAlternativeDto alternative;
}
