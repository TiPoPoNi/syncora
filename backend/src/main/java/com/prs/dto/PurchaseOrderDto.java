package com.prs.dto;

import com.prs.entity.PoApprovalStatus;
import com.prs.entity.PoDeliveryStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderDto {
    private Long id;
    private String poReference;
    private Long projectId;
    private String projectName;
    private Long vendorId;
    private String vendorName;
    private Long quotationId;
    private String quoteReference;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String currency;
    private BigDecimal totalCommittedAmount;
    private PoApprovalStatus approvalStatus;
    private PoDeliveryStatus deliveryStatus;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String notes;
    private List<PurchaseOrderLineDto> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
