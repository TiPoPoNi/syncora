package com.prs.repository;

import com.prs.entity.ProjectBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectBaselineRepository extends JpaRepository<ProjectBaseline, Long> {
    List<ProjectBaseline> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<ProjectBaseline> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
}
