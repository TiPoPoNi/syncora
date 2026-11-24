package com.prs.repository;

import com.prs.entity.PurchaseOrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PurchaseOrderDeliveryRepository extends JpaRepository<PurchaseOrderDelivery, Long> {
    List<PurchaseOrderDelivery> findByPurchaseOrderId(Long purchaseOrderId);
}
