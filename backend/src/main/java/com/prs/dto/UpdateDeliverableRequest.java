package com.prs.dto;

import com.prs.entity.DeliverableStatus;
import com.prs.entity.DeliverableType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateDeliverableRequest {
    private String name;
    private String description;
    private DeliverableType deliverableType;
    private Long taskId;
    private Long responsibleUserId;
    private Long assignedReviewerId;
    private LocalDate requiredSubmissionDate;
    private LocalDate requestedDecisionDate;
    private DeliverableStatus status;
    private Boolean isClientVisible;
    private Boolean isConfidential;
}
