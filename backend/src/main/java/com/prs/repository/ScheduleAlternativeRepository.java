package com.prs.repository;

import com.prs.entity.ScheduleAlternative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleAlternativeRepository extends JpaRepository<ScheduleAlternative, Long> {
    List<ScheduleAlternative> findByOptimizationRunIdOrderByAlternativeRankAsc(Long optimizationRunId);
}
