package com.prs.controller;

import com.prs.dto.*;
import com.prs.entity.User;
import com.prs.repository.UserRepository;
import com.prs.service.PortfolioAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor

public class PortfolioAnalyticsController {

    private final PortfolioAnalyticsService analyticsService;
    private final UserRepository userRepository;

    @GetMapping("/portfolio")
    public ResponseEntity<PortfolioKpiSummaryDto> getPortfolioKpis(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long projectId) {

        User user = getUser(principal);
        AnalyticsFilter filter = buildFilter(startDate, endDate, status, priority, clientId, managerId, projectId);
        return ResponseEntity.ok(analyticsService.getPortfolioKpiSummary(user, filter));
    }

    @GetMapping("/projects/performance")
    public ResponseEntity<List<ProjectPerformanceDto>> getProjectsPerformance(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long projectId) {

        User user = getUser(principal);
        AnalyticsFilter filter = buildFilter(startDate, endDate, status, priority, clientId, managerId, projectId);
        return ResponseEntity.ok(analyticsService.getProjectsPerformance(user, filter));
    }

    @GetMapping("/projects/{projectId}/performance")
    public ResponseEntity<ProjectPerformanceDto> getSingleProjectPerformance(
            @PathVariable Long projectId,
            Principal principal) {

        User user = getUser(principal);
        AnalyticsFilter filter = AnalyticsFilter.builder().projectId(projectId).build();
        List<ProjectPerformanceDto> list = analyticsService.getProjectsPerformance(user, filter);
        if (list.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(list.get(0));
    }

    @GetMapping("/projects/deadlines")
    public ResponseEntity<PortfolioDeadlineRiskSummaryDto> getDeadlineRiskSummary(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long projectId) {

        User user = getUser(principal);
        AnalyticsFilter filter = buildFilter(startDate, endDate, status, priority, clientId, managerId, projectId);
        return ResponseEntity.ok(analyticsService.getDeadlineRiskSummary(user, filter));
    }

    @GetMapping("/resources/utilization")
    public ResponseEntity<ResourceUtilizationSummaryDto> getResourceUtilization(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        User user = getUser(principal);
        AnalyticsFilter filter = AnalyticsFilter.builder().startDate(startDate).endDate(endDate).build();
        return ResponseEntity.ok(analyticsService.getResourceUtilization(user, filter));
    }

    @GetMapping("/vendors/performance")
    public ResponseEntity<VendorAnalyticsSummaryDto> getVendorPerformance(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        User user = getUser(principal);
        AnalyticsFilter filter = AnalyticsFilter.builder().startDate(startDate).endDate(endDate).build();
        return ResponseEntity.ok(analyticsService.getVendorPerformance(user, filter));
    }

    @GetMapping("/trends")
    public ResponseEntity<AnalyticsTrendsDto> getTrends(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long projectId) {

        User user = getUser(principal);
        AnalyticsFilter filter = buildFilter(startDate, endDate, status, priority, clientId, managerId, projectId);
        return ResponseEntity.ok(analyticsService.getAnalyticsTrends(user, filter));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportAnalyticsCsv(
            Principal principal,
            @RequestParam(defaultValue = "portfolio") String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long projectId) {

        User user = getUser(principal);
        AnalyticsFilter filter = buildFilter(startDate, endDate, status, priority, clientId, managerId, projectId);
        String csv = analyticsService.exportAnalyticsCsv(user, reportType, filter);

        String filename = "analytics_" + reportType + "_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    private User getUser(Principal principal) {
        if (principal == null) return null;
        return userRepository.findByUsername(principal.getName()).orElse(null);
    }

    private AnalyticsFilter buildFilter(LocalDate startDate, LocalDate endDate, String status, String priority,
                                        Long clientId, Long managerId, Long projectId) {
        return AnalyticsFilter.builder()
                .startDate(startDate)
                .endDate(endDate)
                .status(status)
                .priority(priority)
                .clientId(clientId)
                .managerId(managerId)
                .projectId(projectId)
                .build();
    }
}
