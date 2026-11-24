package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioDeadlineRiskSummaryDto {
    private int projectsMeetingDeadline;
    private int projectsMissingDeadline;
    private int projectsWithConflicts;
    private int projectsWithCriticalPathDelays;
    private int projectsWithLowSlack;
    private int projectsAffectedByVendorDelays;
    private int projectsRequiringAttention;

    private List<Long> projectsNeedingAttentionIds;
    private List<Long> projectsMissingDeadlineIds;
}
