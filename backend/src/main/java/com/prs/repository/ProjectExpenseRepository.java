package com.prs.repository;

import com.prs.entity.CostCategory;
import com.prs.entity.ProjectExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectExpenseRepository extends JpaRepository<ProjectExpense, Long> {
    List<ProjectExpense> findByProjectId(Long projectId);
    List<ProjectExpense> findByProjectIdAndCategory(Long projectId, CostCategory category);
    List<ProjectExpense> findByPurchaseOrderId(Long purchaseOrderId);
}
