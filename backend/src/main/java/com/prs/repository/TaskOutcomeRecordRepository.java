package com.prs.repository;

import com.prs.entity.TaskOutcomeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskOutcomeRecordRepository extends JpaRepository<TaskOutcomeRecord, Long> {
    List<TaskOutcomeRecord> findByProjectId(Long projectId);
    List<TaskOutcomeRecord> findByProjectOrganizationId(Long organizationId);
    long countByProjectOrganizationId(Long organizationId);
    long countByIsDelayed(Boolean isDelayed);

    @Query("SELECT r.primaryDelayReason, COUNT(r) FROM TaskOutcomeRecord r WHERE r.isDelayed = true AND r.primaryDelayReason IS NOT NULL GROUP BY r.primaryDelayReason")
    List<Object[]> countDelayReasons();

    @Query("SELECT r.assignedWorker.name, COUNT(r), SUM(CASE WHEN r.isDelayed = true THEN 1 ELSE 0 END) FROM TaskOutcomeRecord r WHERE r.assignedWorker IS NOT NULL GROUP BY r.assignedWorker.name")
    List<Object[]> getWorkerReliabilityStats();
}
