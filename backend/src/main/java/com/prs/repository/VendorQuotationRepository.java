package com.prs.repository;

import com.prs.entity.VendorQuotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorQuotationRepository extends JpaRepository<VendorQuotation, Long> {
    List<VendorQuotation> findByProjectId(Long projectId);
    Optional<VendorQuotation> findByQuoteReference(String quoteReference);
    boolean existsByQuoteReference(String quoteReference);
}
