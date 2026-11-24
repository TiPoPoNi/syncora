package com.prs.dto;

import com.prs.entity.ApprovalDecision;
import com.prs.entity.DeliverableStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DeliverableApprovalDto {
    private Long id;
    private Long deliverableId;
    private Long documentId;
    private Integer documentVersionNumber;
    private Long reviewerId;
    private String reviewerUsername;
    private ApprovalDecision decision;
    private LocalDateTime decisionDate;
    private String comments;
    private DeliverableStatus previousStatus;
    private DeliverableStatus resultingStatus;
    private BigDecimal turnaroundHours;
}
