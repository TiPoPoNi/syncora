package com.prs.repository;

import com.prs.entity.OptimizationRecommendation;
import com.prs.entity.RecommendationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptimizationRecommendationRepository extends JpaRepository<OptimizationRecommendation, Long> {
    List<OptimizationRecommendation> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<OptimizationRecommendation> findByProjectIdAndStatus(Long projectId, RecommendationStatus status);
}
