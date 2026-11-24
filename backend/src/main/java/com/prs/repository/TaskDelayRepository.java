package com.prs.repository;

import com.prs.entity.TaskDelay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskDelayRepository extends JpaRepository<TaskDelay, Long> {
    List<TaskDelay> findByTaskId(Long taskId);

    @Query("SELECT d FROM TaskDelay d WHERE d.task.project.id = :projectId ORDER BY d.createdAt DESC")
    List<TaskDelay> findByProjectId(@Param("projectId") Long projectId);
}
