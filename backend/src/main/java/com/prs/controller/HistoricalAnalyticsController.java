package com.prs.controller;

import com.prs.dto.CompleteTaskRequest;
import com.prs.dto.HistoricalOutcomesDto;
import com.prs.dto.TaskDelayRiskDto;
import com.prs.entity.TaskOutcomeRecord;
import com.prs.service.HistoricalAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor

public class HistoricalAnalyticsController {

    private final HistoricalAnalyticsService analyticsService;

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    public ResponseEntity<TaskOutcomeRecord> completeTask(
            @PathVariable Long taskId,
            @Valid @RequestBody CompleteTaskRequest request) {
        TaskOutcomeRecord outcome = analyticsService.completeTask(taskId, request);
        return ResponseEntity.ok(outcome);
    }

    @GetMapping("/analytics/historical-outcomes")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'WORKER', 'CLIENT')")
    public ResponseEntity<HistoricalOutcomesDto> getHistoricalOutcomes(
            @RequestParam(required = false) Long projectId) {
        HistoricalOutcomesDto outcomes = analyticsService.getHistoricalOutcomes(projectId);
        return ResponseEntity.ok(outcomes);
    }

    @GetMapping("/projects/{projectId}/delay-risk")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'WORKER', 'CLIENT')")
    public ResponseEntity<List<TaskDelayRiskDto>> getProjectDelayRisk(
            @PathVariable Long projectId) {
        List<TaskDelayRiskDto> riskList = analyticsService.getProjectDelayRisk(projectId);
        return ResponseEntity.ok(riskList);
    }
}
