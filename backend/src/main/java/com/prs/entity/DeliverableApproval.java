package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "deliverable_approvals")
public class DeliverableApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deliverable_id", nullable = false)
    private Deliverable deliverable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private DeliverableDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision;

    @Column(name = "decision_date")
    private LocalDateTime decisionDate;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private DeliverableStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_status")
    private DeliverableStatus resultingStatus;

    @Column(name = "turnaround_hours", precision = 10, scale = 2)
    private BigDecimal turnaroundHours;

    @PrePersist
    protected void onCreate() {
        if (decisionDate == null) {
            decisionDate = LocalDateTime.now();
        }
    }
}
