package com.prs.dto;

import com.prs.entity.ApprovalDecision;
import lombok.Data;

@Data
public class ApprovalDecisionRequest {
    private ApprovalDecision decision;
    private String comments;
    private Long documentId;
}
