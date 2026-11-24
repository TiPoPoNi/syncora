package com.prs.repository;

import com.prs.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    java.util.List<Worker> findByOrganizationId(Long organizationId);
    java.util.Optional<Worker> findByIdAndOrganizationId(Long id, Long organizationId);
    java.util.Optional<Worker> findByUserId(Long userId);
}
