package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "project_deliverables")
public class Deliverable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "deliverable_type", nullable = false)
    private DeliverableType deliverableType = DeliverableType.TECHNICAL_DOCUMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_user_id")
    private User responsibleUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id")
    private User assignedReviewer;

    @Column(name = "required_submission_date")
    private LocalDate requiredSubmissionDate;

    @Column(name = "actual_submission_date")
    private LocalDate actualSubmissionDate;

    @Column(name = "requested_decision_date")
    private LocalDate requestedDecisionDate;

    @Column(name = "actual_decision_date")
    private LocalDate actualDecisionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliverableStatus status = DeliverableStatus.PLANNED;

    @Column(name = "current_version_number")
    private Integer currentVersionNumber = 1;

    @Column(name = "is_client_visible")
    private Boolean isClientVisible = true;

    @Column(name = "is_confidential")
    private Boolean isConfidential = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
