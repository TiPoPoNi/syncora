package com.prs.repository;

import com.prs.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    java.util.List<Client> findByOrganizationId(Long organizationId);
    java.util.Optional<Client> findByIdAndOrganizationId(Long id, Long organizationId);
}
