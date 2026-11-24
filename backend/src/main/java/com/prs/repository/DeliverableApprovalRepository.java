package com.prs.repository;

import com.prs.entity.DeliverableApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliverableApprovalRepository extends JpaRepository<DeliverableApproval, Long> {
    List<DeliverableApproval> findByDeliverableIdOrderByDecisionDateDesc(Long deliverableId);
    List<DeliverableApproval> findByDeliverableIdAndDocumentId(Long deliverableId, Long documentId);
}
