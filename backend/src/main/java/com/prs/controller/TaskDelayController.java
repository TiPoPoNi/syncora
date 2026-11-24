package com.prs.controller;

import com.prs.delay.DelayReportingService;
import com.prs.dto.ReportDelayRequest;
import com.prs.dto.TaskDelayDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/delays")
@RequiredArgsConstructor

public class TaskDelayController {

    private final DelayReportingService delayReportingService;

    @PostMapping
    public ResponseEntity<TaskDelayDto> reportDelay(
            @PathVariable Long taskId,
            @Valid @RequestBody ReportDelayRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        TaskDelayDto delay = delayReportingService.reportDelay(taskId, request, username);
        return ResponseEntity.ok(delay);
    }

    @GetMapping
    public ResponseEntity<List<TaskDelayDto>> getDelays(@PathVariable Long taskId) {
        return ResponseEntity.ok(delayReportingService.getDelaysForTask(taskId));
    }
}
