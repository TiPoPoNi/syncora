package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SetBudgetRequest {
    private BigDecimal initialBudget;
    private String currency;
    private String notes;
}
