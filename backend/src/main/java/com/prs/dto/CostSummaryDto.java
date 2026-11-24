package com.prs.dto;

import com.prs.entity.CostCategory;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CostSummaryDto {
    private Long projectId;
    private String projectName;
    private String currency;
    private BigDecimal approvedBudget;
    private BigDecimal totalCommittedCost;
    private BigDecimal totalActualCost;
    private BigDecimal remainingBudget;
    private BigDecimal remainingCommittedBudget;
    private BigDecimal costVariance;
    private BigDecimal costVariancePercent;
    private boolean isOverBudget;
    private Map<CostCategory, BigDecimal> categoryBreakdown;
    private List<ProjectExpenseDto> recentExpenses;
    private List<PurchaseOrderDto> activePurchaseOrders;
}
