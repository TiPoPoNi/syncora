package com.prs.dto;

import com.prs.entity.DeliverableStatus;
import com.prs.entity.DeliverableType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DeliverableDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long taskId;
    private String taskName;
    private String name;
    private String description;
    private DeliverableType deliverableType;
    private Long responsibleUserId;
    private String responsibleUsername;
    private Long assignedReviewerId;
    private String assignedReviewerUsername;
    private LocalDate requiredSubmissionDate;
    private LocalDate actualSubmissionDate;
    private LocalDate requestedDecisionDate;
    private LocalDate actualDecisionDate;
    private DeliverableStatus status;
    private Integer currentVersionNumber;
    private Boolean isClientVisible;
    private Boolean isConfidential;
    private Boolean isOverdue;
    private Integer documentsCount;
    private Integer approvalsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
