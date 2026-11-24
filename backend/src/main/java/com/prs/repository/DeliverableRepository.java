package com.prs.repository;

import com.prs.entity.Deliverable;
import com.prs.entity.DeliverableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DeliverableRepository extends JpaRepository<Deliverable, Long> {
    List<Deliverable> findByProjectId(Long projectId);
    List<Deliverable> findByProjectIdAndIsClientVisibleTrueAndIsConfidentialFalse(Long projectId);
    List<Deliverable> findByTaskId(Long taskId);
    List<Deliverable> findByStatus(DeliverableStatus status);
    List<Deliverable> findByRequestedDecisionDateBeforeAndStatusIn(LocalDate date, List<DeliverableStatus> statuses);
}
