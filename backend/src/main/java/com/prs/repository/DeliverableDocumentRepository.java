package com.prs.repository;

import com.prs.entity.DeliverableDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliverableDocumentRepository extends JpaRepository<DeliverableDocument, Long> {
    List<DeliverableDocument> findByDeliverableIdOrderByVersionNumberDesc(Long deliverableId);
    Optional<DeliverableDocument> findByDeliverableIdAndVersionNumber(Long deliverableId, Integer versionNumber);
    Optional<DeliverableDocument> findByStorageKey(String storageKey);
    Integer countByDeliverableId(Long deliverableId);
}
