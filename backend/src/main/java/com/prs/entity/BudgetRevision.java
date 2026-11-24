package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "budget_revisions")
public class BudgetRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private ProjectBudget budget;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "previous_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "revised_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal revisedAmount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
