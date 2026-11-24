package com.prs.repository;

import com.prs.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByPredecessorTaskId(Long predecessorTaskId);
    List<TaskDependency> findBySuccessorTaskId(Long successorTaskId);
    List<TaskDependency> findBySuccessorTaskProjectId(Long projectId);
    boolean existsByPredecessorTaskIdAndSuccessorTaskId(Long predecessorTaskId, Long successorTaskId);
}
