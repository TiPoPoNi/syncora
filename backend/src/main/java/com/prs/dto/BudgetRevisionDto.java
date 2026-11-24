package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BudgetRevisionDto {
    private Long id;
    private Long budgetId;
    private Integer revisionNumber;
    private BigDecimal previousAmount;
    private BigDecimal revisedAmount;
    private String reason;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
