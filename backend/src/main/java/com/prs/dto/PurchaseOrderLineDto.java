package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderLineDto {
    private Long id;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private BigDecimal deliveredQuantity;
}
