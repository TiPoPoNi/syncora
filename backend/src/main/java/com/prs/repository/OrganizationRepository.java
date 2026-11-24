package com.prs.repository;

import com.prs.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findBySlug(String slug);
    Optional<Organization> findBySlugIgnoreCase(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    Optional<Organization> findByOrgCode(String orgCode);
    Optional<Organization> findByOrgCodeIgnoreCase(String orgCode);
    boolean existsByOrgCode(String orgCode);
    boolean existsByOrgCodeIgnoreCase(String orgCode);
}
