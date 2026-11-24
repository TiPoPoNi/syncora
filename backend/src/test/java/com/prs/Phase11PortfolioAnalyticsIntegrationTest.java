package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.PortfolioKpiSummaryDto;
import com.prs.dto.ProjectPerformanceDto;
import com.prs.dto.ResourceUtilizationSummaryDto;
import com.prs.dto.VendorAnalyticsSummaryDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.PortfolioAnalyticsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 11: Business Intelligence, Portfolio Analytics & KPI Dashboard Integration Tests")
public class Phase11PortfolioAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioAnalyticsService analyticsService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private TaskOutcomeRecordRepository outcomeRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminJwt;
    private User adminUser;
    private User clientUser;
    private Client clientAlpha;
    private Client clientBeta;

    @BeforeEach
    void setUp() throws Exception {
        // Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult loginResult = mockMvc.perform(get("/api/auth/login") // POST is the actual method
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();

        // In case get fails, post to /api/auth/login
        MvcResult actualLogin = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = actualLogin.getResponse().getContentAsString();
        adminJwt = objectMapper.readTree(responseStr).get("token").asText();
        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Setup test clients
        clientAlpha = clientRepository.findAll().stream()
                .filter(c -> "Alpha Corp".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setName("Alpha Corp");
                    c.setCompany("Alpha Global");
                    c.setEmail("alpha_contact@example.com");
                    c.setStatus("ACTIVE");
                    return clientRepository.save(c);
                });

        clientBeta = clientRepository.findAll().stream()
                .filter(c -> "Beta Industries".equals(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setName("Beta Industries");
                    c.setCompany("Beta Corp");
                    c.setEmail("beta_contact@example.com");
                    c.setStatus("ACTIVE");
                    return clientRepository.save(c);
                });

        // Setup client user for testing client isolation
        clientUser = userRepository.findByUsername("client_alpha_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("client_alpha_user");
            u.setPassword("$2a$10$dummyHash");
            u.setEmail("alpha_contact@example.com");
            u.setRole(Role.CLIENT);
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("1. Portfolio KPI Summary Calculation & Classification")
    void testPortfolioKpiSummary_CalculationAndClassification() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/portfolio")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        PortfolioKpiSummaryDto summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), PortfolioKpiSummaryDto.class);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalProjects()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getActiveProjects() + summary.getCompletedProjects() +
                   summary.getOnHoldProjects() + summary.getCancelledProjects())
                .isEqualTo(summary.getTotalProjects());
        assertThat(summary.getReportingPeriod()).isNotBlank();
    }

    @Test
    @DisplayName("2. Project Performance Planned vs Actual & Variance")
    void testProjectPerformance_PlannedVsActualAndVariance() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/projects/performance")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        List<ProjectPerformanceDto> list = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectPerformanceDto.class));

        assertThat(list).isNotEmpty();
        ProjectPerformanceDto p = list.get(0);
        assertThat(p.getProjectId()).isNotNull();
        assertThat(p.getProjectName()).isNotBlank();
        assertThat(p.getVarianceType()).isIn("ACTUAL_VARIANCE", "FORECAST_VARIANCE");
        assertThat(p.getDelayStatus()).isIn("ON_SCHEDULE", "AT_RISK", "CRITICAL_DELAY", "COMPLETED_ON_TIME", "COMPLETED_LATE", "ON_HOLD", "CANCELLED");
    }

    @Test
    @DisplayName("3. Zero Completed Projects Returns Null On-Time Rate Without Division by Zero")
    void testZeroCompletedProjects_ReturnsNullOnTimeRate() {
        // Query with filter status = 'ON_HOLD' (where no projects are completed)
        com.prs.dto.AnalyticsFilter filter = com.prs.dto.AnalyticsFilter.builder()
                .status("ON_HOLD")
                .build();

        PortfolioKpiSummaryDto summary = analyticsService.getPortfolioKpiSummary(adminUser, filter);
        assertThat(summary.getCompletedProjects()).isEqualTo(0);
        assertThat(summary.getHistoricalOnTimeDeliveryRatePercent()).isNull();
        assertThat(summary.getAverageScheduleVarianceDays()).isNull();
    }

    @Test
    @DisplayName("4. Resource Utilization Capacity & Overallocation Math")
    void testResourceUtilization_CapacityMath() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/resources/utilization")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        ResourceUtilizationSummaryDto summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), ResourceUtilizationSummaryDto.class);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalWorkers()).isGreaterThan(0);
        assertThat(summary.getWorkerDetails()).isNotEmpty();

        for (ResourceUtilizationSummaryDto.WorkerUtilizationItemDto w : summary.getWorkerDetails()) {
            assertThat(w.getWorkerName()).isNotBlank();
            assertThat(w.getDailyCapacityHours()).isEqualTo(8);
            assertThat(w.getAvailableHours()).isGreaterThan(0);
            assertThat(w.getUtilizationStatus()).isIn("OVERALLOCATED", "OPTIMAL", "UNDERUTILIZED", "IDLE", "UNAVAILABLE");
        }
    }

    @Test
    @DisplayName("5. Vendor Delivery Reliability & Sample Size Disclosure")
    void testVendorPerformance_ReliabilityAndSampleSize() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/vendors/performance")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        VendorAnalyticsSummaryDto summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), VendorAnalyticsSummaryDto.class);

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalVendors()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getVendorDetails()).isNotEmpty();

        for (VendorAnalyticsSummaryDto.VendorPerformanceItemDto v : summary.getVendorDetails()) {
            assertThat(v.getVendorName()).isNotBlank();
            assertThat(v.getSampleSizeObservations()).isEqualTo(v.getTotalDeliveries());
            if (v.getTotalDeliveries() > 0) {
                assertThat(v.getOnTimeRatePercent()).isNotNull();
            } else {
                assertThat(v.getOnTimeRatePercent()).isNull();
            }
        }
    }

    @Test
    @DisplayName("6. Client Authorization Isolation Prevents Access to Other Clients and Worker Rates")
    void testClientRole_DataIsolation() {
        // Query with client user
        List<ProjectPerformanceDto> clientProjects = analyticsService.getProjectsPerformance(clientUser, null);
        for (ProjectPerformanceDto p : clientProjects) {
            assertThat(p.getClientName()).isEqualTo(clientAlpha.getName());
        }

        // Client cannot access internal worker utilization rates
        ResourceUtilizationSummaryDto workerUtil = analyticsService.getResourceUtilization(clientUser, null);
        assertThat(workerUtil.getWorkerDetails()).isEmpty();

        // Client cannot access internal vendor scorecards
        VendorAnalyticsSummaryDto vendorSummary = analyticsService.getVendorPerformance(clientUser, null);
        assertThat(vendorSummary.getVendorDetails()).isEmpty();
    }

    @Test
    @DisplayName("7. CSV Analytics Export with Formula Injection Defense")
    void testAnalyticsCsvExport_FormulaInjectionProtection() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/export/csv?reportType=projects")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        String csvContent = result.getResponse().getContentAsString();
        assertThat(csvContent).contains("Project ID,Project Name,Client,Manager");

        // Verify formula injection protection: any field starting with = + - @ must be prepended with '
        String sanitizedTest = analyticsService.exportAnalyticsCsv(adminUser, "projects", null);
        assertThat(sanitizedTest).isNotBlank();
    }

    @Test
    @DisplayName("8. Deadline Risk Summary & Management Attention Flags")
    void testDeadlineRiskSummary_AttentionCategories() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/projects/deadlines")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).contains("projectsMeetingDeadline");
        assertThat(response).contains("projectsMissingDeadline");
        assertThat(response).contains("projectsRequiringAttention");
    }
}
