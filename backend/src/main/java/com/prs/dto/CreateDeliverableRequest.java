package com.prs.dto;

import com.prs.entity.DeliverableType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateDeliverableRequest {
    private String name;
    private String description;
    private DeliverableType deliverableType = DeliverableType.TECHNICAL_DOCUMENT;
    private Long taskId;
    private Long responsibleUserId;
    private Long assignedReviewerId;
    private LocalDate requiredSubmissionDate;
    private LocalDate requestedDecisionDate;
    private Boolean isClientVisible = true;
    private Boolean isConfidential = false;
}
