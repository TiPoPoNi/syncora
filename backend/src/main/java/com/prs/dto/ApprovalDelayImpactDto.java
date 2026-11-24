package com.prs.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ApprovalDelayImpactDto {
    private Long deliverableId;
    private String deliverableName;
    private LocalDate requestedDecisionDate;
    private Long daysOverdue;
    private Boolean isOverdue;
    private Integer blockedTasksCount;
    private List<String> blockedTaskNames;
    private Boolean impactsCriticalPath;
    private Integer potentialScheduleSlipDays;
}
