package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.cpm.CpmTaskResult;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.integration.apikey.ApiKeyService;
import com.prs.integration.dto.ApiKeyResponse;
import com.prs.integration.dto.CreateApiKeyRequest;
import com.prs.integration.dto.WebhookSubscriptionRequest;
import com.prs.integration.dto.WebhookSubscriptionResponse;
import com.prs.integration.event.IntegrationEventPublisher;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.SchedulingEngine;
import com.prs.scheduler.WorkingDayCalculator;
import com.prs.security.JwtUtil;
import com.prs.security.UserDetailsServiceImpl;
import com.prs.service.DeliverableManagementService;
import com.prs.service.TaskService;
import com.prs.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 16: Final Integration, Stabilization & Release Candidate Tests")
public class Phase16ReleaseCandidateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private DeliverableManagementService deliverableService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private IntegrationEventPublisher eventPublisher;

    private String adminJwt;
    private Organization orgA;
    private Organization orgB;
    private User adminUser;

    private final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();

        // 1. Authenticate Admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult actualLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = actualLogin.getResponse().getContentAsString();
        Map<String, Object> respMap = objectMapper.readValue(responseStr, Map.class);
        adminJwt = (String) respMap.get("token");
        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // 2. Setup Organizations
        orgA = organizationRepository.findBySlug("rc-org-alpha").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Release Candidate Alpha Corp");
            o.setSlug("rc-org-alpha");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        orgB = organizationRepository.findBySlug("rc-org-beta").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Release Candidate Beta Corp");
            o.setSlug("rc-org-beta");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        // Ensure Admin is member of orgA and orgB
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgA.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgA);
            m.setRole("ADMIN");
            m.setStatus("ACTIVE");
            membershipRepository.save(m);
        }

        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgB.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgB);
            m.setRole("ADMIN");
            m.setStatus("ACTIVE");
            membershipRepository.save(m);
        }
    }

    @Test
    @DisplayName("RC-1: Full Project Lifecycle - Scheduling, Deliverable Gate & Task Completion Gate Validation")
    void testFullProjectLifecycle() throws Exception {
        TenantContext.setTenantId(orgA.getId());

        // 1. Create Client
        Client client = new Client();
        client.setName("Alpha Client Services");
        client.setEmail("contact@alphaclient.com");
        client.setOrganization(orgA);
        client = clientRepository.save(client);

        // 2. Create Project
        Project project = new Project();
        project.setName("RC Enterprise Modernization " + System.currentTimeMillis());
        project.setClient(client);
        project.setOrganization(orgA);
        project.setStartDate(MONDAY);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project = projectRepository.save(project);

        // 3. Create Tasks (Sequential: Task A -> Task B)
        Task taskA = new Task();
        taskA.setName("Architecture Design " + System.currentTimeMillis());
        taskA.setProject(project);
        taskA.setEstimatedDuration(3);
        taskA.setStatus(TaskStatus.IN_PROGRESS);
        taskA.setProgress(0);
        taskA.setPlannedStartDate(MONDAY);
        taskA.setPlannedFinishDate(MONDAY.plusDays(2));
        taskA = taskRepository.save(taskA);

        // 4. Attach Deliverable to Task A
        CreateDeliverableRequest delivReq = new CreateDeliverableRequest();
        delivReq.setName("Architecture Blueprint Spec");
        delivReq.setDeliverableType(DeliverableType.TECHNICAL_DOCUMENT);
        delivReq.setTaskId(taskA.getId());
        delivReq.setRequiredSubmissionDate(MONDAY.plusDays(2));

        DeliverableDto deliverableDto = deliverableService.createDeliverable(project.getId(), delivReq, "admin");
        assertThat(deliverableDto).isNotNull();
        assertThat(deliverableDto.getStatus()).isEqualTo(DeliverableStatus.PLANNED);

        // 5. Attach Mandatory Task Approval Gate
        ConfigureTaskGateRequest gateReq = new ConfigureTaskGateRequest();
        gateReq.setDeliverableId(deliverableDto.getId());
        gateReq.setGateType(GateType.COMPLETION_GATE);
        gateReq.setIsMandatory(true);

        TaskApprovalGateDto gateDto = deliverableService.configureTaskGate(taskA.getId(), gateReq, "admin");
        assertThat(gateDto).isNotNull();
        assertThat(gateDto.getIsSatisfied()).isFalse();

        // 6. Attempting to complete Task A before approval must fail due to gate
        final Long taskAId = taskA.getId();
        TaskDto updateAttempt = new TaskDto();
        updateAttempt.setName(taskA.getName());
        updateAttempt.setStatus(TaskStatus.COMPLETED);
        updateAttempt.setProgress(100);

        assertThatThrownBy(() -> taskService.updateTask(taskAId, updateAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be marked COMPLETED");

        // 7. Upload document, submit deliverable, and record APPROVED decision
        MockMultipartFile file = new MockMultipartFile("file", "arch_blueprint.pdf", "application/pdf", "architecture design binary".getBytes());
        deliverableService.uploadDocument(deliverableDto.getId(), file, "v1.0", "admin");
        deliverableService.submitDeliverable(deliverableDto.getId(), "admin");

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest();
        decision.setDecision(ApprovalDecision.APPROVED);
        decision.setComments("Architecture satisfies enterprise high-availability standards");
        deliverableService.recordApprovalDecision(deliverableDto.getId(), decision, "admin");

        // Verify gate is satisfied
        List<TaskApprovalGateDto> gates = deliverableService.getTaskGates(taskA.getId());
        assertThat(gates).anyMatch(g -> g.getId().equals(gateDto.getId()) && Boolean.TRUE.equals(g.getIsSatisfied()));

        // 8. Now completing Task A succeeds
        TaskDto completedTask = taskService.updateTask(taskA.getId(), updateAttempt);
        assertThat(completedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completedTask.getProgress()).isEqualTo(100);

        TenantContext.clear();
    }

    @Test
    @DisplayName("RC-2: Deterministic Scheduling Engine - Forward & Backward Pass, Float, and Calendar Weekend Exclusions")
    void testSchedulingEngineDeterministicCorrectness() {
        WorkingDayCalculator calc = new WorkingDayCalculator();
        SchedulingEngine engine = new SchedulingEngine(calc);
        CriticalPathService cpmService = new CriticalPathService(calc);

        Project project = new Project();
        project.setId(201L);
        project.setStartDate(MONDAY); // Mon Nov 2, 2026

        // Network:
        // A (3d) -> C (2d) -> E (3d)   Total = 3 + 2 + 3 = 8d
        // B (2d) -> D (2d) -> E (3d)   Total = 2 + 2 + 3 = 7d (Path B-D has 1 day float)
        Task a = createTask(1L, "Design A", 3);
        Task b = createTask(2L, "Design B", 2);
        Task c = createTask(3L, "Build C", 2);
        Task d = createTask(4L, "Build D", 2);
        Task e = createTask(5L, "Deploy E", 3);

        TaskDependency depA_C = createDep(a, c);
        TaskDependency depB_D = createDep(b, d);
        TaskDependency depC_E = createDep(c, e);
        TaskDependency depD_E = createDep(d, e);

        List<Task> tasks = new ArrayList<>(Arrays.asList(a, b, c, d, e));
        List<TaskDependency> deps = Arrays.asList(depA_C, depB_D, depC_E, depD_E);

        // Calculate schedule
        engine.calculateSchedule(project, tasks, deps);

        // Task A: Mon Nov 2 -> Wed Nov 4 (3 working days)
        assertEquals(LocalDate.of(2026, 11, 2), a.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 11, 4), a.getPlannedFinishDate());

        // Task C: Thu Nov 5 -> Fri Nov 6 (2 working days)
        assertEquals(LocalDate.of(2026, 11, 5), c.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 11, 6), c.getPlannedFinishDate());

        // Task E: Mon Nov 9 -> Wed Nov 11 (3 working days, skips weekend Nov 7-8)
        assertEquals(LocalDate.of(2026, 11, 9), e.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 11, 11), e.getPlannedFinishDate());

        // Run CPM Analysis
        CriticalPathResult cpm = cpmService.calculate(MONDAY, tasks, deps);
        assertNotNull(cpm);
        assertEquals(8, cpm.getMinimumDurationWorkingDays());

        // Critical Path must be A -> C -> E
        Set<Long> critIds = cpm.getCriticalTaskIds();
        assertTrue(critIds.contains(1L), "Task A must be critical");
        assertTrue(critIds.contains(3L), "Task C must be critical");
        assertTrue(critIds.contains(5L), "Task E must be critical");
        assertFalse(critIds.contains(2L), "Task B is not on critical path");
        assertFalse(critIds.contains(4L), "Task D is not on critical path");

        // Verify float for Task B: Path B-D finishes Fri Nov 6, while E starts Mon Nov 9 -> Total slack = 1 day
        CpmTaskResult resB = cpm.getTaskResults().stream()
                .filter(r -> r.getTaskId() == 2L)
                .findFirst()
                .orElseThrow();
        assertEquals(1, resB.getTotalSlack());
        assertFalse(resB.isCritical());
    }

    @Test
    @DisplayName("RC-3: Resource Conflict Detection - Concurrent Allocation & Capacity Overload")
    void testResourceConflictDetection() {
        ConflictDetector detector = new ConflictDetector();

        Worker worker = new Worker();
        worker.setId(501L);
        worker.setName("Dev Lead Dave");

        Task task1 = createTask(11L, "Backend API", 5);
        task1.setPlannedStartDate(MONDAY);
        task1.setPlannedFinishDate(LocalDate.of(2026, 11, 6));

        Task task2 = createTask(12L, "Database Schema", 3);
        task2.setPlannedStartDate(LocalDate.of(2026, 11, 4)); // Overlaps with task1
        task2.setPlannedFinishDate(LocalDate.of(2026, 11, 6));

        TaskAssignment a1 = new TaskAssignment();
        a1.setWorker(worker);
        a1.setTask(task1);

        TaskAssignment a2 = new TaskAssignment();
        a2.setWorker(worker);
        a2.setTask(task2);

        List<ResourceConflict> conflicts = detector.detectConflicts(
                List.of(task1, task2),
                List.of(a1, a2)
        );

        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.get(0).getResource()).isEqualTo("Dev Lead Dave");
        assertThat(conflicts.get(0).getOverlapStart()).isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    @DisplayName("RC-4: Multi-Tenant Strict Data Isolation - Inter-Organization Access Prevention")
    void testMultiTenantIsolation() throws Exception {
        // Create Project in Org B
        Project projectB = new Project();
        projectB.setName("Confidential Org B Roadmap " + System.currentTimeMillis());
        projectB.setOrganization(orgB);
        projectB.setStartDate(MONDAY);
        projectB.setStatus(ProjectStatus.IN_PROGRESS);
        projectB = projectRepository.save(projectB);

        Long orgBProjectId = projectB.getId();

        // 1. Accessing Proj B under Org A header context -> Must be rejected (403 Forbidden)
        mockMvc.perform(get("/api/projects/" + orgBProjectId)
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());

        // 2. Accessing Proj B under Org B header context -> 200 OK
        mockMvc.perform(get("/api/projects/" + orgBProjectId)
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orgBProjectId));
    }

    @Test
    @DisplayName("RC-5: External Interoperability - Webhook Subscription Dispatch & API Key Security")
    void testExternalInteroperabilityReleaseCandidate() throws Exception {
        // 1. Create Webhook Subscription for Org A via Controller
        WebhookSubscriptionRequest subReq = WebhookSubscriptionRequest.builder()
                .name("CI/CD Automation Pipeline")
                .destinationUrl("https://webhook.site/2984a861-1234-5678-abcd-000000000000")
                .subscribedEvents(List.of("task.updated", "deliverable.approved"))
                .isEnabled(true)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/integrations/webhooks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andReturn();

        WebhookSubscriptionResponse subDto = objectMapper.readValue(
                createRes.getResponse().getContentAsString(),
                WebhookSubscriptionResponse.class
        );
        assertThat(subDto).isNotNull();
        assertThat(subDto.getSecretMasked()).contains("***");
        assertThat(subDto.getIsEnabled()).isTrue();

        // 2. Verify Event Dispatch safely queues/processes without exception
        eventPublisher.publishEvent(
                "deliverable.approved",
                orgA.getId(),
                101L,
                Map.of("deliverableName", "Release Candidate Report", "status", "APPROVED")
        );

        // 3. Create Scoped API Key for Org A
        CreateApiKeyRequest keyReq = CreateApiKeyRequest.builder()
                .name("Release Candidate Pipeline Token")
                .scopes(List.of("projects:read", "tasks:read"))
                .expiresInDays(30)
                .build();

        MvcResult keyCreateRes = mockMvc.perform(post("/api/integrations/api-keys")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiKeyResponse keyResp = objectMapper.readValue(
                keyCreateRes.getResponse().getContentAsString(),
                ApiKeyResponse.class
        );
        assertThat(keyResp).isNotNull();
        assertThat(keyResp.getRawKey()).startsWith("prs_live_");
        assertThat(keyResp.getKeyPrefix()).isNotBlank();

        // Verify API Key exists in DB with SHA-256 hash (never raw plain text)
        ApiKey savedKey = apiKeyRepository.findById(keyResp.getId()).orElseThrow();
        assertThat(savedKey.getKeyHash()).isEqualTo(ApiKeyService.sha256Hex(keyResp.getRawKey()));
        assertThat(savedKey.getOrganization().getId()).isEqualTo(orgA.getId());

        // 4. Invoke API using Admin listing API Keys
        mockMvc.perform(get("/api/integrations/api-keys")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // Helper builders
    private Task createTask(Long id, String name, int duration) {
        Task t = new Task();
        t.setId(id);
        t.setName(name);
        t.setEstimatedDuration(duration);
        t.setStatus(TaskStatus.TODO);
        t.setProgress(0);
        return t;
    }

    private TaskDependency createDep(Task pred, Task succ) {
        TaskDependency d = new TaskDependency();
        d.setPredecessorTask(pred);
        d.setSuccessorTask(succ);
        d.setDependencyType(DependencyType.FINISH_TO_START);
        return d;
    }
}
