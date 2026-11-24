package com.prs.dto;

import com.prs.entity.BudgetStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProjectBudgetDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private BigDecimal initialBudget;
    private BigDecimal approvedBudget;
    private String currency;
    private BudgetStatus status;
    private String notes;
    private Integer revisionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
