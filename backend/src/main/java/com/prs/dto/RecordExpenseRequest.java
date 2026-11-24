package com.prs.dto;

import com.prs.entity.CostCategory;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordExpenseRequest {
    private CostCategory category;
    private BigDecimal amount;
    private String currency = "INR";
    private LocalDate expenseDate;
    private String description;
    private Long taskId;
    private Long vendorId;
    private Long purchaseOrderId;
    private Long workerId;
}
