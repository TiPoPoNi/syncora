package com.prs.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecordDeliveryRequest {
    private Long poLineId;
    private String deliveryReference;
    private LocalDate expectedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private BigDecimal quantityDelivered;
    private String status = "ACCEPTED";
    private String notes;
}
