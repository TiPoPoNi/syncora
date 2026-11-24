package com.prs.repository;

import com.prs.entity.TaskBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskBaselineRepository extends JpaRepository<TaskBaseline, Long> {
    List<TaskBaseline> findByBaselineId(Long baselineId);
    List<TaskBaseline> findByTaskId(Long taskId);
}
