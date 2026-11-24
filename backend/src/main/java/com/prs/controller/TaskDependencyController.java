package com.prs.controller;

import com.prs.dto.TaskDependencyDto;
import com.prs.service.TaskDependencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor

public class TaskDependencyController {

    private final TaskDependencyService service;

    @GetMapping("/tasks/{taskId}/dependencies")
    public ResponseEntity<List<TaskDependencyDto>> getDependencies(@PathVariable Long taskId) {
        return ResponseEntity.ok(service.getDependencies(taskId));
    }

    @GetMapping("/tasks/{taskId}/dependency-details")
    public ResponseEntity<com.prs.dto.TaskDependencyDetailsDto> getDependencyDetails(@PathVariable Long taskId) {
        return ResponseEntity.ok(service.getDependencyDetails(taskId));
    }

    @PostMapping("/tasks/{taskId}/dependencies")
    public ResponseEntity<?> addDependency(@PathVariable Long taskId, @RequestBody TaskDependencyDto dto) {
        try {
            return ResponseEntity.ok(service.addDependency(taskId, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    public ResponseEntity<Void> removeDependency(@PathVariable Long dependencyId) {
        service.removeDependency(dependencyId);
        return ResponseEntity.ok().build();
    }
}
