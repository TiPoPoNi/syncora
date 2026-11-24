package com.prs.dto;

import com.prs.entity.QuotationStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VendorQuotationDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long vendorId;
    private String vendorName;
    private String quoteReference;
    private LocalDate issueDate;
    private LocalDate validUntil;
    private String currency;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private QuotationStatus status;
    private String notes;
    private List<QuotationItemDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
