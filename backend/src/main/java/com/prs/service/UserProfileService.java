package com.prs.service;

import com.prs.dto.ProfileChangeRequestDto;
import com.prs.dto.ReviewProfileChangeRequest;
import com.prs.dto.SubmitProfileChangeRequest;
import com.prs.dto.UserProfileDto;
import com.prs.entity.*;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.repository.OrganizationMembershipRepository;
import com.prs.repository.ProfileChangeRequestRepository;
import com.prs.repository.UserRepository;
import com.prs.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final WorkerRepository workerRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String username) {
        User user = getUserByUsername(username);

        List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
        OrganizationMembership primaryMembership = memberships.isEmpty() ? null : memberships.get(0);

        String employeeId = primaryMembership != null ? primaryMembership.getEmployeeId() : null;
        Long orgId = primaryMembership != null ? primaryMembership.getOrganization().getId() : null;
        String orgName = primaryMembership != null ? primaryMembership.getOrganization().getName() : null;
        String roleName = primaryMembership != null ? primaryMembership.getRole() : user.getRole().name();

        Optional<ProfileChangeRequest> pendingReqOpt = profileChangeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), ProfileChangeRequestStatus.PENDING);

        return UserProfileDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .jobTitle(user.getJobTitle())
                .department(user.getDepartment())
                .avatarUrl(user.getAvatarUrl())
                .employeeId(employeeId)
                .organizationId(orgId)
                .organizationName(orgName)
                .role(roleName)
                .hasPendingChangeRequest(pendingReqOpt.isPresent())
                .pendingChangeRequestId(pendingReqOpt.map(ProfileChangeRequest::getId).orElse(null))
                .build();
    }

    @Transactional
    public ProfileChangeRequestDto submitProfileChangeRequest(String username, SubmitProfileChangeRequest req) {
        User user = getUserByUsername(username);

        List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new IllegalArgumentException("User must belong to an active organization to submit profile change requests.");
        }
        OrganizationMembership membership = memberships.get(0);
        Organization organization = membership.getOrganization();

        // 1. Check for existing pending request
        Optional<ProfileChangeRequest> existingPending = profileChangeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), ProfileChangeRequestStatus.PENDING);
        if (existingPending.isPresent()) {
            throw new IllegalStateException("You already have an unresolved profile change request pending review (#" 
                    + existingPending.get().getId() + "). Please wait for an administrator to review it.");
        }

        // 2. Validate proposed vs current values
        String proposedName = trimToNull(req.getProposedFullName());
        String proposedPhone = trimToNull(req.getProposedPhone());
        String proposedJob = trimToNull(req.getProposedJobTitle());
        String proposedDept = trimToNull(req.getProposedDepartment());
        String proposedEmail = trimToNull(req.getProposedWorkEmail());
        String proposedAvatar = trimToNull(req.getProposedAvatarUrl());

        boolean hasDiff = !Objects.equals(proposedName, user.getFullName())
                || !Objects.equals(proposedPhone, user.getPhone())
                || !Objects.equals(proposedJob, user.getJobTitle())
                || !Objects.equals(proposedDept, user.getDepartment())
                || !Objects.equals(proposedAvatar, user.getAvatarUrl())
                || (proposedEmail != null && !proposedEmail.equalsIgnoreCase(user.getEmail()));

        if (!hasDiff) {
            throw new IllegalArgumentException("No differences detected between proposed fields and official profile.");
        }

        // Check if proposed email is already registered by another account
        if (proposedEmail != null && !proposedEmail.equalsIgnoreCase(user.getEmail())) {
            Optional<User> existingEmailUser = userRepository.findByEmailIgnoreCase(proposedEmail);
            if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("The proposed work email is already associated with another account.");
            }
        }

        // 3. Create pending profile change request
        ProfileChangeRequest changeRequest = new ProfileChangeRequest();
        changeRequest.setUser(user);
        changeRequest.setOrganization(organization);
        changeRequest.setStatus(ProfileChangeRequestStatus.PENDING);
        changeRequest.setSubmissionComment(trimToNull(req.getSubmissionComment()));

        // Snapshot current official values
        changeRequest.setExistingFullName(user.getFullName());
        changeRequest.setExistingPhone(user.getPhone());
        changeRequest.setExistingJobTitle(user.getJobTitle());
        changeRequest.setExistingDepartment(user.getDepartment());
        changeRequest.setExistingWorkEmail(user.getEmail());
        changeRequest.setExistingAvatarUrl(user.getAvatarUrl());

        // Proposed values
        changeRequest.setProposedFullName(proposedName != null ? proposedName : user.getFullName());
        changeRequest.setProposedPhone(proposedPhone != null ? proposedPhone : user.getPhone());
        changeRequest.setProposedJobTitle(proposedJob != null ? proposedJob : user.getJobTitle());
        changeRequest.setProposedDepartment(proposedDept != null ? proposedDept : user.getDepartment());
        changeRequest.setProposedAvatarUrl(proposedAvatar != null ? proposedAvatar : user.getAvatarUrl());

        if (proposedEmail != null && !proposedEmail.equalsIgnoreCase(user.getEmail())) {
            changeRequest.setProposedWorkEmail(proposedEmail);
            changeRequest.setEmailVerificationToken(UUID.randomUUID().toString());
            changeRequest.setEmailVerificationExpiresAt(LocalDateTime.now().plusDays(1));
            changeRequest.setEmailVerified(false);
        } else {
            changeRequest.setProposedWorkEmail(user.getEmail());
            changeRequest.setEmailVerified(true);
        }

        ProfileChangeRequest saved = profileChangeRequestRepository.save(changeRequest);
        log.info("Profile change request #{} created by user '{}' for org '{}'", saved.getId(), username, organization.getName());

        // 4. Notify authorized organization administrators
        List<OrganizationMembership> adminMemberships = membershipRepository.findByOrganizationId(organization.getId())
                .stream()
                .filter(m -> "ORGANIZATION_OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZATION_ADMIN".equalsIgnoreCase(m.getRole()))
                .filter(m -> !m.getUser().getId().equals(user.getId()))
                .toList();

        for (OrganizationMembership adminMember : adminMemberships) {
            notificationService.sendNotification(
                    adminMember.getUser(),
                    NotificationEventType.PROFILE_CHANGE_REQUESTED,
                    "New Profile Change Request",
                    "Employee " + (user.getFullName() != null ? user.getFullName() : user.getUsername()) 
                            + " submitted profile changes for organization review.",
                    null,
                    null
            );
        }

        return ProfileChangeRequestDto.fromEntity(saved, membership.getEmployeeId());
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeRequestDto> getUserChangeRequests(String username) {
        User user = getUserByUsername(username);
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
        String employeeId = memberships.isEmpty() ? null : memberships.get(0).getEmployeeId();

        return profileChangeRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(r -> ProfileChangeRequestDto.fromEntity(r, employeeId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeRequestDto> getOrganizationChangeRequests(Long orgId, String username, ProfileChangeRequestStatus status) {
        User reviewer = getUserByUsername(username);
        validateReviewerAuthorization(reviewer, orgId);

        List<ProfileChangeRequest> requests;
        if (status != null) {
            requests = profileChangeRequestRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, status);
        } else {
            requests = profileChangeRequestRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        }

        return requests.stream().map(r -> {
            Optional<OrganizationMembership> mem = membershipRepository.findByUserIdAndOrganizationId(r.getUser().getId(), orgId);
            return ProfileChangeRequestDto.fromEntity(r, mem.map(OrganizationMembership::getEmployeeId).orElse(null));
        }).collect(Collectors.toList());
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public ProfileChangeRequestDto approveChangeRequest(Long requestId, String username, ReviewProfileChangeRequest reviewReq) {
        User reviewer = getUserByUsername(username);
        ProfileChangeRequest request = profileChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Profile change request not found: " + requestId));

        Long orgId = request.getOrganization().getId();
        validateReviewerAuthorization(reviewer, orgId);

        // Security check: Employee self-approval rejection
        if (request.getUser().getId().equals(reviewer.getId())) {
            throw new IllegalArgumentException("Organization administrators cannot approve their own profile change requests. Review must be conducted by another administrator or owner.");
        }

        if (request.getStatus() != ProfileChangeRequestStatus.PENDING) {
            throw new IllegalStateException("Request #" + requestId + " is not pending (current status: " + request.getStatus() + ").");
        }

        User employee = request.getUser();

        // Stale detection: Check if official profile has changed since request submission
        boolean isStale = !Objects.equals(employee.getFullName(), request.getExistingFullName())
                || !Objects.equals(employee.getPhone(), request.getExistingPhone())
                || !Objects.equals(employee.getJobTitle(), request.getExistingJobTitle())
                || !Objects.equals(employee.getDepartment(), request.getExistingDepartment())
                || !Objects.equals(employee.getEmail(), request.getExistingWorkEmail());

        if (isStale) {
            request.setStatus(ProfileChangeRequestStatus.STALE);
            request.setReviewComment("Marked STALE: Official profile values were modified after request submission. Please submit a fresh request.");
            request.setReviewedBy(reviewer);
            request.setReviewedAt(LocalDateTime.now());
            profileChangeRequestRepository.saveAndFlush(request);

            notificationService.sendNotification(
                    employee,
                    NotificationEventType.PROFILE_CHANGE_STALE,
                    "Profile Change Request Marked Stale",
                    "Your profile change request #" + requestId + " was marked stale because your profile was updated elsewhere since submission.",
                    null,
                    null
            );

            throw new IllegalStateException("Request #" + requestId + " is stale because the employee's official profile was modified after submission. Request status updated to STALE.");
        }

        // Sensitive work email verification check
        if (request.getProposedWorkEmail() != null && !request.getProposedWorkEmail().equalsIgnoreCase(employee.getEmail())) {
            if (!Boolean.TRUE.equals(request.getEmailVerified())) {
                throw new IllegalStateException("Proposed work email address has not been verified yet. Verification is required before approving email change.");
            }
            // Double-check no collision occurred during pending period
            Optional<User> collision = userRepository.findByEmailIgnoreCase(request.getProposedWorkEmail());
            if (collision.isPresent() && !collision.get().getId().equals(employee.getId())) {
                throw new IllegalStateException("The proposed work email is now in use by another user.");
            }
            employee.setEmail(request.getProposedWorkEmail());
        }

        // Apply approved fields transactionally
        employee.setFullName(request.getProposedFullName());
        employee.setPhone(request.getProposedPhone());
        employee.setJobTitle(request.getProposedJobTitle());
        employee.setDepartment(request.getProposedDepartment());
        if (request.getProposedAvatarUrl() != null) {
            employee.setAvatarUrl(request.getProposedAvatarUrl());
        }
        userRepository.save(employee);

        // Keep Worker entity synchronized if linked
        workerRepository.findByUserId(employee.getId()).ifPresent(worker -> {
            if (employee.getFullName() != null && !employee.getFullName().isBlank()) {
                worker.setName(employee.getFullName());
            }
            if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
                worker.setEmail(employee.getEmail());
            }
            workerRepository.save(worker);
        });

        request.setStatus(ProfileChangeRequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        if (reviewReq != null && reviewReq.getComment() != null) {
            request.setReviewComment(reviewReq.getComment().trim());
        }
        ProfileChangeRequest saved = profileChangeRequestRepository.save(request);

        log.info("Profile change request #{} APPROVED by admin '{}' for user '{}'", requestId, username, employee.getUsername());

        // Notify employee
        notificationService.sendNotification(
                employee,
                NotificationEventType.PROFILE_CHANGE_APPROVED,
                "Profile Changes Approved",
                "Your profile change request has been approved by " + reviewer.getUsername() + ".",
                null,
                null
        );

        Optional<OrganizationMembership> mem = membershipRepository.findByUserIdAndOrganizationId(employee.getId(), orgId);
        return ProfileChangeRequestDto.fromEntity(saved, mem.map(OrganizationMembership::getEmployeeId).orElse(null));
    }

    @Transactional
    public ProfileChangeRequestDto rejectChangeRequest(Long requestId, String username, ReviewProfileChangeRequest reviewReq) {
        User reviewer = getUserByUsername(username);
        ProfileChangeRequest request = profileChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Profile change request not found: " + requestId));

        Long orgId = request.getOrganization().getId();
        validateReviewerAuthorization(reviewer, orgId);

        if (request.getStatus() != ProfileChangeRequestStatus.PENDING) {
            throw new IllegalStateException("Request #" + requestId + " is not pending (current status: " + request.getStatus() + ").");
        }

        request.setStatus(ProfileChangeRequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        String reason = (reviewReq != null && reviewReq.getComment() != null && !reviewReq.getComment().isBlank())
                ? reviewReq.getComment().trim()
                : "Request rejected by organization administrator.";
        request.setReviewComment(reason);

        ProfileChangeRequest saved = profileChangeRequestRepository.save(request);
        log.info("Profile change request #{} REJECTED by admin '{}' for user '{}'", requestId, username, request.getUser().getUsername());

        // Notify employee
        notificationService.sendNotification(
                request.getUser(),
                NotificationEventType.PROFILE_CHANGE_REJECTED,
                "Profile Changes Rejected",
                "Your profile change request was rejected. Reason: " + reason,
                null,
                null
        );

        Optional<OrganizationMembership> mem = membershipRepository.findByUserIdAndOrganizationId(request.getUser().getId(), orgId);
        return ProfileChangeRequestDto.fromEntity(saved, mem.map(OrganizationMembership::getEmployeeId).orElse(null));
    }

    @Transactional
    public boolean verifyEmailToken(Long requestId, String token) {
        ProfileChangeRequest request = profileChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Profile change request not found: " + requestId));

        if (request.getEmailVerificationToken() == null || !request.getEmailVerificationToken().equals(token)) {
            throw new IllegalArgumentException("Invalid verification token.");
        }

        if (request.getEmailVerificationExpiresAt() != null && LocalDateTime.now().isAfter(request.getEmailVerificationExpiresAt())) {
            throw new IllegalArgumentException("Email verification token has expired. Please submit a new request.");
        }

        request.setEmailVerified(true);
        profileChangeRequestRepository.save(request);
        log.info("Work email verified for profile change request #{}", requestId);
        return true;
    }

    private void validateReviewerAuthorization(User reviewer, Long orgId) {
        if (reviewer.getRole() == Role.ADMIN) {
            return;
        }

        OrganizationMembership mem = membershipRepository.findByUserIdAndOrganizationId(reviewer.getId(), orgId)
                .orElseThrow(() -> new TenantAccessDeniedException("Access denied: You are not a member of organization #" + orgId));

        boolean isAuthorized = ("ORGANIZATION_OWNER".equalsIgnoreCase(mem.getRole()) || "ORGANIZATION_ADMIN".equalsIgnoreCase(mem.getRole()))
                && "ACTIVE".equalsIgnoreCase(mem.getStatus());

        if (!isAuthorized) {
            throw new TenantAccessDeniedException("Access denied: Only organization administrators or owners can review profile change requests.");
        }
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    private String trimToNull(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
