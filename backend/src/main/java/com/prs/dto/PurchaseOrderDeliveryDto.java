package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PurchaseOrderDeliveryDto {
    private Long id;
    private Long purchaseOrderId;
    private String poReference;
    private Long poLineId;
    private String lineDescription;
    private String deliveryReference;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private BigDecimal quantityDelivered;
    private String receivedBy;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
}
