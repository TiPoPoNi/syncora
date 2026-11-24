package com.prs.repository;

import com.prs.entity.BudgetRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BudgetRevisionRepository extends JpaRepository<BudgetRevision, Long> {
    List<BudgetRevision> findByBudgetIdOrderByRevisionNumberDesc(Long budgetId);
    int countByBudgetId(Long budgetId);
}
