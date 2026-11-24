package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.DeliverableManagementService;
import com.prs.service.TaskService;
import com.prs.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 14: Deliverables, Document Management & Approval Workflows Integration Tests")
public class Phase14DeliverablesApprovalIntegrationTest {

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
    private TaskRepository taskRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private DeliverableDocumentRepository documentRepository;

    @Autowired
    private DeliverableApprovalRepository approvalRepository;

    @Autowired
    private TaskApprovalGateRepository taskGateRepository;

    @Autowired
    private DeliverableManagementService deliverableService;

    @Autowired
    private TaskService taskService;

    private String adminJwt;
    private User adminUser;
    private Organization orgAlpha;
    private Organization orgBeta;
    private Project projectAlpha;
    private Project projectBeta;
    private Task taskAlpha;
    private User testUserAlice;
    private User testReviewerBob;
    private User testClientCharlie;
    private Client clientAlpha;

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
        Map<String, Object> respMap = objectMapper.readValue(responseStr, Map.class);
        adminJwt = (String) respMap.get("token");
        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Set up test orgs
        orgAlpha = organizationRepository.findBySlug("p14-alpha-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Phase 14 Alpha Org");
            o.setSlug("p14-alpha-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        orgBeta = organizationRepository.findBySlug("p14-beta-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Phase 14 Beta Org");
            o.setSlug("p14-beta-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        // Set up users
        testUserAlice = userRepository.findByUsername("alice_dev").orElseGet(() -> {
            User u = new User();
            u.setUsername("alice_dev");
            u.setPassword("$2a$10$wK1.V/VbS2sV7Q3.11rYuehBfZP8U5P5rJqPcfGtz72cQyCbmf8Q2"); // bcrypt "password"
            u.setEmail("alice@alpha.com");
            u.setRole(Role.PROJECT_MANAGER);
            return userRepository.save(u);
        });

        testReviewerBob = userRepository.findByUsername("bob_reviewer").orElseGet(() -> {
            User u = new User();
            u.setUsername("bob_reviewer");
            u.setPassword("$2a$10$wK1.V/VbS2sV7Q3.11rYuehBfZP8U5P5rJqPcfGtz72cQyCbmf8Q2");
            u.setEmail("bob@alpha.com");
            u.setRole(Role.PROJECT_MANAGER);
            return userRepository.save(u);
        });

        testClientCharlie = userRepository.findByUsername("charlie_client").orElseGet(() -> {
            User u = new User();
            u.setUsername("charlie_client");
            u.setPassword("$2a$10$wK1.V/VbS2sV7Q3.11rYuehBfZP8U5P5rJqPcfGtz72cQyCbmf8Q2");
            u.setEmail("charlie@clientcorp.com");
            u.setRole(Role.CLIENT);
            return userRepository.save(u);
        });

        clientAlpha = clientRepository.findAll().stream()
                .filter(c -> "Client Corp Alpha".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setName("Client Corp Alpha");
                    c.setEmail("charlie@clientcorp.com");
                    c.setOrganization(orgAlpha);
                    return clientRepository.save(c);
                });

        // Ensure memberships
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgAlpha.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgAlpha);
            m.setRole("ADMIN");
            membershipRepository.save(m);
        }
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgBeta.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgBeta);
            m.setRole("ADMIN");
            membershipRepository.save(m);
        }
        if (membershipRepository.findByUserIdAndOrganizationId(testUserAlice.getId(), orgAlpha.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(testUserAlice);
            m.setOrganization(orgAlpha);
            m.setRole("MEMBER");
            membershipRepository.save(m);
        }

        // Project Alpha in Org Alpha
        projectAlpha = projectRepository.findAll().stream()
                .filter(p -> "P14 Project Alpha".equals(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setName("P14 Project Alpha");
                    p.setDescription("Test Deliverables Project");
                    p.setStartDate(LocalDate.now());
                    p.setDeadline(LocalDate.now().plusMonths(3));
                    p.setStatus(ProjectStatus.IN_PROGRESS);
                    p.setOrganization(orgAlpha);
                    p.setClient(clientAlpha);
                    return projectRepository.save(p);
                });

        // Project Beta in Org Beta
        projectBeta = projectRepository.findAll().stream()
                .filter(p -> "P14 Project Beta".equals(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setName("P14 Project Beta");
                    p.setDescription("Org Beta Project");
                    p.setStartDate(LocalDate.now());
                    p.setDeadline(LocalDate.now().plusMonths(2));
                    p.setStatus(ProjectStatus.PLANNED);
                    p.setOrganization(orgBeta);
                    return projectRepository.save(p);
                });

        // Task in Project Alpha
        taskAlpha = taskRepository.findAll().stream()
                .filter(t -> "Architecture Blueprint".equals(t.getName()) && t.getProject().getId().equals(projectAlpha.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Task t = new Task();
                    t.setName("Architecture Blueprint");
                    t.setDescription("Produce technical architecture blueprint");
                    t.setProject(projectAlpha);
                    t.setEstimatedDuration(5);
                    t.setStatus(TaskStatus.IN_PROGRESS);
                    return taskRepository.save(t);
                });
    }

    @Test
    @DisplayName("Test 1: Deliverable Creation, Validation & Project Retrieval")
    void testDeliverableCreationAndRetrieval() throws Exception {
        CreateDeliverableRequest req = new CreateDeliverableRequest();
        req.setName("System Architecture Spec");
        req.setDescription("Core system architecture and sequence diagrams");
        req.setDeliverableType(DeliverableType.TECHNICAL_DOCUMENT);
        req.setTaskId(taskAlpha.getId());
        req.setResponsibleUserId(testUserAlice.getId());
        req.setRequiredSubmissionDate(LocalDate.now().plusDays(7));
        req.setIsClientVisible(true);
        req.setIsConfidential(false);

        MvcResult result = mockMvc.perform(post("/api/projects/" + projectAlpha.getId() + "/deliverables")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        DeliverableDto created = objectMapper.readValue(result.getResponse().getContentAsString(), DeliverableDto.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("System Architecture Spec");
        assertThat(created.getStatus()).isEqualTo(DeliverableStatus.PLANNED);
        assertThat(created.getCurrentVersionNumber()).isEqualTo(1);
        assertThat(created.getTaskId()).isEqualTo(taskAlpha.getId());

        // Get project deliverables
        MvcResult listRes = mockMvc.perform(get("/api/projects/" + projectAlpha.getId() + "/deliverables")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<DeliverableDto> deliverables = objectMapper.readValue(
                listRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeliverableDto.class)
        );
        assertThat(deliverables).anyMatch(d -> d.getId().equals(created.getId()));
    }

    @Test
    @DisplayName("Test 2: Document Upload, Extension/Size Validation, Checksum & Versioning")
    void testDocumentUploadAndVersioning() throws Exception {
        // Create deliverable
        CreateDeliverableRequest createReq = new CreateDeliverableRequest();
        createReq.setName("API Documentation Package");
        createReq.setDeliverableType(DeliverableType.TECHNICAL_DOCUMENT);
        createReq.setResponsibleUserId(testUserAlice.getId());
        DeliverableDto deliverable = deliverableService.createDeliverable(projectAlpha.getId(), createReq, "admin");

        // 1. Test invalid file extension rejection (e.g. .exe)
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malicious_script.exe", "application/octet-stream", "MZ...test".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/deliverables/" + deliverable.getId() + "/documents")
                        .file(exeFile)
                        .param("versionNotes", "Initial build")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isBadRequest());

        // 2. Test valid PDF document upload (v1)
        byte[] docV1Content = "%PDF-1.4 Architectural Spec v1 content...".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFileV1 = new MockMultipartFile(
                "file", "architecture_spec_v1.pdf", "application/pdf", docV1Content
        );
        MvcResult v1Res = mockMvc.perform(multipart("/api/deliverables/" + deliverable.getId() + "/documents")
                        .file(pdfFileV1)
                        .param("versionNotes", "Initial version for preliminary review")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isCreated())
                .andReturn();

        DeliverableDocumentDto docV1Dto = objectMapper.readValue(v1Res.getResponse().getContentAsString(), DeliverableDocumentDto.class);
        assertThat(docV1Dto.getVersionNumber()).isEqualTo(1);
        assertThat(docV1Dto.getOriginalFilename()).isEqualTo("architecture_spec_v1.pdf");
        assertThat(docV1Dto.getChecksum()).isNotNull();
        assertThat(docV1Dto.getStorageKey()).isNotNull();

        // Verify deliverable moved to IN_PROGRESS and current version is 1
        Deliverable updatedDeliv = deliverableRepository.findById(deliverable.getId()).orElseThrow();
        assertThat(updatedDeliv.getStatus()).isEqualTo(DeliverableStatus.IN_PROGRESS);
        assertThat(updatedDeliv.getCurrentVersionNumber()).isEqualTo(1);

        // 3. Test document download
        MvcResult downloadRes = mockMvc.perform(get("/api/deliverables/" + deliverable.getId() + "/documents/" + docV1Dto.getId() + "/download")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(downloadRes.getResponse().getContentAsByteArray()).isEqualTo(docV1Content);

        // 4. Upload revision v2
        byte[] docV2Content = "%PDF-1.4 Architectural Spec v2 updated content...".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFileV2 = new MockMultipartFile(
                "file", "architecture_spec_v2.pdf", "application/pdf", docV2Content
        );
        MvcResult v2Res = mockMvc.perform(multipart("/api/deliverables/" + deliverable.getId() + "/documents")
                        .file(pdfFileV2)
                        .param("versionNotes", "Updated per internal sync")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isCreated())
                .andReturn();

        DeliverableDocumentDto docV2Dto = objectMapper.readValue(v2Res.getResponse().getContentAsString(), DeliverableDocumentDto.class);
        assertThat(docV2Dto.getVersionNumber()).isEqualTo(2);

        // Check document versions list
        MvcResult historyRes = mockMvc.perform(get("/api/deliverables/" + deliverable.getId() + "/versions")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<DeliverableDocumentDto> versions = objectMapper.readValue(
                historyRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeliverableDocumentDto.class)
        );
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(2); // ordered desc
        assertThat(versions.get(1).getVersionNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("Test 3: Workflow Transitions (Submit, Review, Anti-Self-Approval, Approval Decision)")
    void testWorkflowTransitionsAndAntiSelfApproval() throws Exception {
        // Create deliverable assigned to Alice
        CreateDeliverableRequest createReq = new CreateDeliverableRequest();
        createReq.setName("Security Hardening Report");
        createReq.setDeliverableType(DeliverableType.TECHNICAL_DOCUMENT);
        createReq.setResponsibleUserId(testUserAlice.getId());
        DeliverableDto deliverable = deliverableService.createDeliverable(projectAlpha.getId(), createReq, "admin");

        // Cannot submit without attached document
        mockMvc.perform(post("/api/deliverables/" + deliverable.getId() + "/submit")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isBadRequest());

        // Upload document
        MockMultipartFile file = new MockMultipartFile("file", "security_report.pdf", "application/pdf", "security data".getBytes());
        deliverableService.uploadDocument(deliverable.getId(), file, "v1", "admin");

        // Submit deliverable
        MvcResult submitRes = mockMvc.perform(post("/api/deliverables/" + deliverable.getId() + "/submit")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();
        DeliverableDto submitted = objectMapper.readValue(submitRes.getResponse().getContentAsString(), DeliverableDto.class);
        assertThat(submitted.getStatus()).isEqualTo(DeliverableStatus.SUBMITTED);
        assertThat(submitted.getActualSubmissionDate()).isNotNull();

        // Assign reviewer Bob
        deliverableService.assignReviewer(deliverable.getId(), testReviewerBob.getId(), LocalDate.now().plusDays(3), "admin");
        Deliverable inReview = deliverableRepository.findById(deliverable.getId()).orElseThrow();
        assertThat(inReview.getStatus()).isEqualTo(DeliverableStatus.UNDER_REVIEW);
        assertThat(inReview.getAssignedReviewer().getId()).isEqualTo(testReviewerBob.getId());

        // Test Anti-Self-Approval Rule: Alice cannot approve her own work
        ApprovalDecisionRequest selfApproveReq = new ApprovalDecisionRequest();
        selfApproveReq.setDecision(ApprovalDecision.APPROVED);
        selfApproveReq.setComments("I approve my own work");

        assertThatThrownBy(() -> deliverableService.recordApprovalDecision(deliverable.getId(), selfApproveReq, "alice_dev"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Self-approval prohibited");

        // Bob records CHANGES_REQUESTED
        ApprovalDecisionRequest changesReq = new ApprovalDecisionRequest();
        changesReq.setDecision(ApprovalDecision.CHANGES_REQUESTED);
        changesReq.setComments("Please add threat modeling details in Section 4.");

        MvcResult changesRes = mockMvc.perform(post("/api/deliverables/" + deliverable.getId() + "/reviews/decision")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changesReq)))
                .andExpect(status().isOk())
                .andReturn();

        DeliverableApprovalDto approval1 = objectMapper.readValue(changesRes.getResponse().getContentAsString(), DeliverableApprovalDto.class);
        assertThat(approval1.getDecision()).isEqualTo(ApprovalDecision.CHANGES_REQUESTED);
        assertThat(approval1.getComments()).isEqualTo("Please add threat modeling details in Section 4.");

        Deliverable afterChanges = deliverableRepository.findById(deliverable.getId()).orElseThrow();
        assertThat(afterChanges.getStatus()).isEqualTo(DeliverableStatus.CHANGES_REQUESTED);

        // Upload revision v2 to address changes
        MockMultipartFile fileV2 = new MockMultipartFile("file", "security_report_v2.pdf", "application/pdf", "v2 with threat model".getBytes());
        deliverableService.uploadDocument(deliverable.getId(), fileV2, "Addressed threat modeling", "alice_dev");

        // Deliverable moves back to UNDER_REVIEW automatically for v2
        Deliverable afterV2 = deliverableRepository.findById(deliverable.getId()).orElseThrow();
        assertThat(afterV2.getStatus()).isEqualTo(DeliverableStatus.UNDER_REVIEW);
        assertThat(afterV2.getCurrentVersionNumber()).isEqualTo(2);

        // Bob approves v2
        ApprovalDecisionRequest approveReq = new ApprovalDecisionRequest();
        approveReq.setDecision(ApprovalDecision.APPROVED);
        approveReq.setComments("Threat modeling looks solid. Approved.");

        MvcResult approveRes = mockMvc.perform(post("/api/deliverables/" + deliverable.getId() + "/reviews/decision")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andReturn();

        DeliverableApprovalDto approval2 = objectMapper.readValue(approveRes.getResponse().getContentAsString(), DeliverableApprovalDto.class);
        assertThat(approval2.getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(approval2.getDocumentVersionNumber()).isEqualTo(2);

        Deliverable approved = deliverableRepository.findById(deliverable.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(DeliverableStatus.APPROVED);

        // Verify complete approval audit history has both decisions preserved
        List<DeliverableApprovalDto> history = deliverableService.getDeliverableApprovals(deliverable.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getDecision()).isEqualTo(ApprovalDecision.APPROVED);
        assertThat(history.get(1).getDecision()).isEqualTo(ApprovalDecision.CHANGES_REQUESTED);
    }

    @Test
    @DisplayName("Test 4: Task Completion Gate Enforcement & Unblocking")
    void testTaskCompletionGateEnforcement() throws Exception {
        // Create deliverable
        CreateDeliverableRequest createReq = new CreateDeliverableRequest();
        createReq.setName("UI Component Library Spec");
        createReq.setDeliverableType(DeliverableType.UI_PROTOTYPE);
        createReq.setTaskId(taskAlpha.getId());
        createReq.setResponsibleUserId(testUserAlice.getId());
        DeliverableDto deliverable = deliverableService.createDeliverable(projectAlpha.getId(), createReq, "admin");

        // Configure task completion gate
        ConfigureTaskGateRequest gateReq = new ConfigureTaskGateRequest();
        gateReq.setDeliverableId(deliverable.getId());
        gateReq.setGateType(GateType.COMPLETION_GATE);
        gateReq.setIsMandatory(true);

        MvcResult gateRes = mockMvc.perform(post("/api/tasks/" + taskAlpha.getId() + "/approval-gates")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gateReq)))
                .andExpect(status().isCreated())
                .andReturn();

        TaskApprovalGateDto gateDto = objectMapper.readValue(gateRes.getResponse().getContentAsString(), TaskApprovalGateDto.class);
        assertThat(gateDto.getId()).isNotNull();
        assertThat(gateDto.getIsSatisfied()).isFalse();

        // Attempt to mark task completed - should fail because gate is unsatisfied
        TaskDto updateAttempt = new TaskDto();
        updateAttempt.setName(taskAlpha.getName());
        updateAttempt.setStatus(TaskStatus.COMPLETED);
        assertThatThrownBy(() -> taskService.updateTask(taskAlpha.getId(), updateAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be marked COMPLETED")
                .hasMessageContaining("mandatory deliverable");

        // Upload doc and approve deliverable
        MockMultipartFile file = new MockMultipartFile("file", "ui_spec.pdf", "application/pdf", "ui spec content".getBytes());
        deliverableService.uploadDocument(deliverable.getId(), file, "v1", "admin");
        deliverableService.submitDeliverable(deliverable.getId(), "admin");

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest();
        decision.setDecision(ApprovalDecision.APPROVED);
        decision.setComments("Gate deliverable approved");
        deliverableService.recordApprovalDecision(deliverable.getId(), decision, "admin");

        // Verify gate is now marked satisfied via getTaskGates
        List<TaskApprovalGateDto> gates = deliverableService.getTaskGates(taskAlpha.getId());
        assertThat(gates).anyMatch(g -> g.getId().equals(gateDto.getId()) && Boolean.TRUE.equals(g.getIsSatisfied()));

        // Now task completion succeeds
        TaskDto completedTask = taskService.updateTask(taskAlpha.getId(), updateAttempt);
        assertThat(completedTask.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    @DisplayName("Test 5: Overdue Approval Delay Impact Analysis")
    void testOverdueApprovalDelayImpact() throws Exception {
        CreateDeliverableRequest createReq = new CreateDeliverableRequest();
        createReq.setName("Overdue Prototype Evaluation");
        createReq.setDeliverableType(DeliverableType.UI_PROTOTYPE);
        createReq.setTaskId(taskAlpha.getId());
        DeliverableDto deliverable = deliverableService.createDeliverable(projectAlpha.getId(), createReq, "admin");

        // Attach gate
        ConfigureTaskGateRequest gateReq = new ConfigureTaskGateRequest();
        gateReq.setDeliverableId(deliverable.getId());
        gateReq.setGateType(GateType.COMPLETION_GATE);
        gateReq.setIsMandatory(true);
        deliverableService.configureTaskGate(taskAlpha.getId(), gateReq, "admin");

        // Upload doc & submit
        MockMultipartFile file = new MockMultipartFile("file", "proto.pdf", "application/pdf", "proto bytes".getBytes());
        deliverableService.uploadDocument(deliverable.getId(), file, "v1", "admin");
        deliverableService.submitDeliverable(deliverable.getId(), "admin");

        // Assign reviewer with past decision date
        LocalDate pastDate = LocalDate.now().minusDays(5);
        deliverableService.assignReviewer(deliverable.getId(), testReviewerBob.getId(), pastDate, "admin");

        // Check delay impact analysis
        MvcResult impactRes = mockMvc.perform(get("/api/deliverables/" + deliverable.getId() + "/delay-impact")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        ApprovalDelayImpactDto impact = objectMapper.readValue(impactRes.getResponse().getContentAsString(), ApprovalDelayImpactDto.class);
        assertThat(impact.getIsOverdue()).isTrue();
        assertThat(impact.getDaysOverdue()).isGreaterThanOrEqualTo(5);
        assertThat(impact.getBlockedTasksCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Test 6: Client Portal Access & Confidentiality Protection")
    void testClientPortalAccessAndConfidentiality() throws Exception {
        // Create 1 public deliverable and 1 confidential deliverable
        CreateDeliverableRequest pubReq = new CreateDeliverableRequest();
        pubReq.setName("Client Handover Manual");
        pubReq.setDeliverableType(DeliverableType.CLIENT_HANDOVER);
        pubReq.setIsClientVisible(true);
        pubReq.setIsConfidential(false);
        DeliverableDto publicDeliv = deliverableService.createDeliverable(projectAlpha.getId(), pubReq, "admin");

        CreateDeliverableRequest confReq = new CreateDeliverableRequest();
        confReq.setName("Internal Architecture Trade Secrets");
        confReq.setDeliverableType(DeliverableType.TECHNICAL_DOCUMENT);
        confReq.setIsClientVisible(false);
        confReq.setIsConfidential(true);
        DeliverableDto confDeliv = deliverableService.createDeliverable(projectAlpha.getId(), confReq, "admin");

        // Upload files to both
        MockMultipartFile pubFile = new MockMultipartFile("file", "handover.pdf", "application/pdf", "handover info".getBytes());
        DeliverableDocumentDto pubDoc = deliverableService.uploadDocument(publicDeliv.getId(), pubFile, "v1", "admin");

        MockMultipartFile confFile = new MockMultipartFile("file", "secrets.pdf", "application/pdf", "confidential secrets".getBytes());
        DeliverableDocumentDto confDoc = deliverableService.uploadDocument(confDeliv.getId(), confFile, "v1", "admin");

        // Client lists deliverables
        List<DeliverableDto> clientVisibleList = deliverableService.getProjectDeliverables(projectAlpha.getId(), "charlie_client");
        assertThat(clientVisibleList).anyMatch(d -> d.getId().equals(publicDeliv.getId()));
        assertThat(clientVisibleList).noneMatch(d -> d.getId().equals(confDeliv.getId()));

        // Client can download public document
        Resource pubResource = deliverableService.downloadDocument(publicDeliv.getId(), pubDoc.getId(), "charlie_client");
        assertThat(pubResource.exists()).isTrue();

        // Client downloading confidential document is rejected
        assertThatThrownBy(() -> deliverableService.downloadDocument(confDeliv.getId(), confDoc.getId(), "charlie_client"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Test 7: Multi-Tenant Workspace Isolation")
    void testMultiTenantIsolation() {
        // Project Beta is in Org Beta. Trying to create deliverable in Beta from Org Alpha context should fail.
        CreateDeliverableRequest req = new CreateDeliverableRequest();
        req.setName("Cross Tenant Deliverable");
        req.setDeliverableType(DeliverableType.DESIGN_SPECIFICATION);

        TenantContext.setTenantId(orgAlpha.getId());
        try {
            assertThatThrownBy(() -> deliverableService.createDeliverable(projectBeta.getId(), req, "admin"))
                    .isInstanceOf(com.prs.tenant.TenantAccessDeniedException.class);
        } finally {
            TenantContext.clear();
        }
    }
}
