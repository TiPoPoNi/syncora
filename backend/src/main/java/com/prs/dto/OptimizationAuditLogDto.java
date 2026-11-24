package com.prs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OptimizationAuditLogDto {
    private Long id;
    private Long projectId;
    private Long runId;
    private Long recommendationId;
    private String action;
    private String performedBy;
    private String details;
    private LocalDateTime createdAt;
}
