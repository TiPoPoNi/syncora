package com.prs.controller;

import com.prs.dto.WorkerDto;
import com.prs.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor

public class WorkerController {
    private final WorkerService workerService;

    @GetMapping
    public List<WorkerDto> getAllWorkers() {
        return workerService.getAllWorkers();
    }

    @GetMapping("/{id}")
    public WorkerDto getWorkerById(@PathVariable Long id) {
        return workerService.getWorkerById(id);
    }

    @PostMapping
    public WorkerDto createWorker(@RequestBody WorkerDto workerDto) {
        return workerService.createWorker(workerDto);
    }

    @PutMapping("/{id}")
    public WorkerDto updateWorker(@PathVariable Long id, @RequestBody WorkerDto workerDto) {
        return workerService.updateWorker(id, workerDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorker(@PathVariable Long id) {
        workerService.deleteWorker(id);
        return ResponseEntity.ok().build();
    }
}
