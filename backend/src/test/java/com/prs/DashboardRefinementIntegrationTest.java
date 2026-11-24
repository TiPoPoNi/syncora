package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.AuthResponse;
import com.prs.dto.DashboardDto;
import com.prs.entity.OptimizationAuditLog;
import com.prs.entity.Project;
import com.prs.repository.OptimizationAuditLogRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.ProjectRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Dashboard Refinement & Loading States Integration Tests")
public class DashboardRefinementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OptimizationAuditLogRepository auditLogRepository;

    private String adminJwt;
    private String workerJwt;

    @BeforeEach
    void setUp() throws Exception {
        adminJwt = obtainToken("admin", "admin");
        workerJwt = obtainToken("worker", "admin");
    }

    private String obtainToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return resp.getToken();
    }

    @Test
    @DisplayName("1. Dashboard: returns accurate workspace-scoped metrics for Acme Global (ACM)")
    void test1_dashboardReturnsAccurateWorkspaceData() throws Exception {
        Long acmId = organizationRepository.findBySlug("acme-global").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", acmId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto).isNotNull();
        assertThat(dto.getTotalProjects()).isGreaterThanOrEqualTo(2);
        assertThat(dto.getActiveProjects()).isGreaterThanOrEqualTo(2);
        assertThat(dto.getRecentProjects()).isNotEmpty();
        assertThat(dto.getUpcomingMilestones()).isNotEmpty();
        assertThat(dto.getResourceWorkload()).isNotEmpty();
    }

    @Test
    @DisplayName("2. Dashboard: returns clean 0-metric state for empty workspace (STK) without errors")
    void test2_dashboardForEmptyWorkspaceReturnsZeroMetricsWithoutError() throws Exception {
        Long stkId = organizationRepository.findBySlug("stark-tech").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", stkId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto).isNotNull();
        assertThat(dto.getTotalProjects()).isEqualTo(0);
        assertThat(dto.getActiveProjects()).isEqualTo(0);
        assertThat(dto.getDelayedProjects()).isEqualTo(0);
        assertThat(dto.getProjectsAtRisk()).isEqualTo(0);
        assertThat(dto.getRecentProjects()).isEmpty();
        assertThat(dto.getUpcomingMilestones()).isEmpty();
    }

    @Test
    @DisplayName("3. Resilience: /api/organizations ignores stale/mismatched X-Organization-Id header and returns user orgs")
    void test3_organizationsEndpointResilientToStaleOrgHeader() throws Exception {
        Long stkId = organizationRepository.findBySlug("stark-tech").orElseThrow().getId();

        // Worker is only in ACM, not STK. Stale header for STK must NOT block the discovery endpoint with 403.
        MvcResult result = mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + workerJwt)
                        .header("X-Organization-Id", stkId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Acme Global Solutions");
        assertThat(body).doesNotContain("Stark Tech Industries");
    }

    @Test
    @DisplayName("4. Dashboard: populates recentActivities from immutable project audit logs")
    void test4_dashboardIncludesRecentActivities() throws Exception {
        Long acmId = organizationRepository.findBySlug("acme-global").orElseThrow().getId();
        Project project = projectRepository.findByOrganizationId(acmId).get(0);

        OptimizationAuditLog log = new OptimizationAuditLog();
        log.setProject(project);
        log.setAction("SCHEDULE_OPTIMIZATION_RUN");
        log.setPerformedBy("admin");
        log.setDetails("Simulated multi-objective run");
        auditLogRepository.save(log);

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", acmId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto.getRecentActivities()).isNotNull();
        assertThat(dto.getRecentActivities()).anyMatch(a -> "SCHEDULE_OPTIMIZATION_RUN".equals(a.getAction()));
    }

    @Test
    @DisplayName("5. Security: non-admin without tenant header does not leak cross-tenant data")
    void test5_nonAdminWithoutTenantDoesNotLeakCrossTenantData() throws Exception {
        Long stkId = organizationRepository.findBySlug("stark-tech").orElseThrow().getId();
        java.util.Set<Long> stkProjectIds = projectRepository.findByOrganizationId(stkId).stream()
                .map(Project::getId)
                .collect(java.util.stream.Collectors.toSet());

        // When worker requests dashboard without an explicit header, TenantFilter resolves worker's default org (ACM)
        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto).isNotNull();
        assertThat(dto.getTotalProjects()).isGreaterThanOrEqualTo(2);
        // Verify zero cross-tenant leakage: no STK projects appear in worker's ACM dashboard
        if (dto.getRecentProjects() != null) {
            assertThat(dto.getRecentProjects()).noneMatch(p -> stkProjectIds.contains(p.getId()));
        }
    }

    @Test
    @DisplayName("6. Security: requesting dashboard for unauthorized workspace returns 403 Forbidden")
    void test6_unauthorizedTenantAccessReturns403() throws Exception {
        Long stkId = organizationRepository.findBySlug("stark-tech").orElseThrow().getId();

        // Worker belongs to ACM, not STK. Accessing STK dashboard must be rejected with 403.
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + workerJwt)
                        .header("X-Organization-Id", stkId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. Risk Consistency: projects at risk match projects requiring attention with explicit risk reasons")
    void test7_projectRiskConsistencyAndAttentionList() throws Exception {
        Long acmId = organizationRepository.findBySlug("acme-global").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", acmId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto).isNotNull();

        // If projectsAtRisk > 0, projectsRequiringAttention must not be empty
        if (dto.getProjectsAtRisk() > 0) {
            assertThat(dto.getProjectsRequiringAttention()).isNotEmpty();
            assertThat(dto.getProjectsRequiringAttention().size()).isEqualTo(dto.getProjectsAtRisk());
            
            for (DashboardDto.DashboardProjectSummaryDto p : dto.getProjectsRequiringAttention()) {
                assertThat(p.isAtRisk()).isTrue();
                assertThat(p.getRiskReason()).isNotBlank();
                // Risk reason must reflect either delay or resource contention
                boolean explainsRisk = p.isDelayed() || p.isHasResourceConflict();
                assertThat(explainsRisk).isTrue();
            }
        }
    }

    @Test
    @DisplayName("8. Worker Workload: distinguishes roster availability from active task assignments and conflict states")
    void test8_workerWorkloadAndAvailabilityDistinct() throws Exception {
        Long acmId = organizationRepository.findBySlug("acme-global").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", acmId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto.getResourceWorkload()).isNotEmpty();

        for (DashboardDto.DashboardWorkerWorkloadDto w : dto.getResourceWorkload()) {
            assertThat(w.getRosterStatus()).isNotBlank();
            assertThat(w.getWorkloadStatus()).isNotBlank();
            assertThat(w.getWorkloadSummary()).isNotBlank();

            // An overallocated worker with conflicts must NEVER be labeled AVAILABLE in workload
            if (w.isHasConflict()) {
                assertThat(w.getWorkloadStatus()).isEqualTo("OVERALLOCATED");
            }
            // An active worker with tasks and no conflict must not be marked UNAVAILABLE unless roster says so
            if (w.getActiveAssignedTasks() > 0 && !w.isHasConflict() && !"UNAVAILABLE".equalsIgnoreCase(w.getRosterStatus())) {
                assertThat(w.getWorkloadStatus()).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    @DisplayName("9. Task Deadlines: preserves distinct required deadline vs calculated CPM planned finish")
    void test9_taskDeadlinesDistinctFromPlannedFinish() throws Exception {
        Long acmId = organizationRepository.findBySlug("acme-global").orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", acmId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        DashboardDto dto = objectMapper.readValue(result.getResponse().getContentAsString(), DashboardDto.class);
        assertThat(dto.getUpcomingMilestones()).isNotEmpty();

        for (DashboardDto.DashboardMilestoneDto m : dto.getUpcomingMilestones()) {
            assertThat(m.getTaskName()).isNotBlank();
            assertThat(m.getProjectName()).isNotBlank();
            // Deadline can be null (not set), plannedFinishDate is CPM calculated
            if (m.getDeadline() != null && m.getPlannedFinishDate() != null) {
                if (m.getPlannedFinishDate().isAfter(m.getDeadline())) {
                    assertThat(m.isDeadlineExceeded()).isTrue();
                    assertThat(m.getDelayDays()).isGreaterThan(0);
                }
            }
        }
    }
}
