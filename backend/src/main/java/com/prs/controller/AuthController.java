package com.prs.controller;

import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.security.JwtUtil;
import com.prs.security.PasswordPolicyValidator;
import com.prs.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor

public class AuthController {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final WorkerRepository workerRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailService emailService;

    @Value("${app.auth.dev-mode:true}")
    private boolean devMode;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String hashToken(String rawToken) {
        if (rawToken == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthRequest authRequest) {
        try {
            String identifier = authRequest.getUsername();
            if (identifier == null || identifier.isBlank()) {
                if (authRequest.getEmail() != null && !authRequest.getEmail().isBlank()) {
                    identifier = authRequest.getEmail().trim();
                } else if (authRequest.getEmployeeId() != null && !authRequest.getEmployeeId().isBlank()) {
                    identifier = authRequest.getEmployeeId().trim();
                }
            }

            if (identifier == null || identifier.isBlank() || authRequest.getPassword() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication Failed: Invalid credentials or account is suspended."));
            }

            identifier = identifier.trim();

            // 1. Resolve User entity by identifier (username, work email, or employee ID)
            User user = null;
            Organization targetedOrg = null;

            if (authRequest.getOrganizationCode() != null && !authRequest.getOrganizationCode().isBlank()) {
                targetedOrg = organizationRepository.findByOrgCodeIgnoreCase(authRequest.getOrganizationCode().trim())
                        .or(() -> organizationRepository.findBySlugIgnoreCase(authRequest.getOrganizationCode().trim()))
                        .orElse(null);
            }

            // Check if identifier is an employee ID format: e.g. SYN-EMP-0001
            if (identifier.toUpperCase().contains("-EMP-")) {
                String empId = identifier.toUpperCase();
                if (targetedOrg != null) {
                    Optional<OrganizationMembership> memOpt = membershipRepository.findByOrganizationIdAndEmployeeId(targetedOrg.getId(), empId);
                    if (memOpt.isPresent()) {
                        user = memOpt.get().getUser();
                    }
                } else {
                    Optional<OrganizationMembership> memOpt = membershipRepository.findByEmployeeId(empId);
                    if (memOpt.isPresent()) {
                        user = memOpt.get().getUser();
                    }
                }
            }

            // Check if identifier is an email address
            if (user == null && identifier.contains("@")) {
                user = userRepository.findByEmailIgnoreCase(identifier).orElse(null);
            }

            // Fallback: search by username
            if (user == null) {
                user = userRepository.findByUsernameIgnoreCase(identifier).orElse(null);
            }

            // If user still not found, return generic error (avoid account enumeration)
            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication Failed: Invalid credentials or account is suspended."));
            }

            // 2. Check temporary lockout from repeated brute-force attempts
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication Failed: Account is temporarily locked due to repeated failed login attempts. Please try again later."));
            }

            // 3. Check account activation & status
            if ("PENDING_ACTIVATION".equalsIgnoreCase(user.getAccountStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Account activation is required. Please check your invitation email or link to complete setup."));
            }

            if ("SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Account is suspended. Please contact your organization administrator."));
            }

            // 4. Verify password
            boolean passwordMatches = passwordEncoder.matches(authRequest.getPassword(), user.getPassword());
            if (!passwordMatches) {
                // Record failed attempt
                int failed = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
                user.setFailedLoginAttempts(failed);
                if (failed >= 5) {
                    user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                }
                userRepository.save(user);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication Failed: Invalid credentials or account is suspended."));
            }

            // Successful authentication -> reset lockouts
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);

            // 5. Resolve Organization context
            List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
            OrganizationMembership primaryMem = null;

            if (targetedOrg != null) {
                Long targetOrgId = targetedOrg.getId();
                primaryMem = memberships.stream()
                        .filter(m -> m.getOrganization().getId().equals(targetOrgId))
                        .findFirst()
                        .orElse(null);

                if (primaryMem == null && user.getRole() != Role.ADMIN) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have active membership in organization workspace: " + targetedOrg.getName()));
                }
            }

            if (primaryMem == null) {
                primaryMem = memberships.stream()
                        .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                        .findFirst()
                        .orElse(null);
            }

            if (primaryMem == null && user.getRole() != Role.ADMIN) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have an active organization membership. Please contact your organization administrator."));
            }

            if (primaryMem != null && "SUSPENDED".equalsIgnoreCase(primaryMem.getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Your membership in " + primaryMem.getOrganization().getName() + " is suspended."));
            }

            // 6. Generate JWT and issue response
            final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            final String jwt = jwtUtil.generateToken(userDetails);

            List<UserMembershipSummaryDto> membershipSummaries = memberships.stream().map(m ->
                    UserMembershipSummaryDto.builder()
                            .organizationId(m.getOrganization().getId())
                            .organizationName(m.getOrganization().getName())
                            .organizationSlug(m.getOrganization().getSlug())
                            .organizationCode(m.getOrganization().getOrgCode())
                            .role(m.getRole())
                            .status(m.getStatus())
                            .employeeId(m.getEmployeeId())
                            .build()
            ).collect(Collectors.toList());

            String userRole = primaryMem != null ? primaryMem.getRole() : user.getRole().name();

            AuthResponse response = AuthResponse.builder()
                    .token(jwt)
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .userRole(userRole)
                    .organizationId(primaryMem != null ? primaryMem.getOrganization().getId() : null)
                    .organizationName(primaryMem != null ? primaryMem.getOrganization().getName() : null)
                    .organizationSlug(primaryMem != null ? primaryMem.getOrganization().getSlug() : null)
                    .organizationCode(primaryMem != null ? primaryMem.getOrganization().getOrgCode() : null)
                    .employeeId(primaryMem != null ? primaryMem.getEmployeeId() : null)
                    .memberships(membershipSummaries)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication Failed: " + e.getMessage()));
        }
    }

    @PostMapping("/register-organization")
    @Transactional
    public ResponseEntity<?> registerOrganization(@RequestBody RegisterOrganizationRequest req) {
        try {
            if (req.getOrganizationName() == null || req.getOrganizationName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Organization name is required."));
            }
            if (req.getOwnerEmail() == null || !req.getOwnerEmail().contains("@")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Valid owner email is required."));
            }
            if (req.getOwnerUsername() == null || req.getOwnerUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Owner username is required."));
            }

            // Enforce unified password policy (12-128 chars, confirm match, not common)
            passwordPolicyValidator.validatePassword(req.getOwnerPassword(), req.getOwnerConfirmPassword(), req.getOwnerUsername().trim());

            // Slug generation and case-insensitive uniqueness
            String slug = req.getOrganizationSlug();
            if (slug == null || slug.trim().isEmpty()) {
                slug = req.getOrganizationName().trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            } else {
                slug = slug.trim().toLowerCase();
            }

            if (organizationRepository.existsBySlugIgnoreCase(slug)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Organization slug already exists: " + slug));
            }

            // Org Code generation and case-insensitive uniqueness
            String orgCode = req.getOrganizationCode();
            if (orgCode == null || orgCode.trim().isEmpty()) {
                String clean = req.getOrganizationName().replaceAll("[^a-zA-Z]", "").toUpperCase();
                orgCode = clean.length() >= 3 ? clean.substring(0, 3) : "SYN";
            } else {
                orgCode = orgCode.trim().toUpperCase();
            }

            if (organizationRepository.existsByOrgCodeIgnoreCase(orgCode)) {
                int c = 1;
                while (organizationRepository.existsByOrgCodeIgnoreCase(orgCode + c)) {
                    c++;
                }
                orgCode = orgCode + c;
            }

            // Check user email & username case-insensitive uniqueness globally
            String email = req.getOwnerEmail().trim().toLowerCase();
            String username = req.getOwnerUsername().trim();

            if (userRepository.existsByEmailIgnoreCase(email)) {
                return ResponseEntity.badRequest().body(Map.of("message", "An account with email '" + email + "' already exists."));
            }
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username '" + username + "' is already taken."));
            }

            // 1. Create Organization
            Organization org = new Organization();
            org.setName(req.getOrganizationName().trim());
            org.setSlug(slug);
            org.setOrgCode(orgCode);
            org.setStatus("ACTIVE");
            org.setEmployeeIdCounter(1);
            Organization savedOrg = organizationRepository.save(org);

            // 2. Create Owner User (role is PROJECT_MANAGER, not global ADMIN!)
            User owner = new User();
            owner.setUsername(username);
            owner.setEmail(email);
            owner.setPassword(passwordEncoder.encode(req.getOwnerPassword()));
            owner.setRole(Role.PROJECT_MANAGER);
            owner.setAccountStatus("ACTIVE");
            User savedOwner = userRepository.save(owner);

            // 3. Create OrganizationMembership with ORGANIZATION_OWNER role
            String employeeId = String.format("%s-EMP-0001", orgCode);
            OrganizationMembership membership = new OrganizationMembership();
            membership.setUser(savedOwner);
            membership.setOrganization(savedOrg);
            membership.setRole("ORGANIZATION_OWNER");
            membership.setStatus("ACTIVE");
            membership.setInvitationStatus("ACCEPTED");
            membership.setEmployeeId(employeeId);
            membership.setActivatedAt(LocalDateTime.now());
            membershipRepository.save(membership);

            // 4. Optionally create a Worker record for the owner
            Worker w = new Worker();
            w.setUser(savedOwner);
            w.setOrganization(savedOrg);
            w.setName(req.getOwnerFullName() != null && !req.getOwnerFullName().isBlank() ? req.getOwnerFullName().trim() : username);
            w.setEmail(email);
            w.setStatus(WorkerStatus.AVAILABLE);
            workerRepository.save(w);

            // 5. Generate JWT and issue response
            final UserDetails userDetails = userDetailsService.loadUserByUsername(savedOwner.getUsername());
            final String jwt = jwtUtil.generateToken(userDetails);

            AuthResponse response = AuthResponse.builder()
                    .token(jwt)
                    .username(savedOwner.getUsername())
                    .email(savedOwner.getEmail())
                    .role(savedOwner.getRole().name())
                    .userRole("ORGANIZATION_OWNER")
                    .organizationId(savedOrg.getId())
                    .organizationName(savedOrg.getName())
                    .organizationSlug(savedOrg.getSlug())
                    .organizationCode(savedOrg.getOrgCode())
                    .employeeId(employeeId)
                    .memberships(List.of(
                            UserMembershipSummaryDto.builder()
                                    .organizationId(savedOrg.getId())
                                    .organizationName(savedOrg.getName())
                                    .organizationSlug(savedOrg.getSlug())
                                    .organizationCode(savedOrg.getOrgCode())
                                    .role("ORGANIZATION_OWNER")
                                    .status("ACTIVE")
                                    .employeeId(employeeId)
                                    .build()
                    ))
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Organization onboarding failed: " + e.getMessage()));
        }
    }

    @GetMapping("/activate/verify")
    public ResponseEntity<?> verifyActivationToken(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Token is required."));
        }

        Optional<OrganizationMembership> memOpt = membershipRepository.findByInvitationToken(token.trim());
        if (memOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Invalid or expired invitation token."));
        }

        OrganizationMembership mem = memOpt.get();
        if (!"PENDING".equalsIgnoreCase(mem.getInvitationStatus())) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "This invitation has already been " + mem.getInvitationStatus().toLowerCase() + "."));
        }

        if (mem.getInvitationExpiresAt() != null && mem.getInvitationExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "This invitation token has expired. Please request a new invitation."));
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "organizationName", mem.getOrganization().getName(),
                "organizationSlug", mem.getOrganization().getSlug(),
                "email", mem.getUser().getEmail(),
                "username", mem.getUser().getUsername(),
                "role", mem.getRole(),
                "employeeId", mem.getEmployeeId() != null ? mem.getEmployeeId() : ""
        ));
    }

    @PostMapping("/activate")
    @Transactional
    public ResponseEntity<?> activateAccount(@RequestBody ActivateAccountRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Activation token is required."));
        }

        Optional<OrganizationMembership> memOpt = membershipRepository.findByInvitationToken(req.getToken().trim());
        if (memOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid or expired activation token."));
        }

        OrganizationMembership mem = memOpt.get();
        if (!"PENDING".equalsIgnoreCase(mem.getInvitationStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This invitation has already been " + mem.getInvitationStatus().toLowerCase() + "."));
        }

        if (mem.getInvitationExpiresAt() != null && mem.getInvitationExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This invitation token has expired."));
        }

        User user = mem.getUser();

        // Enforce unified password policy
        try {
            passwordPolicyValidator.validatePassword(req.getPassword(), req.getConfirmPassword(), user.getUsername());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }

        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);

        mem.setInvitationStatus("ACCEPTED");
        mem.setInvitationToken(null);
        mem.setActivatedAt(LocalDateTime.now());
        membershipRepository.save(mem);

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            workerRepository.findByUserId(user.getId()).ifPresent(w -> {
                w.setName(req.getFullName().trim());
                workerRepository.save(w);
            });
        }

        // Issue JWT and login immediately
        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);

        AuthResponse response = AuthResponse.builder()
                .token(jwt)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .userRole(mem.getRole())
                .organizationId(mem.getOrganization().getId())
                .organizationName(mem.getOrganization().getName())
                .organizationSlug(mem.getOrganization().getSlug())
                .organizationCode(mem.getOrganization().getOrgCode())
                .employeeId(mem.getEmployeeId())
                .memberships(List.of(
                        UserMembershipSummaryDto.builder()
                                .organizationId(mem.getOrganization().getId())
                                .organizationName(mem.getOrganization().getName())
                                .organizationSlug(mem.getOrganization().getSlug())
                                .organizationCode(mem.getOrganization().getOrgCode())
                                .role(mem.getRole())
                                .status(mem.getStatus())
                                .employeeId(mem.getEmployeeId())
                                .build()
                ))
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/request")
    @Transactional
    public ResponseEntity<?> requestPasswordReset(@RequestBody PasswordResetRequest req) {
        String genericMsg = "If an account exists with that identifier, password reset instructions have been dispatched.";
        String rawToken = null;

        if (req.getIdentifier() != null && !req.getIdentifier().isBlank()) {
            String ident = req.getIdentifier().trim();
            User user = null;
            if (ident.contains("@")) {
                user = userRepository.findByEmailIgnoreCase(ident).orElse(null);
            } else {
                user = userRepository.findByUsernameIgnoreCase(ident).orElse(null);
            }

            if (user != null) {
                // Generate cryptographically secure 64-hex-character token
                byte[] randomBytes = new byte[32];
                SECURE_RANDOM.nextBytes(randomBytes);
                StringBuilder sb = new StringBuilder();
                for (byte b : randomBytes) {
                    sb.append(String.format("%02x", b));
                }
                rawToken = sb.toString();

                // Store SHA-256 hash in database
                String tokenHash = hashToken(rawToken);
                user.setPasswordResetToken(tokenHash);
                user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
                userRepository.save(user);

                // Dispatch via configured email service
                String resetLink = "/reset-password?token=" + rawToken;
                String emailBody = "Hello " + user.getUsername() + ",\n\n"
                        + "A request was received to reset the password for your SYNCORA account.\n\n"
                        + "Please use the following link to choose a new password:\n"
                        + resetLink + "\n\n"
                        + "This link will expire in 1 hour. If you did not request this change, you can safely ignore this email.";
                emailService.sendEmail(user.getEmail(), "SYNCORA — Password Reset Request", emailBody);
            }
        }

        // Return generic message to prevent account enumeration
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", genericMsg);

        // Include dev links only in development mode
        if (devMode && rawToken != null) {
            resp.put("demoResetToken", rawToken);
            resp.put("demoResetLink", "/reset-password?token=" + rawToken);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/password-reset/verify")
    public ResponseEntity<?> verifyPasswordResetToken(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Reset token is required."));
        }

        String rawToken = token.trim();
        String tokenHash = hashToken(rawToken);

        // Look up by token hash (or legacy raw token)
        Optional<User> userOpt = userRepository.findByPasswordResetToken(tokenHash)
                .or(() -> userRepository.findByPasswordResetToken(rawToken));

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "This password reset link is invalid, expired, or has already been used. Please request a new link."
            ));
        }

        User user = userOpt.get();
        if (user.getPasswordResetExpiresAt() != null && user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "valid", false,
                    "message", "This password reset token has expired. Please request a new password reset link."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : ""
        ));
    }

    @PostMapping("/password-reset/confirm")
    @Transactional
    public ResponseEntity<?> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Reset token is required."));
        }

        String rawToken = req.getToken().trim();
        String tokenHash = hashToken(rawToken);

        Optional<User> userOpt = userRepository.findByPasswordResetToken(tokenHash)
                .or(() -> userRepository.findByPasswordResetToken(rawToken));

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid, expired, or already used password reset token. Please request a new link."));
        }

        User user = userOpt.get();
        if (user.getPasswordResetExpiresAt() != null && user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This password reset token has expired. Please request a new one."));
        }

        // Validate password against unified policy
        try {
            passwordPolicyValidator.validatePassword(req.getNewPassword(), req.getConfirmPassword(), user.getUsername());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }

        // Securely update password and atomically invalidate reset token
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password has been reset successfully. You can now sign in with your new password."));
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }

        User user = userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authentication.getName()));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password does not match."));
        }

        // Validate password against unified policy
        try {
            passwordPolicyValidator.validatePassword(req.getNewPassword(), req.getConfirmPassword(), user.getUsername());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }
}
