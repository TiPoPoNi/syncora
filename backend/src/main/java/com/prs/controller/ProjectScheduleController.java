package com.prs.controller;

import com.prs.baseline.ProjectBaselineService;
import com.prs.dto.*;
import com.prs.scheduler.ResourceConflict;
import com.prs.service.ProjectScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor

public class ProjectScheduleController {

    private final ProjectScheduleService scheduleService;
    private final ProjectBaselineService baselineService;

    @GetMapping("/schedule")
    public ResponseEntity<ProjectScheduleDto> getSchedule(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getSchedule(projectId));
    }

    @PostMapping("/schedule/recalculate")
    public ResponseEntity<ProjectScheduleDto> recalculateSchedule(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.recalculateSchedule(projectId));
    }

    @GetMapping("/conflicts")
    public ResponseEntity<List<ResourceConflict>> getConflicts(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getSchedule(projectId).getConflicts());
    }

    @GetMapping("/critical-path")
    public ResponseEntity<CriticalPathDto> getCriticalPath(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getCriticalPath(projectId));
    }

    @GetMapping("/schedule-analysis")
    public ResponseEntity<ScheduleAnalysisDto> getScheduleAnalysis(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getScheduleAnalysis(projectId));
    }

    @GetMapping("/gantt")
    public ResponseEntity<GanttChartDto> getGanttData(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getGanttData(projectId));
    }

    @GetMapping("/delay-impact")
    public ResponseEntity<DelayImpactDto> getDelayImpact(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getDelayImpact(projectId));
    }

    @GetMapping("/baseline")
    public ResponseEntity<ProjectBaselineDto> getBaseline(@PathVariable Long projectId) {
        return baselineService.getLatestBaseline(projectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/baseline")
    public ResponseEntity<ProjectBaselineDto> createBaseline(
            @PathVariable Long projectId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String versionName = (body != null) ? body.get("versionName") : null;
        String username = authentication != null ? authentication.getName() : "admin";
        ProjectBaselineDto baseline = baselineService.createBaseline(projectId, versionName, username);
        return ResponseEntity.ok(baseline);
    }
}
