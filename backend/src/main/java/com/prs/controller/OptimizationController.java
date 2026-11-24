package com.prs.controller;

import com.prs.dto.*;
import com.prs.optimization.ScheduleOptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor

public class OptimizationController {

    private final ScheduleOptimizationService optimizationService;

    // 1. Start an optimization run
    @PostMapping("/api/projects/{projectId}/optimization/runs")
    public ResponseEntity<OptimizationRunDto> startOptimizationRun(
            @PathVariable Long projectId,
            @RequestBody(required = false) StartOptimizationRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ProjectManager";
        String objective = request != null && request.getSelectedObjective() != null
                ? request.getSelectedObjective()
                : "BALANCED";

        OptimizationRunDto run = optimizationService.runOptimization(projectId, username, objective);
        return ResponseEntity.ok(run);
    }

    // 2. List optimization runs for project
    @GetMapping("/api/projects/{projectId}/optimization/runs")
    public ResponseEntity<List<OptimizationRunDto>> listOptimizationRuns(@PathVariable Long projectId) {
        return ResponseEntity.ok(optimizationService.listRuns(projectId));
    }

    // 3. Get run details
    @GetMapping("/api/optimization/runs/{runId}")
    public ResponseEntity<OptimizationRunDto> getOptimizationRun(@PathVariable Long runId) {
        return ResponseEntity.ok(optimizationService.getRun(runId));
    }

    // 4. Get ranked alternatives for a run
    @GetMapping("/api/optimization/runs/{runId}/alternatives")
    public ResponseEntity<List<ScheduleAlternativeDto>> getAlternatives(@PathVariable Long runId) {
        return ResponseEntity.ok(optimizationService.getAlternatives(runId));
    }

    // 5. List recommendations for project
    @GetMapping("/api/projects/{projectId}/recommendations")
    public ResponseEntity<List<OptimizationRecommendationDto>> listRecommendations(@PathVariable Long projectId) {
        return ResponseEntity.ok(optimizationService.listRecommendations(projectId));
    }

    // 6. Get resource recommendations for a task
    @GetMapping("/api/tasks/{taskId}/resource-recommendations")
    public ResponseEntity<TaskResourceRecommendationsResponse> getTaskResourceRecommendations(@PathVariable Long taskId) {
        return ResponseEntity.ok(optimizationService.getTaskResourceRecommendations(taskId));
    }

    // 7. Approve recommendation
    @PostMapping("/api/recommendations/{recommendationId}/approve")
    public ResponseEntity<OptimizationRecommendationDto> approveRecommendation(
            @PathVariable Long recommendationId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "Manager";
        return ResponseEntity.ok(optimizationService.approveRecommendation(recommendationId, username));
    }

    // 8. Reject recommendation
    @PostMapping("/api/recommendations/{recommendationId}/reject")
    public ResponseEntity<OptimizationRecommendationDto> rejectRecommendation(
            @PathVariable Long recommendationId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "Manager";
        return ResponseEntity.ok(optimizationService.rejectRecommendation(recommendationId, username));
    }

    // 9. Apply recommendation
    @PostMapping("/api/recommendations/{recommendationId}/apply")
    public ResponseEntity<OptimizationRecommendationDto> applyRecommendation(
            @PathVariable Long recommendationId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "Manager";
        return ResponseEntity.ok(optimizationService.applyRecommendation(recommendationId, username));
    }

    // 10. Audit logs for project
    @GetMapping("/api/projects/{projectId}/optimization/audit-logs")
    public ResponseEntity<List<OptimizationAuditLogDto>> getOptimizationAuditLogs(@PathVariable Long projectId) {
        return ResponseEntity.ok(optimizationService.listAuditLogs(projectId));
    }
}
