package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateQuotationRequest {
    private Long vendorId;
    private String quoteReference;
    private LocalDate issueDate;
    private LocalDate validUntil;
    private String currency = "INR";
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private String notes;
    private List<QuotationItemDto> items;
}
