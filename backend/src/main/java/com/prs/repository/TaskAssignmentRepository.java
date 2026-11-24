package com.prs.repository;

import com.prs.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByTaskId(Long taskId);
    List<TaskAssignment> findByWorkerId(Long workerId);
    List<TaskAssignment> findByVendorId(Long vendorId);
    List<TaskAssignment> findByTaskProjectId(Long projectId);
}
