package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 18: Organization-Managed Authentication & Employee Account Provisioning Tests")
public class Phase18OrganizationAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationService organizationService;

    private Organization acmeOrg;
    private String adminJwt;

    @BeforeEach
    void setUp() throws Exception {
        acmeOrg = organizationRepository.findBySlug("acme-global")
                .orElseGet(() -> {
                    Organization org = new Organization();
                    org.setName("Acme Global Solutions");
                    org.setSlug("acme-global");
                    org.setOrgCode("ACM");
                    org.setStatus("ACTIVE");
                    org.setEmployeeIdCounter(5);
                    return organizationRepository.save(org);
                });

        // Ensure canonical employee IDs for seed users in Acme
        userRepository.findByUsername("admin").ifPresent(admin -> {
            membershipRepository.findByUserIdAndOrganizationId(admin.getId(), acmeOrg.getId()).ifPresent(m -> {
                m.setEmployeeId("ACM-EMP-0001");
                m.setInvitationStatus("ACCEPTED");
                membershipRepository.save(m);
            });
        });

        // Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        adminJwt = resp.getToken();
    }

    @Test
    @DisplayName("1. User can authenticate using Work Email")
    void testLoginByEmail() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String adminEmail = admin.getEmail();

        AuthRequest req = new AuthRequest();
        req.setUsername(adminEmail);
        req.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUsername()).isEqualTo("admin");
        assertThat(resp.getOrganizationId()).isNotNull();
    }

    @Test
    @DisplayName("2. User can authenticate using Employee ID")
    void testLoginByEmployeeId() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername("ACM-EMP-0001");
        req.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUsername()).isEqualTo("admin");
        assertThat(resp.getEmployeeId()).isEqualTo("ACM-EMP-0001");
    }

    @Test
    @DisplayName("3. Controlled Organization Onboarding & Owner Creation")
    void testOrganizationOnboarding() throws Exception {
        String testSlug = "nexus-logistics-" + System.currentTimeMillis();
        String testCode = "NEX";
        String ownerEmail = "owner_" + System.currentTimeMillis() + "@nexus.com";
        String ownerUsername = "nexus_owner_" + System.currentTimeMillis();

        RegisterOrganizationRequest req = RegisterOrganizationRequest.builder()
                .organizationName("Nexus Logistics Global")
                .organizationSlug(testSlug)
                .organizationCode(testCode)
                .ownerEmail(ownerEmail)
                .ownerUsername(ownerUsername)
                .ownerPassword("SecurePass123!")
                .ownerFullName("Nexus CEO")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getUserRole()).isEqualTo("ORGANIZATION_OWNER");
        assertThat(resp.getOrganizationSlug()).isEqualTo(testSlug);
        assertThat(resp.getEmployeeId()).startsWith("NEX-EMP-");

        // Verify database state: owner does not have global ADMIN role
        User ownerUser = userRepository.findByUsername(ownerUsername).orElseThrow();
        assertThat(ownerUser.getRole()).isNotEqualTo(Role.ADMIN);
        assertThat(ownerUser.getAccountStatus()).isEqualTo("ACTIVE");

        Organization org = organizationRepository.findBySlug(testSlug).orElseThrow();
        assertThat(org.getOrgCode()).startsWith("NEX");
    }

    @Test
    @DisplayName("4. Employee Account Provisioning via Secure Invitation")
    void testInviteMemberAndAccountActivation() throws Exception {
        String inviteEmail = "engineer_" + System.currentTimeMillis() + "@acme.com";

        InviteMemberRequest inviteReq = InviteMemberRequest.builder()
                .email(inviteEmail)
                .fullName("Senior Engineer Bob")
                .role("WORKER")
                .build();

        // Admin invites employee
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/" + acmeOrg.getId() + "/members/invite")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andReturn();

        OrganizationMemberDto memberDto = objectMapper.readValue(inviteResult.getResponse().getContentAsString(), OrganizationMemberDto.class);
        assertThat(memberDto.getEmail()).isEqualTo(inviteEmail);
        assertThat(memberDto.getRole()).isEqualTo("WORKER");
        assertThat(memberDto.getInvitationStatus()).isEqualTo("PENDING");
        assertThat(memberDto.getEmployeeId()).startsWith("ACM-EMP-");
        assertThat(memberDto.getActivationLink()).isNotBlank();

        // Verify account cannot be logged into before activation
        AuthRequest prematurelyLogin = new AuthRequest();
        prematurelyLogin.setUsername(inviteEmail);
        prematurelyLogin.setPassword("somePassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prematurelyLogin)))
                .andExpect(status().isForbidden());

        // Extract invitation token
        String token = memberDto.getActivationLink().replace("/activate?token=", "");

        // Verify invitation token endpoint
        mockMvc.perform(get("/api/auth/activate/verify?token=" + token))
                .andExpect(status().isOk());

        // Employee activates account and sets password
        ActivateAccountRequest actReq = ActivateAccountRequest.builder()
                .token(token)
                .password("EmployeePass2026!")
                .fullName("Bob Engineer")
                .build();

        MvcResult actResult = mockMvc.perform(post("/api/auth/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse actAuth = objectMapper.readValue(actResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(actAuth.getToken()).isNotBlank();
        assertThat(actAuth.getEmail()).isEqualTo(inviteEmail);

        // Employee can now sign in using their assigned Employee ID
        AuthRequest empLogin = new AuthRequest();
        empLogin.setUsername(memberDto.getEmployeeId());
        empLogin.setPassword("EmployeePass2026!");

        MvcResult empLogResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empLogin)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse empResp = objectMapper.readValue(empLogResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(empResp.getEmployeeId()).isEqualTo(memberDto.getEmployeeId());
    }

    @Test
    @DisplayName("5. Concurrency: Parallel Employee ID Generation guarantees uniqueness")
    void testConcurrentEmployeeIdGeneration() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> organizationService.generateNextEmployeeId(acmeOrg)));
        }

        List<String> generatedIds = new ArrayList<>();
        for (Future<String> f : futures) {
            generatedIds.add(f.get(5, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Assert all generated IDs are distinct
        long uniqueCount = generatedIds.stream().distinct().count();
        assertThat(uniqueCount).isEqualTo(threads);
    }

    @Test
    @DisplayName("6. Password Recovery: Request, One-Use Token, and Confirmation")
    void testPasswordRecoveryWorkflow() throws Exception {
        // Create an active test user
        String email = "recovery_" + System.currentTimeMillis() + "@acme.com";
        User user = new User();
        user.setUsername("recover_user_" + System.currentTimeMillis());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("OldPassword123!"));
        user.setRole(Role.WORKER);
        user.setAccountStatus("ACTIVE");
        User savedUser = userRepository.save(user);

        OrganizationMembership mem = new OrganizationMembership();
        mem.setUser(savedUser);
        mem.setOrganization(acmeOrg);
        mem.setRole("WORKER");
        mem.setStatus("ACTIVE");
        mem.setInvitationStatus("ACCEPTED");
        membershipRepository.save(mem);

        // Request reset
        PasswordResetRequest req = PasswordResetRequest.builder().identifier(email).build();
        MvcResult reqResult = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> respMap = objectMapper.readValue(reqResult.getResponse().getContentAsString(), Map.class);
        String token = (String) respMap.get("demoResetToken");
        assertThat(token).isNotBlank();

        // Confirm reset
        PasswordResetConfirmRequest confReq = PasswordResetConfirmRequest.builder()
                .token(token)
                .newPassword("BrandNewSecret2026!")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confReq)))
                .andExpect(status().isOk());

        // Token cannot be reused
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confReq)))
                .andExpect(status().isBadRequest());

        // User can authenticate with new password
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername(email);
        loginReq.setPassword("BrandNewSecret2026!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7. Role Security: Cannot grant global ADMIN from organization membership")
    void testCannotGrantGlobalAdmin() throws Exception {
        AddMemberRequest req = new AddMemberRequest();
        req.setUsername("worker");
        req.setRole("ADMIN");

        mockMvc.perform(post("/api/organizations/" + acmeOrg.getId() + "/members")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("8. Search & Filter Organization Members")
    void testFilterOrganizationMembers() throws Exception {
        mockMvc.perform(get("/api/organizations/" + acmeOrg.getId() + "/members?role=WORKER")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }
}
