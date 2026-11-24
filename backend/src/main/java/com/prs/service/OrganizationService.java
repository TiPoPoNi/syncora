package com.prs.service;

import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.tenant.TenantAccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;

    public static final Set<String> ALLOWED_ORG_ROLES = Set.of(
            "ORGANIZATION_OWNER", "ORGANIZATION_ADMIN", "PROJECT_MANAGER", "CLIENT", "WORKER", "VENDOR");

    @Transactional(readOnly = true)
    public List<OrganizationDto> getUserOrganizations(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (user.getRole() == Role.ADMIN) {
            // Global admin can see all organizations
            return organizationRepository.findAll().stream().map(org -> {
                Optional<OrganizationMembership> memOpt = membershipRepository
                        .findByUserIdAndOrganizationId(user.getId(), org.getId());
                String role = memOpt.map(OrganizationMembership::getRole).orElse("GLOBAL_ADMIN");
                return mapToDto(org, role);
            }).collect(Collectors.toList());
        }

        List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
        return memberships.stream()
                .map(m -> mapToDto(m.getOrganization(), m.getRole()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrganizationDto getOrganization(Long orgId, String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        String role = checkMembershipOrAdmin(user, org);
        return mapToDto(org, role);
    }

    @Transactional
    public synchronized String generateNextEmployeeId(Organization org) {
        int currentCount = org.getEmployeeIdCounter() != null ? org.getEmployeeIdCounter() : 0;
        int nextCount = currentCount + 1;
        org.setEmployeeIdCounter(nextCount);
        organizationRepository.saveAndFlush(org);

        String prefix = (org.getOrgCode() != null && !org.getOrgCode().isBlank())
                ? org.getOrgCode().trim().toUpperCase()
                : "SYN";
        String candidate = String.format("%s-EMP-%04d", prefix, nextCount);

        // Guarantee collision freedom
        while (membershipRepository.existsByOrganizationIdAndEmployeeId(org.getId(), candidate)) {
            nextCount++;
            org.setEmployeeIdCounter(nextCount);
            organizationRepository.saveAndFlush(org);
            candidate = String.format("%s-EMP-%04d", prefix, nextCount);
        }
        return candidate;
    }

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest req, String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name cannot be empty.");
        }

        String slug = req.getSlug();
        if (slug == null || slug.trim().isEmpty()) {
            slug = req.getName().trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        } else {
            slug = slug.trim().toLowerCase();
        }

        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            throw new IllegalArgumentException("Organization slug already exists: " + slug);
        }

        String orgCode = req.getOrgCode();
        if (orgCode == null || orgCode.trim().isEmpty()) {
            String[] words = req.getName().trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (!w.isEmpty())
                    sb.append(Character.toUpperCase(w.charAt(0)));
            }
            if (sb.length() < 3) {
                String clean = req.getName().replaceAll("[^a-zA-Z]", "").toUpperCase();
                orgCode = clean.length() >= 3 ? clean.substring(0, 3) : "SYN";
            } else {
                orgCode = sb.substring(0, Math.min(sb.length(), 4));
            }
        } else {
            orgCode = orgCode.trim().toUpperCase();
        }

        if (organizationRepository.existsByOrgCodeIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Organization code already exists: " + orgCode);
        }

        Organization org = new Organization();
        org.setName(req.getName().trim());
        org.setSlug(slug);
        org.setOrgCode(orgCode);
        org.setStatus("ACTIVE");
        org.setEmployeeIdCounter(1);
        Organization savedOrg = organizationRepository.save(org);

        // Creator becomes ORGANIZATION_OWNER
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(user);
        membership.setOrganization(savedOrg);
        membership.setRole("ORGANIZATION_OWNER");
        membership.setStatus("ACTIVE");
        membership.setInvitationStatus("ACCEPTED");
        membership.setEmployeeId(String.format("%s-EMP-0001", orgCode));
        membership.setActivatedAt(LocalDateTime.now());
        membershipRepository.save(membership);

        return mapToDto(savedOrg, "ORGANIZATION_OWNER");
    }

    @Transactional
    public OrganizationDto updateOrganization(Long orgId, CreateOrganizationRequest req, String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(user, org);

        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            org.setName(req.getName().trim());
        }

        if (req.getSlug() != null && !req.getSlug().trim().isEmpty()) {
            String newSlug = req.getSlug().trim().toLowerCase();
            if (!newSlug.equals(org.getSlug())) {
                if (organizationRepository.existsBySlug(newSlug)) {
                    throw new IllegalArgumentException("Organization slug already exists: " + newSlug);
                }
                org.setSlug(newSlug);
            }
        }

        if (req.getOrgCode() != null && !req.getOrgCode().trim().isEmpty()) {
            org.setOrgCode(req.getOrgCode().trim().toUpperCase());
        }

        if (req.getStatus() != null
                && ("ACTIVE".equalsIgnoreCase(req.getStatus()) || "SUSPENDED".equalsIgnoreCase(req.getStatus()))) {
            org.setStatus(req.getStatus().toUpperCase());
        }

        return mapToDto(organizationRepository.save(org), "ORGANIZATION_ADMIN");
    }

    @Transactional
    public OrganizationDto setOrganizationStatus(Long orgId, String status, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(user, org);

        if (!"ACTIVE".equalsIgnoreCase(status) && !"SUSPENDED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Allowed values: ACTIVE, SUSPENDED.");
        }

        org.setStatus(status.toUpperCase());
        return mapToDto(organizationRepository.save(org), "ORGANIZATION_ADMIN");
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberDto> getMembers(Long orgId, String username) {
        return getMembers(orgId, username, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberDto> getMembers(Long orgId, String username, String search, String role,
            String status, String invitationStatus) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkMembershipOrAdmin(user, org);

        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);

        return members.stream()
                .filter(m -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase().trim();
                        boolean matchUser = m.getUser().getUsername().toLowerCase().contains(s);
                        boolean matchEmail = m.getUser().getEmail() != null
                                && m.getUser().getEmail().toLowerCase().contains(s);
                        boolean matchEmp = m.getEmployeeId() != null && m.getEmployeeId().toLowerCase().contains(s);
                        if (!matchUser && !matchEmail && !matchEmp)
                            return false;
                    }
                    if (role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")) {
                        if (!m.getRole().equalsIgnoreCase(role.trim()))
                            return false;
                    }
                    if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                        if (!m.getStatus().equalsIgnoreCase(status.trim()))
                            return false;
                    }
                    if (invitationStatus != null && !invitationStatus.isBlank()
                            && !invitationStatus.equalsIgnoreCase("ALL")) {
                        if (!m.getInvitationStatus().equalsIgnoreCase(invitationStatus.trim()))
                            return false;
                    }
                    return true;
                })
                .map(this::mapToMemberDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationMemberDto inviteMember(Long orgId, InviteMemberRequest req, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        if (req.getEmail() == null || req.getEmail().trim().isEmpty() || !req.getEmail().contains("@")) {
            throw new IllegalArgumentException("Valid work email address is required.");
        }

        String targetEmail = req.getEmail().trim().toLowerCase();
        String role = req.getRole() != null ? req.getRole().trim().toUpperCase() : "WORKER";
        if ("ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException(
                    "Cannot grant global application administrator privileges through organization membership.");
        }
        if (!ALLOWED_ORG_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "Invalid organization role: " + role + ". Allowed: " + ALLOWED_ORG_ROLES);
        }

        // Find or create user
        User targetUser = userRepository.findByEmailIgnoreCase(targetEmail).orElse(null);
        if (targetUser == null) {
            targetUser = new User();
            String generatedUsername = targetEmail.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
            String unameCandidate = generatedUsername;
            int counter = 1;
            while (userRepository.existsByUsernameIgnoreCase(unameCandidate)) {
                unameCandidate = generatedUsername + counter++;
            }
            targetUser.setUsername(unameCandidate);
            targetUser.setEmail(targetEmail);
            targetUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Temporary randomized hash
            targetUser.setRole(mapOrgRoleToUserRole(role));
            targetUser.setAccountStatus("PENDING_ACTIVATION");
            targetUser = userRepository.save(targetUser);
        }

        if (membershipRepository.existsByUserIdAndOrganizationId(targetUser.getId(), orgId)) {
            throw new IllegalArgumentException("User '" + targetEmail + "' is already a member of " + org.getName());
        }

        // Generate or assign employee ID
        String employeeId;
        if (req.getCustomEmployeeId() != null && !req.getCustomEmployeeId().trim().isEmpty()) {
            employeeId = req.getCustomEmployeeId().trim().toUpperCase();
            if (membershipRepository.existsByOrganizationIdAndEmployeeId(orgId, employeeId)) {
                throw new IllegalArgumentException(
                        "Employee ID '" + employeeId + "' is already assigned within this organization.");
            }
        } else {
            employeeId = generateNextEmployeeId(org);
        }

        String invitationToken = UUID.randomUUID().toString().replace("-", "");

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(targetUser);
        membership.setOrganization(org);
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        membership.setEmployeeId(employeeId);
        membership.setInvitationToken(invitationToken);
        membership.setInvitationStatus("PENDING");
        membership.setInvitationExpiresAt(LocalDateTime.now().plusDays(7));

        OrganizationMembership saved = membershipRepository.save(membership);

        // If worker, ensure Worker record exists
        if ("WORKER".equalsIgnoreCase(role)) {
            final User finalTargetUser = targetUser;
            workerRepository.findByUserId(finalTargetUser.getId()).orElseGet(() -> {
                Worker w = new Worker();
                w.setUser(finalTargetUser);
                w.setOrganization(org);
                w.setName(req.getFullName() != null && !req.getFullName().isBlank() ? req.getFullName().trim()
                        : finalTargetUser.getUsername());
                w.setEmail(targetEmail);
                w.setStatus(WorkerStatus.AVAILABLE);
                return workerRepository.save(w);
            });
        }

        return mapToMemberDto(saved);
    }

    @Transactional
    public OrganizationMemberDto resendInvitation(Long orgId, Long membershipId, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership record not found: " + membershipId));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new TenantAccessDeniedException("Membership does not belong to organization " + org.getName());
        }

        if ("ACCEPTED".equalsIgnoreCase(membership.getInvitationStatus())) {
            throw new IllegalStateException("Account is already activated. Invitation cannot be resent.");
        }

        String newToken = UUID.randomUUID().toString().replace("-", "");
        membership.setInvitationToken(newToken);
        membership.setInvitationStatus("PENDING");
        membership.setInvitationExpiresAt(LocalDateTime.now().plusDays(7));

        return mapToMemberDto(membershipRepository.save(membership));
    }

    @Transactional
    public OrganizationMemberDto revokeInvitation(Long orgId, Long membershipId, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership record not found: " + membershipId));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new TenantAccessDeniedException("Membership does not belong to organization " + org.getName());
        }

        if ("ACCEPTED".equalsIgnoreCase(membership.getInvitationStatus())) {
            throw new IllegalStateException("Account has already been activated and cannot be revoked via invitation.");
        }

        membership.setInvitationStatus("REVOKED");
        membership.setInvitationToken(null);

        return mapToMemberDto(membershipRepository.save(membership));
    }

    @Transactional
    public OrganizationMemberDto addMember(Long orgId, AddMemberRequest req, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Target username or email cannot be empty.");
        }

        String targetIdent = req.getUsername().trim();
        User targetUser = userRepository.findByUsernameIgnoreCase(targetIdent)
                .or(() -> userRepository.findByEmailIgnoreCase(targetIdent))
                .orElseThrow(() -> new IllegalArgumentException("Target user not found: " + targetIdent));

        String role = req.getRole() != null ? req.getRole().trim().toUpperCase() : "WORKER";
        if ("ADMIN".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException(
                    "Cannot grant global application administrator privileges through organization membership.");
        }
        if (!ALLOWED_ORG_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "Invalid organization role: " + role + ". Allowed: " + ALLOWED_ORG_ROLES);
        }

        if (membershipRepository.existsByUserIdAndOrganizationId(targetUser.getId(), orgId)) {
            throw new IllegalArgumentException(
                    "User '" + targetUser.getUsername() + "' is already a member of " + org.getName());
        }

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(targetUser);
        membership.setOrganization(org);
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        membership.setInvitationStatus("ACCEPTED");
        membership.setActivatedAt(LocalDateTime.now());
        membership.setEmployeeId(generateNextEmployeeId(org));

        return mapToMemberDto(membershipRepository.save(membership));
    }

    @Transactional
    public OrganizationMemberDto updateMember(Long orgId, Long membershipId, UpdateMemberRequest req, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership record not found: " + membershipId));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new TenantAccessDeniedException("Membership does not belong to organization " + org.getName());
        }

        // Validate role change
        if (req.getRole() != null) {
            String newRole = req.getRole().trim().toUpperCase();
            if ("ADMIN".equalsIgnoreCase(newRole)) {
                throw new IllegalArgumentException(
                        "Cannot grant global application administrator privileges through organization membership.");
            }
            if (!ALLOWED_ORG_ROLES.contains(newRole)) {
                throw new IllegalArgumentException("Invalid organization role: " + newRole);
            }

            // Protect last active ORGANIZATION_OWNER
            if ("ORGANIZATION_OWNER".equals(membership.getRole()) && !"ORGANIZATION_OWNER".equals(newRole)) {
                long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_OWNER", "ACTIVE");
                if (activeOwnerCount <= 1) {
                    throw new IllegalStateException("Cannot demote the last active organization owner.");
                }
            }

            // Protect last active ORGANIZATION_ADMIN (if no owners exist)
            if ("ORGANIZATION_ADMIN".equals(membership.getRole()) && !"ORGANIZATION_ADMIN".equals(newRole)
                    && !"ORGANIZATION_OWNER".equals(newRole)) {
                long activeAdminCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_ADMIN", "ACTIVE");
                long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_OWNER", "ACTIVE");
                if (activeAdminCount <= 1 && activeOwnerCount == 0) {
                    throw new IllegalStateException("Cannot demote the last active organization administrator.");
                }
            }
            membership.setRole(newRole);
        }

        // Validate status change
        if (req.getStatus() != null) {
            String newStatus = req.getStatus().trim().toUpperCase();
            if (!"ACTIVE".equals(newStatus) && !"SUSPENDED".equals(newStatus)) {
                throw new IllegalArgumentException("Invalid membership status: " + newStatus);
            }

            if ("ORGANIZATION_OWNER".equals(membership.getRole()) && "SUSPENDED".equals(newStatus)) {
                long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_OWNER", "ACTIVE");
                if (activeOwnerCount <= 1) {
                    throw new IllegalStateException("Cannot suspend the last active organization owner.");
                }
            }

            if ("ORGANIZATION_ADMIN".equals(membership.getRole()) && "SUSPENDED".equals(newStatus)) {
                long activeAdminCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_ADMIN", "ACTIVE");
                long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                        "ORGANIZATION_OWNER", "ACTIVE");
                if (activeAdminCount <= 1 && activeOwnerCount == 0) {
                    throw new IllegalStateException("Cannot suspend the last active organization administrator.");
                }
            }
            membership.setStatus(newStatus);
        }

        return mapToMemberDto(membershipRepository.save(membership));
    }

    @Transactional
    public void deleteMember(Long orgId, Long membershipId, String username) {
        User actor = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        checkAdminPrivileges(actor, org);

        OrganizationMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership record not found: " + membershipId));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new TenantAccessDeniedException("Membership does not belong to organization " + org.getName());
        }

        if ("ORGANIZATION_OWNER".equals(membership.getRole()) && "ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                    "ORGANIZATION_OWNER", "ACTIVE");
            if (activeOwnerCount <= 1) {
                throw new IllegalStateException("Cannot remove the last active organization owner.");
            }
        }

        if ("ORGANIZATION_ADMIN".equals(membership.getRole()) && "ACTIVE".equalsIgnoreCase(membership.getStatus())) {
            long activeAdminCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                    "ORGANIZATION_ADMIN", "ACTIVE");
            long activeOwnerCount = membershipRepository.countByOrganizationIdAndRoleAndStatus(orgId,
                    "ORGANIZATION_OWNER", "ACTIVE");
            if (activeAdminCount <= 1 && activeOwnerCount == 0) {
                throw new IllegalStateException("Cannot remove the last active organization administrator.");
            }
        }

        membershipRepository.delete(membership);
    }

    private String checkMembershipOrAdmin(User user, Organization org) {
        if (user.getRole() == Role.ADMIN) {
            return "GLOBAL_ADMIN";
        }
        return membershipRepository.findByUserIdAndOrganizationId(user.getId(), org.getId())
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .map(OrganizationMembership::getRole)
                .orElseThrow(() -> new TenantAccessDeniedException(
                        "Access denied: You are not an active member of organization " + org.getName()));
    }

    private void checkAdminPrivileges(User user, Organization org) {
        if (user.getRole() == Role.ADMIN) {
            return; // Global admin permitted
        }
        OrganizationMembership mem = membershipRepository.findByUserIdAndOrganizationId(user.getId(), org.getId())
                .orElseThrow(() -> new TenantAccessDeniedException(
                        "Access denied: You are not a member of organization " + org.getName()));

        boolean isAuthorized = ("ORGANIZATION_OWNER".equalsIgnoreCase(mem.getRole())
                || "ORGANIZATION_ADMIN".equalsIgnoreCase(mem.getRole()))
                && "ACTIVE".equalsIgnoreCase(mem.getStatus());

        if (!isAuthorized) {
            throw new TenantAccessDeniedException(
                    "Access denied: Organization administrator or owner privileges required.");
        }
    }

    private Role mapOrgRoleToUserRole(String orgRole) {
        if ("PROJECT_MANAGER".equalsIgnoreCase(orgRole) || "ORGANIZATION_OWNER".equalsIgnoreCase(orgRole)
                || "ORGANIZATION_ADMIN".equalsIgnoreCase(orgRole)) {
            return Role.PROJECT_MANAGER;
        } else if ("CLIENT".equalsIgnoreCase(orgRole)) {
            return Role.CLIENT;
        } else if ("VENDOR".equalsIgnoreCase(orgRole)) {
            return Role.VENDOR;
        }
        return Role.WORKER;
    }

    private OrganizationDto mapToDto(Organization org, String userRole) {
        long memberCount = membershipRepository.findByOrganizationId(org.getId()).size();
        long projectCount = projectRepository.countByOrganizationId(org.getId());
        return OrganizationDto.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .orgCode(org.getOrgCode())
                .status(org.getStatus())
                .memberCount(memberCount)
                .projectCount(projectCount)
                .currentUserRole(userRole)
                .createdAt(
                        org.getCreatedAt() != null ? org.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : null)
                .build();
    }

    private OrganizationMemberDto mapToMemberDto(OrganizationMembership m) {
        return OrganizationMemberDto.builder()
                .id(m.getId())
                .userId(m.getUser().getId())
                .username(m.getUser().getUsername())
                .fullName(m.getUser().getFullName())
                .email(m.getUser().getEmail())
                .role(m.getRole())
                .status(m.getStatus())
                .employeeId(m.getEmployeeId())
                .invitationStatus(m.getInvitationStatus())
                .invitationExpiresAt(m.getInvitationExpiresAt() != null
                        ? m.getInvitationExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null)
                .activatedAt(
                        m.getActivatedAt() != null ? m.getActivatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                : null)
                .activationLink(m.getInvitationToken() != null ? "/activate?token=" + m.getInvitationToken() : null)
                .joinedAt(m.getCreatedAt() != null ? m.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : null)
                .build();
    }
}
