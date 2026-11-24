package com.prs.repository;

import com.prs.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    long countByProjectId(Long projectId);
    java.util.List<Task> findByProjectId(Long projectId);
}
