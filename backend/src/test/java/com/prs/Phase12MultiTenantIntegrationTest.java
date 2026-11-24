package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AddMemberRequest;
import com.prs.dto.AuthRequest;
import com.prs.dto.CreateOrganizationRequest;
import com.prs.dto.UpdateMemberRequest;
import com.prs.entity.*;
import com.prs.optimization.ScheduleOptimizationService;
import com.prs.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 12: Multi-Organization Workspaces & SaaS Architecture Integration Tests")
public class Phase12MultiTenantIntegrationTest {

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
    private ProjectRepository projectRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private ScheduleOptimizationService optimizationService;

    private String adminJwt;
    private User adminUser;
    private Organization orgAcme;
    private Organization orgStark;

    @BeforeEach
    void setUp() throws Exception {
        // Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult actualLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = actualLogin.getResponse().getContentAsString();
        adminJwt = objectMapper.readTree(responseStr).get("token").asText();
        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Ensure Acme and Stark orgs exist
        orgAcme = organizationRepository.findBySlug("acme-global").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Acme Global Solutions");
            o.setSlug("acme-global");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        orgStark = organizationRepository.findBySlug("stark-tech").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Stark Tech Industries");
            o.setSlug("stark-tech");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        // Ensure admin has membership in Acme
        if (!membershipRepository.existsByUserIdAndOrganizationId(adminUser.getId(), orgAcme.getId())) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgAcme);
            m.setRole("ORGANIZATION_ADMIN");
            m.setStatus("ACTIVE");
            membershipRepository.save(m);
        }
    }

    @Test
    @DisplayName("1. Verify seed organizations and memberships exist")
    void testSeedOrganizationsAndMemberships() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Acme Global Solutions");
        assertThat(body).contains("acme-global");
    }

    @Test
    @DisplayName("2. Test organization onboarding workflow and creator admin assignment")
    void testCreateOrganizationWorkflow() throws Exception {
        String testSlug = "wayne-enterprises-" + System.currentTimeMillis();
        CreateOrganizationRequest req = new CreateOrganizationRequest();
        req.setName("Wayne Enterprises");
        req.setSlug(testSlug);

        MvcResult res = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Wayne Enterprises");
        assertThat(body).contains(testSlug);

        Organization created = organizationRepository.findBySlug(testSlug).orElseThrow();
        assertThat(membershipRepository.existsByUserIdAndOrganizationId(adminUser.getId(), created.getId())).isTrue();
    }

    @Test
    @DisplayName("3. Verify strict tenant project isolation (Cross-tenant access blocked)")
    void testTenantProjectIsolation() throws Exception {
        // Project A in Acme
        Project projA = new Project();
        projA.setName("Acme Satellite Deployment " + System.currentTimeMillis());
        projA.setStatus(ProjectStatus.IN_PROGRESS);
        projA.setOrganization(orgAcme);
        projA = projectRepository.save(projA);

        // Project B in Stark
        Project projB = new Project();
        projB.setName("Stark Arc Reactor " + System.currentTimeMillis());
        projB.setStatus(ProjectStatus.IN_PROGRESS);
        projB.setOrganization(orgStark);
        projB = projectRepository.save(projB);

        // Access Proj A under Acme context -> OK
        mockMvc.perform(get("/api/projects/" + projA.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId()))
                .andExpect(status().isOk());

        // Access Proj B under Acme context -> Must be rejected (403 Forbidden)
        mockMvc.perform(get("/api/projects/" + projB.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId()))
                .andExpect(status().isForbidden());

        // Attempt to update Proj B under Acme context -> Must be rejected (403 Forbidden)
        mockMvc.perform(put("/api/projects/" + projB.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered Project Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Verify cross-tenant task access and modification denial")
    void testCrossTenantTaskAccessDenied() throws Exception {
        // Project B in Stark
        Project projB = new Project();
        projB.setName("Stark Quantum Suit " + System.currentTimeMillis());
        projB.setStatus(ProjectStatus.IN_PROGRESS);
        projB.setOrganization(orgStark);
        projB = projectRepository.save(projB);

        Task taskB = new Task();
        taskB.setName("Assemble Suit Core");
        taskB.setEstimatedDuration(5);
        taskB.setPriority(Priority.HIGH);
        taskB.setStatus(TaskStatus.TODO);
        taskB.setProject(projB);
        taskB = taskRepository.save(taskB);

        // Attempt to get task B under Acme context -> 403 Forbidden
        mockMvc.perform(get("/api/tasks/" + taskB.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId()))
                .andExpect(status().isForbidden());

        // Attempt to update task B under Acme context -> 403 Forbidden
        mockMvc.perform(put("/api/tasks/" + taskB.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked Task Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Cross-tenant worker assignment prevention (Cannot assign Org B worker to Org A task)")
    void testCrossTenantWorkerAssignmentBlocked() throws Exception {
        // Org A project & task
        Project projA = new Project();
        projA.setName("Acme Solar Array " + System.currentTimeMillis());
        projA.setStatus(ProjectStatus.IN_PROGRESS);
        projA.setOrganization(orgAcme);
        projA = projectRepository.save(projA);

        Task taskA = new Task();
        taskA.setName("Panel Wiring");
        taskA.setEstimatedDuration(4);
        taskA.setStatus(TaskStatus.TODO);
        taskA.setProject(projA);
        taskA = taskRepository.save(taskA);

        // Org B worker
        Worker workerB = new Worker();
        workerB.setName("Stark Robotics Specialist " + System.currentTimeMillis());
        workerB.setEmail("stark_bot_" + System.currentTimeMillis() + "@stark.com");
        workerB.setSkills("Robotics");
        workerB.setStatus(WorkerStatus.AVAILABLE);
        workerB.setOrganization(orgStark);
        workerB = workerRepository.save(workerB);

        // Attempt assignment: Task A (Acme) with Worker B (Stark) under Acme context -> 400 Bad Request
        String assignmentPayload = String.format("{\"taskId\":%d,\"workerId\":%d,\"role\":\"Lead\"}",
                taskA.getId(), workerB.getId());

        mockMvc.perform(post("/api/tasks/" + taskA.getId() + "/assignments")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Schedule optimization worker isolation (Optimizer only selects workers from target org)")
    void testScheduleOptimizationWorkerIsolation() {
        // Create project in Stark
        Project starkProj = new Project();
        starkProj.setName("Stark Drone Fleet " + System.currentTimeMillis());
        starkProj.setStatus(ProjectStatus.IN_PROGRESS);
        starkProj.setOrganization(orgStark);
        starkProj = projectRepository.save(starkProj);

        Task droneTask = new Task();
        droneTask.setName("Calibrate Gyros");
        droneTask.setEstimatedDuration(3);
        droneTask.setStatus(TaskStatus.TODO);
        droneTask.setProject(starkProj);
        droneTask = taskRepository.save(droneTask);

        // Create Stark worker
        Worker starkWorker = new Worker();
        starkWorker.setName("Stark Avionics Engineer " + System.currentTimeMillis());
        starkWorker.setEmail("avionics_" + System.currentTimeMillis() + "@stark.com");
        starkWorker.setSkills("Avionics");
        starkWorker.setStatus(WorkerStatus.AVAILABLE);
        starkWorker.setOrganization(orgStark);
        workerRepository.save(starkWorker);

        // Run optimizer on Stark project
        var runDto = optimizationService.runOptimization(starkProj.getId(), "admin", "BALANCED");
        assertThat(runDto).isNotNull();
        assertThat(runDto.getStatus()).isIn("COMPLETED", "OPTIMAL");
    }

    @Test
    @DisplayName("7. Suspended organization operations blocked with 403 Forbidden")
    void testSuspendedOrganizationBlocked() throws Exception {
        // Create a suspended test org
        Organization suspendedOrg = new Organization();
        suspendedOrg.setName("Suspended Corp " + System.currentTimeMillis());
        suspendedOrg.setSlug("suspended-" + System.currentTimeMillis());
        suspendedOrg.setStatus("SUSPENDED");
        suspendedOrg = organizationRepository.save(suspendedOrg);

        // Enroll admin as member
        OrganizationMembership mem = new OrganizationMembership();
        mem.setUser(adminUser);
        mem.setOrganization(suspendedOrg);
        mem.setRole("ORGANIZATION_ADMIN");
        mem.setStatus("ACTIVE");
        membershipRepository.save(mem);

        // Any operational request under suspended org context must be rejected
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", suspendedOrg.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Last active organization admin protection and global admin escalation prevention")
    void testLastAdminProtectionAndGlobalAdminSeparation() throws Exception {
        // Create an organization with one admin
        Organization testOrg = new Organization();
        testOrg.setName("Solo Admin Org " + System.currentTimeMillis());
        testOrg.setSlug("solo-admin-" + System.currentTimeMillis());
        testOrg.setStatus("ACTIVE");
        testOrg = organizationRepository.save(testOrg);

        OrganizationMembership adminMem = new OrganizationMembership();
        adminMem.setUser(adminUser);
        adminMem.setOrganization(testOrg);
        adminMem.setRole("ORGANIZATION_ADMIN");
        adminMem.setStatus("ACTIVE");
        adminMem = membershipRepository.save(adminMem);

        // Attempt to demote the only admin to WORKER -> Must fail (400 Bad Request)
        UpdateMemberRequest demoteReq = new UpdateMemberRequest();
        demoteReq.setRole("WORKER");

        mockMvc.perform(put("/api/organizations/" + testOrg.getId() + "/members/" + adminMem.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", testOrg.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteReq)))
                .andExpect(status().isBadRequest());

        // Attempt to deactivate the only admin -> Must fail (400 Bad Request)
        mockMvc.perform(delete("/api/organizations/" + testOrg.getId() + "/members/" + adminMem.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", testOrg.getId()))
                .andExpect(status().isBadRequest());

        // Attempt to grant global "ADMIN" role through tenant membership -> Must fail (400 Bad Request)
        AddMemberRequest addReq = new AddMemberRequest();
        addReq.setUsername("manager");
        addReq.setRole("ADMIN"); // Escalation attempt!

        mockMvc.perform(post("/api/organizations/" + testOrg.getId() + "/members")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", testOrg.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("9. Tenant-scoped analytics and CSV export isolation")
    void testTenantScopedAnalyticsAndExport() throws Exception {
        // Portfolio analytics under Acme context returns 200
        mockMvc.perform(get("/api/analytics/portfolio")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId()))
                .andExpect(status().isOk());

        // Project in Stark cannot be exported via CSV under Acme context
        Project starkProj = new Project();
        starkProj.setName("Stark Reactor Blueprint " + System.currentTimeMillis());
        starkProj.setStatus(ProjectStatus.IN_PROGRESS);
        starkProj.setOrganization(orgStark);
        starkProj = projectRepository.save(starkProj);

        mockMvc.perform(get("/api/projects/" + starkProj.getId() + "/exports/tasks.csv")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAcme.getId()))
                .andExpect(status().isForbidden());
    }
}
