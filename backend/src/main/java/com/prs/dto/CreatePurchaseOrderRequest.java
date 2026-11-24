package com.prs.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchaseOrderRequest {
    private String poReference;
    private Long vendorId;
    private Long quotationId;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String currency = "INR";
    private String notes;
    private List<PurchaseOrderLineDto> lines;
}
