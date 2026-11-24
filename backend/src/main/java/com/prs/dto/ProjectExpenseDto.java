package com.prs.dto;

import com.prs.entity.CostCategory;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectExpenseDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private CostCategory category;
    private BigDecimal amount;
    private String currency;
    private LocalDate expenseDate;
    private String description;
    private Long taskId;
    private String taskName;
    private Long vendorId;
    private String vendorName;
    private Long purchaseOrderId;
    private String poReference;
    private Long workerId;
    private String workerName;
    private String recordedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
