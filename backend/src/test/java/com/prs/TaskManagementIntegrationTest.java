package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.TaskDependencyService;
import com.prs.service.TaskService;
import com.prs.tenant.TenantContext;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tasks Page Refinement & Task Management Integration Tests")
public class TaskManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private TaskDependencyRepository dependencyRepository;

    @Autowired
    private TaskApprovalGateRepository gateRepository;

    @Autowired
    private DeliverableRepository deliverableRepository;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskDependencyService dependencyService;

    private String adminJwt;
    private Organization orgAlpha;
    private Organization orgBeta;
    private Project projectAlpha;
    private Worker workerAlpha;
    private Worker workerBeta;

    @BeforeEach
    void setUp() throws Exception {
        // Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(loginRes.getResponse().getContentAsString(), AuthResponse.class);
        adminJwt = auth.getToken();

        orgAlpha = organizationRepository.findBySlug("task-alpha-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Task Alpha Org");
            o.setSlug("task-alpha-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        orgBeta = organizationRepository.findBySlug("task-beta-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Task Beta Org");
            o.setSlug("task-beta-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        TenantContext.setTenantId(orgAlpha.getId());

        // Test project
        projectAlpha = new Project();
        projectAlpha.setName("Platform Modernization " + System.currentTimeMillis());
        projectAlpha.setOrganization(orgAlpha);
        projectAlpha.setStatus(ProjectStatus.IN_PROGRESS);
        projectAlpha.setStartDate(LocalDate.now());
        projectAlpha.setDeadline(LocalDate.now().plusMonths(3));
        projectAlpha = projectRepository.save(projectAlpha);

        // Workers
        final Long orgAlphaId = orgAlpha.getId();
        workerAlpha = workerRepository.findAll().stream()
                .filter(w -> w.getOrganization() != null && w.getOrganization().getId().equals(orgAlphaId))
                .findFirst()
                .orElseGet(() -> {
                    Worker w = new Worker();
                    w.setName("Dev Lead Alice");
                    w.setEmail("alice@acme.com");
                    w.setSkills("Java, Spring, CPM");
                    w.setOrganization(orgAlpha);
                    w.setStatus(WorkerStatus.AVAILABLE);
                    return workerRepository.save(w);
                });

        final Long orgBetaId = orgBeta.getId();
        workerBeta = workerRepository.findAll().stream()
                .filter(w -> w.getOrganization() != null && w.getOrganization().getId().equals(orgBetaId))
                .findFirst()
                .orElseGet(() -> {
                    Worker w = new Worker();
                    w.setName("External Bob");
                    w.setEmail("bob@cyberdyne.com");
                    w.setSkills("Security");
                    w.setOrganization(orgBeta);
                    w.setStatus(WorkerStatus.AVAILABLE);
                    return workerRepository.save(w);
                });
    }

    @Test
    @DisplayName("Test 1: Task Creation and Full Validation (Dates, Duration, Required fields)")
    void testTaskCreationAndValidation() throws Exception {
        // Validation: Empty name should be rejected
        TaskDto invalidTask = new TaskDto();
        invalidTask.setName("  ");
        invalidTask.setProjectId(projectAlpha.getId());
        invalidTask.setEstimatedDuration(3);
        assertThatThrownBy(() -> taskService.createTask(invalidTask))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task name is required");

        // Validation: Deadline earlier than start date
        invalidTask.setName("Invalid Dates Task");
        invalidTask.setStartDate(LocalDate.now().plusDays(10));
        invalidTask.setDeadline(LocalDate.now().plusDays(5));
        assertThatThrownBy(() -> taskService.createTask(invalidTask))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be earlier than the start date");

        // Valid creation with assigned worker
        TaskDto validTask = new TaskDto();
        validTask.setName("Architecture Design");
        validTask.setDescription("Define clean microservice boundaries");
        validTask.setProjectId(projectAlpha.getId());
        validTask.setEstimatedDuration(5);
        validTask.setStartDate(LocalDate.now());
        validTask.setDeadline(LocalDate.now().plusDays(10));
        validTask.setStatus(TaskStatus.TODO);
        validTask.setProgress(0);
        validTask.setAssignedWorkerId(workerAlpha.getId());

        TaskDto created = taskService.createTask(validTask);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Architecture Design");
        assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(created.getProgress()).isEqualTo(0);
        assertThat(created.getAssignedWorkerId()).isEqualTo(workerAlpha.getId());
        assertThat(created.getAssignedWorkerName()).isEqualTo(workerAlpha.getName());
        assertThat(created.getAssignedWorkerSkills()).contains("Java");
    }

    @Test
    @DisplayName("Test 2: Status & Progress Consistency Rules")
    void testStatusAndProgressConsistency() {
        Task task = new Task();
        task.setName("Consistency Test Task");
        task.setProject(projectAlpha);
        task.setEstimatedDuration(3);
        task = taskRepository.save(task);

        // Case A: TODO status forces 0% progress
        TaskDto updateDto = new TaskDto();
        updateDto.setStatus(TaskStatus.TODO);
        updateDto.setProgress(0);
        TaskDto res = taskService.updateTask(task.getId(), updateDto);
        assertThat(res.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(res.getProgress()).isEqualTo(0);

        // Case B: Updating progress > 0 on TODO automatically transitions to IN_PROGRESS
        updateDto = new TaskDto();
        updateDto.setStatus(TaskStatus.TODO);
        updateDto.setProgress(25);
        res = taskService.updateTask(task.getId(), updateDto);
        assertThat(res.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(res.getProgress()).isEqualTo(25);

        // Case C: Setting progress to 100 transitions to COMPLETED
        updateDto = new TaskDto();
        updateDto.setProgress(100);
        res = taskService.updateTask(task.getId(), updateDto);
        assertThat(res.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(res.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("Test 3: Worker Assignment Integrity & Unassignment")
    void testWorkerAssignmentAndPreservation() {
        // Create task with assigned worker
        TaskDto dto = new TaskDto();
        dto.setName("Worker Integrity Task");
        dto.setProjectId(projectAlpha.getId());
        dto.setEstimatedDuration(4);
        dto.setAssignedWorkerId(workerAlpha.getId());
        TaskDto created = taskService.createTask(dto);
        assertThat(created.getAssignedWorkerId()).isEqualTo(workerAlpha.getId());

        // Update task description without passing assignedWorkerId -> worker assignment is preserved!
        TaskDto updateDesc = new TaskDto();
        updateDesc.setDescription("Updated description only");
        TaskDto updated = taskService.updateTask(created.getId(), updateDesc);
        assertThat(updated.getAssignedWorkerId()).isEqualTo(workerAlpha.getId());
        assertThat(updated.getDescription()).isEqualTo("Updated description only");

        // Explicit unassignment with assignedWorkerId = 0
        TaskDto unassignDto = new TaskDto();
        unassignDto.setAssignedWorkerId(0L);
        TaskDto unassigned = taskService.updateTask(created.getId(), unassignDto);
        assertThat(unassigned.getAssignedWorkerId()).isNull();
        assertThat(unassigned.getAssignedWorkerName()).isNull();

        // Cross-organization worker assignment rejection
        TaskDto crossOrgDto = new TaskDto();
        crossOrgDto.setAssignedWorkerId(workerBeta.getId());
        assertThatThrownBy(() -> taskService.updateTask(created.getId(), crossOrgDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Worker belongs to a different organization");
    }

    @Test
    @DisplayName("Test 4: Task Dependencies, Cycle Detection & Blocked Explanations")
    void testTaskDependenciesAndCycleDetection() {
        // Task A -> Task B -> Task C
        Task taskA = new Task();
        taskA.setName("Task A - Database Schema");
        taskA.setProject(projectAlpha);
        taskA.setStatus(TaskStatus.IN_PROGRESS);
        taskA.setProgress(50);
        taskA.setEstimatedDuration(3);
        taskA = taskRepository.save(taskA);

        Task taskB = new Task();
        taskB.setName("Task B - Business Logic");
        taskB.setProject(projectAlpha);
        taskB.setStatus(TaskStatus.TODO);
        taskB.setProgress(0);
        taskB.setEstimatedDuration(4);
        taskB = taskRepository.save(taskB);

        Task taskC = new Task();
        taskC.setName("Task C - API Endpoints");
        taskC.setProject(projectAlpha);
        taskC.setStatus(TaskStatus.TODO);
        taskC.setProgress(0);
        taskC.setEstimatedDuration(2);
        taskC = taskRepository.save(taskC);

        // Add A -> B dependency (B depends on A)
        TaskDependencyDto depDto1 = new TaskDependencyDto();
        depDto1.setPredecessorTaskId(taskA.getId());
        dependencyService.addDependency(taskB.getId(), depDto1);

        // Add B -> C dependency (C depends on B)
        TaskDependencyDto depDto2 = new TaskDependencyDto();
        depDto2.setPredecessorTaskId(taskB.getId());
        dependencyService.addDependency(taskC.getId(), depDto2);

        final Long taskAId = taskA.getId();

        int depCountBefore = dependencyRepository.findAll().size();

        // Verify cycle detection: adding C -> A dependency must fail!
        TaskDependencyDto cycleDep = new TaskDependencyDto();
        cycleDep.setPredecessorTaskId(taskC.getId());
        assertThatThrownBy(() -> dependencyService.addDependency(taskAId, cycleDep))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("circular dependency");

        // Verify no dependency was persisted after rejection
        assertThat(dependencyRepository.findAll()).hasSize(depCountBefore);

        // Self-dependency must fail
        TaskDependencyDto selfDep = new TaskDependencyDto();
        selfDep.setPredecessorTaskId(taskAId);
        assertThatThrownBy(() -> dependencyService.addDependency(taskAId, selfDep))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot depend on itself");

        // Inspect Task B dependency details
        TaskDependencyDetailsDto detailsB = dependencyService.getDependencyDetails(taskB.getId());
        assertThat(detailsB.isBlocked()).isTrue();
        assertThat(detailsB.getPredecessors()).hasSize(1);
        assertThat(detailsB.getPredecessors().get(0).getTaskId()).isEqualTo(taskA.getId());
        assertThat(detailsB.getPredecessors().get(0).isBlocking()).isTrue();
        assertThat(detailsB.getBlockingReasons()).anyMatch(r -> r.contains("Task A - Database Schema"));
        assertThat(detailsB.getSuccessors()).hasSize(1);
        assertThat(detailsB.getSuccessors().get(0).getTaskId()).isEqualTo(taskC.getId());

        final Long taskBId = taskB.getId();

        // Attempting to complete Task B while Task A is incomplete must fail in the backend!
        TaskDto completeAttempt = new TaskDto();
        completeAttempt.setStatus(TaskStatus.COMPLETED);
        assertThatThrownBy(() -> taskService.updateTask(taskBId, completeAttempt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be marked COMPLETED")
                .hasMessageContaining("Task A - Database Schema");

        // Complete Task A -> Task B becomes unblocked!
        taskA.setStatus(TaskStatus.COMPLETED);
        taskA.setProgress(100);
        taskRepository.save(taskA);

        TaskDependencyDetailsDto detailsBAfter = dependencyService.getDependencyDetails(taskB.getId());
        assertThat(detailsBAfter.isBlocked()).isFalse();
        assertThat(detailsBAfter.getPredecessors().get(0).isBlocking()).isFalse();
        assertThat(detailsBAfter.getBlockingReasons()).isEmpty();

        // Now Task B can be completed successfully!
        TaskDto bCompleted = taskService.updateTask(taskB.getId(), completeAttempt);
        assertThat(bCompleted.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(bCompleted.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("Test 5: REST API Endpoint Verification for Dependencies Details")
    void testDependencyDetailsRestApi() throws Exception {
        Task task = new Task();
        task.setName("API Tested Task");
        task.setProject(projectAlpha);
        task.setStatus(TaskStatus.TODO);
        task.setEstimatedDuration(2);
        task = taskRepository.save(task);

        mockMvc.perform(get("/api/tasks/" + task.getId() + "/dependency-details")
                .header("Authorization", "Bearer " + adminJwt)
                .header("X-Tenant-Id", orgAlpha.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(task.getId()))
                .andExpect(jsonPath("$.taskName").value("API Tested Task"))
                .andExpect(jsonPath("$.predecessors").isArray())
                .andExpect(jsonPath("$.successors").isArray())
                .andExpect(jsonPath("$.approvalGates").isArray());
    }
}
