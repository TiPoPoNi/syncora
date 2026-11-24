package com.prs.controller;

import com.prs.dto.TaskAssignmentDto;
import com.prs.service.TaskAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor

public class TaskAssignmentController {

    private final TaskAssignmentService service;

    @GetMapping("/tasks/{taskId}/assignments")
    public ResponseEntity<List<TaskAssignmentDto>> getAssignments(@PathVariable Long taskId) {
        return ResponseEntity.ok(service.getAssignments(taskId));
    }

    @PostMapping("/tasks/{taskId}/assignments")
    public ResponseEntity<?> addAssignment(@PathVariable Long taskId, @RequestBody TaskAssignmentDto dto) {
        try {
            return ResponseEntity.ok(service.addAssignment(taskId, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> removeAssignment(@PathVariable Long assignmentId) {
        service.removeAssignment(assignmentId);
        return ResponseEntity.ok().build();
    }
}
