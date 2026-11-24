package com.prs.repository;

import com.prs.entity.ProfileChangeRequest;
import com.prs.entity.ProfileChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {

    List<ProfileChangeRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ProfileChangeRequest> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<ProfileChangeRequest> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, ProfileChangeRequestStatus status);

    boolean existsByUserIdAndOrganizationIdAndStatus(Long userId, Long organizationId, ProfileChangeRequestStatus status);

    Optional<ProfileChangeRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ProfileChangeRequestStatus status);

    Optional<ProfileChangeRequest> findByEmailVerificationToken(String emailVerificationToken);
}
