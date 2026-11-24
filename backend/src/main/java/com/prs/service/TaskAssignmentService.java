package com.prs.service;

import com.prs.dto.TaskAssignmentDto;
import com.prs.entity.AssignmentType;
import com.prs.entity.Task;
import com.prs.entity.TaskAssignment;
import com.prs.entity.Worker;
import com.prs.entity.Vendor;
import com.prs.repository.TaskAssignmentRepository;
import com.prs.repository.TaskRepository;
import com.prs.repository.WorkerRepository;
import com.prs.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskAssignmentService {

    private final TaskAssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final VendorRepository vendorRepository;
    private final NotificationService notificationService;

    public List<TaskAssignmentDto> getAssignments(Long taskId) {
        return assignmentRepository.findByTaskId(taskId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public TaskAssignmentDto addAssignment(Long taskId, TaskAssignmentDto dto) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setAssignmentType(dto.getAssignmentType());

        if (dto.getAssignmentType() == AssignmentType.WORKER) {
            if (dto.getWorkerId() == null) throw new IllegalArgumentException("Worker ID is required");
            Worker worker = workerRepository.findById(dto.getWorkerId())
                    .orElseThrow(() -> new IllegalArgumentException("Worker not found"));
            
            if (task.getProject() != null && task.getProject().getOrganization() != null
                    && worker.getOrganization() != null
                    && !worker.getOrganization().getId().equals(task.getProject().getOrganization().getId())) {
                throw new IllegalArgumentException("Cannot assign worker belonging to another organization.");
            }

            assignment.setWorker(worker);

            if (worker.getUser() != null) {
                notificationService.sendNotification(
                        worker.getUser(),
                        com.prs.entity.NotificationEventType.TASK_ASSIGNMENT,
                        "New Task Assignment: " + task.getName(),
                        String.format("You have been assigned to task '%s' in project '%s'.",
                                task.getName(), task.getProject().getName()),
                        task.getProject().getId(),
                        task.getId()
                );
            }
        } else if (dto.getAssignmentType() == AssignmentType.VENDOR) {
            if (dto.getVendorId() == null) throw new IllegalArgumentException("Vendor ID is required");
            Vendor vendor = vendorRepository.findById(dto.getVendorId())
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

            if (task.getProject() != null && task.getProject().getOrganization() != null
                    && vendor.getOrganization() != null
                    && !vendor.getOrganization().getId().equals(task.getProject().getOrganization().getId())) {
                throw new IllegalArgumentException("Cannot assign vendor belonging to another organization.");
            }

            assignment.setVendor(vendor);
        } else {
            throw new IllegalArgumentException("Invalid assignment type");
        }

        if (dto.getAllocationPercentage() != null) {
            assignment.setAllocationPercentage(dto.getAllocationPercentage());
        }

        return mapToDto(assignmentRepository.save(assignment));
    }

    public void removeAssignment(Long assignmentId) {
        assignmentRepository.deleteById(assignmentId);
    }

    private TaskAssignmentDto mapToDto(TaskAssignment entity) {
        TaskAssignmentDto dto = new TaskAssignmentDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTask().getId());
        dto.setTaskName(entity.getTask().getName());
        dto.setAssignmentType(entity.getAssignmentType());
        dto.setAllocationPercentage(entity.getAllocationPercentage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getWorker() != null) {
            dto.setWorkerId(entity.getWorker().getId());
            dto.setWorkerName(entity.getWorker().getName());
        }
        if (entity.getVendor() != null) {
            dto.setVendorId(entity.getVendor().getId());
            dto.setVendorName(entity.getVendor().getName());
        }

        return dto;
    }
}
