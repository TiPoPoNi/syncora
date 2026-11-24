package com.prs.repository;

import com.prs.entity.OptimizationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptimizationRunRepository extends JpaRepository<OptimizationRun, Long> {
    List<OptimizationRun> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
