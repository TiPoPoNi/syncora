package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReviseBudgetRequest {
    private BigDecimal revisedAmount;
    private String reason;
}
