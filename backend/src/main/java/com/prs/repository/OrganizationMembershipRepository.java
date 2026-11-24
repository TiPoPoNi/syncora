package com.prs.repository;

import com.prs.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {
    List<OrganizationMembership> findByUserId(Long userId);
    List<OrganizationMembership> findByOrganizationId(Long organizationId);
    Optional<OrganizationMembership> findByUserIdAndOrganizationId(Long userId, Long organizationId);
    long countByOrganizationIdAndRoleAndStatus(Long organizationId, String role, String status);
    boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId);
    Optional<OrganizationMembership> findByOrganizationIdAndEmployeeId(Long organizationId, String employeeId);
    Optional<OrganizationMembership> findByEmployeeId(String employeeId);
    Optional<OrganizationMembership> findByInvitationToken(String invitationToken);
    boolean existsByOrganizationIdAndEmployeeId(Long organizationId, String employeeId);
    List<OrganizationMembership> findByOrganizationIdAndRole(Long organizationId, String role);
}
