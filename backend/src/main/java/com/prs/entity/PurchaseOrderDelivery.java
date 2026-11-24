package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "purchase_order_deliveries")
public class PurchaseOrderDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "po_line_id")
    private PurchaseOrderLine poLine;

    @Column(name = "delivery_reference", nullable = false, length = 50)
    private String deliveryReference;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "actual_delivery_date", nullable = false)
    private LocalDate actualDeliveryDate;

    @Column(name = "quantity_delivered", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityDelivered;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(nullable = false, length = 30)
    private String status = "ACCEPTED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
