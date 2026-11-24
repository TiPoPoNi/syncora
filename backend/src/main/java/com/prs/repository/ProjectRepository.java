package com.prs.repository;

import com.prs.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOrganizationId(Long organizationId);
    Optional<Project> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<Project> findByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
    long countByOrganizationId(Long organizationId);

    List<Project> findByOrganizationIdAndClientId(Long organizationId, Long clientId);
    List<Project> findByClientId(Long clientId);
    long countByOrganizationIdAndClientId(Long organizationId, Long clientId);
    long countByClientId(Long clientId);
}

