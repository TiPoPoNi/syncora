package com.prs.repository;

import com.prs.entity.OptimizationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptimizationAuditLogRepository extends JpaRepository<OptimizationAuditLog, Long> {
    List<OptimizationAuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
