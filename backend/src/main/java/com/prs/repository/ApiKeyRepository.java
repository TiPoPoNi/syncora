package com.prs.repository;

import com.prs.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<ApiKey> findByIdAndOrganizationId(Long id, Long organizationId);
}
