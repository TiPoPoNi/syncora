package com.prs.repository;

import com.prs.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    java.util.List<Vendor> findByOrganizationId(Long organizationId);
    java.util.Optional<Vendor> findByIdAndOrganizationId(Long id, Long organizationId);
}
