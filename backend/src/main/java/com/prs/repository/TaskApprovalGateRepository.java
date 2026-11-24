package com.prs.repository;

import com.prs.entity.GateType;
import com.prs.entity.TaskApprovalGate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskApprovalGateRepository extends JpaRepository<TaskApprovalGate, Long> {
    List<TaskApprovalGate> findByTaskId(Long taskId);
    List<TaskApprovalGate> findByDeliverableId(Long deliverableId);
    Optional<TaskApprovalGate> findByTaskIdAndDeliverableIdAndGateType(Long taskId, Long deliverableId, GateType gateType);
    boolean existsByTaskIdAndDeliverableIdAndGateType(Long taskId, Long deliverableId, GateType gateType);
}
