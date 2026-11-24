package com.prs.repository;

import com.prs.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByProjectId(Long projectId);
    List<PurchaseOrder> findByVendorId(Long vendorId);
    Optional<PurchaseOrder> findByPoReference(String poReference);
    boolean existsByPoReference(String poReference);
}
