package com.prs.service;

import com.prs.dto.TaskDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final WorkerRepository workerRepository;
    private final DeliverableManagementService deliverableManagementService;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskApprovalGateRepository taskGateRepository;
    private final ProjectScheduleService projectScheduleService;

    @Transactional(readOnly = true)
    public List<TaskDto> getAllTasks() {
        Long tenantId = TenantContext.getTenantId();
        List<Task> tasks = taskRepository.findAll();
        if (tenantId != null) {
            tasks = tasks.stream()
                    .filter(t -> t.getProject() != null && t.getProject().getOrganization() != null 
                            && t.getProject().getOrganization().getId().equals(tenantId))
                    .collect(Collectors.toList());
        }
        return tasks.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDto getTaskById(Long id) {
        Task task = taskRepository.findById(id).orElseThrow();
        validateTenantAccess(task);
        return mapToDto(task);
    }

    @Transactional
    public TaskDto createTask(TaskDto taskDto) {
        if (taskDto.getName() == null || taskDto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Task name is required.");
        }

        Task task = new Task();
        task.setName(taskDto.getName().trim());
        task.setDescription(taskDto.getDescription() != null ? taskDto.getDescription().trim() : null);

        int duration = (taskDto.getEstimatedDuration() != null && taskDto.getEstimatedDuration() >= 1)
                ? taskDto.getEstimatedDuration() : 1;
        task.setEstimatedDuration(duration);

        task.setPriority(taskDto.getPriority() != null ? taskDto.getPriority() : Priority.MEDIUM);

        // Date validation
        LocalDate startDate = taskDto.getStartDate();
        LocalDate deadline = taskDto.getDeadline();
        if (startDate != null && deadline != null && deadline.isBefore(startDate)) {
            throw new IllegalArgumentException("Task deadline cannot be earlier than the start date.");
        }
        task.setStartDate(startDate);
        task.setDeadline(deadline);

        // Status & progress consistency
        normalizeStatusAndProgress(task, taskDto.getStatus(), taskDto.getProgress(), false);

        if (taskDto.getProjectId() != null) {
            Project project = projectRepository.findById(taskDto.getProjectId()).orElseThrow(
                    () -> new IllegalArgumentException("Project not found: " + taskDto.getProjectId())
            );
            validateProjectTenantAccess(project);
            task.setProject(project);
        }

        Task savedTask = taskRepository.save(task);

        // Worker assignment
        handleWorkerAssignment(savedTask, taskDto.getAssignedWorkerId());

        // Recalculate CPM schedule
        if (savedTask.getProject() != null) {
            try {
                projectScheduleService.recalculateSchedule(savedTask.getProject().getId());
                savedTask = taskRepository.findById(savedTask.getId()).orElse(savedTask);
            } catch (Exception e) {
                log.warn("Could not recalculate schedule on task create: {}", e.getMessage());
            }
        }

        return mapToDto(savedTask);
    }

    @Transactional
    public TaskDto updateTask(Long id, TaskDto taskDto) {
        Task task = taskRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Task not found: " + id)
        );
        validateTenantAccess(task);

        if (taskDto.getName() != null && !taskDto.getName().trim().isEmpty()) {
            task.setName(taskDto.getName().trim());
        }
        if (taskDto.getDescription() != null) {
            task.setDescription(taskDto.getDescription().trim());
        }
        if (taskDto.getPriority() != null) {
            task.setPriority(taskDto.getPriority());
        }
        if (taskDto.getEstimatedDuration() != null) {
            if (taskDto.getEstimatedDuration() < 1) {
                throw new IllegalArgumentException("Estimated duration must be at least 1 working day.");
            }
            task.setEstimatedDuration(taskDto.getEstimatedDuration());
        }

        // Dates
        LocalDate newStart = taskDto.getStartDate() != null ? taskDto.getStartDate() : task.getStartDate();
        LocalDate newDeadline = taskDto.getDeadline() != null ? taskDto.getDeadline() : task.getDeadline();
        if (newStart != null && newDeadline != null && newDeadline.isBefore(newStart)) {
            throw new IllegalArgumentException("Task deadline cannot be earlier than the start date.");
        }
        if (taskDto.getStartDate() != null) {
            task.setStartDate(taskDto.getStartDate());
        }
        if (taskDto.getDeadline() != null) {
            task.setDeadline(taskDto.getDeadline());
        }

        // Status & progress
        normalizeStatusAndProgress(task, taskDto.getStatus(), taskDto.getProgress(), true);

        // Project change
        if (taskDto.getProjectId() != null && (task.getProject() == null || !taskDto.getProjectId().equals(task.getProject().getId()))) {
            Project project = projectRepository.findById(taskDto.getProjectId()).orElseThrow(
                    () -> new IllegalArgumentException("Project not found: " + taskDto.getProjectId())
            );
            validateProjectTenantAccess(project);
            task.setProject(project);
        }

        Task savedTask = taskRepository.save(task);

        // Worker assignment (preserves if null, unassigns if <= 0)
        handleWorkerAssignment(savedTask, taskDto.getAssignedWorkerId());

        // Recalculate schedule
        if (savedTask.getProject() != null) {
            try {
                projectScheduleService.recalculateSchedule(savedTask.getProject().getId());
                savedTask = taskRepository.findById(savedTask.getId()).orElse(savedTask);
            } catch (Exception e) {
                log.warn("Could not recalculate schedule on task update: {}", e.getMessage());
            }
        }

        return mapToDto(savedTask);
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Task not found: " + id)
        );
        validateTenantAccess(task);

        Long projectId = task.getProject() != null ? task.getProject().getId() : null;

        // Clean up dependent records
        taskAssignmentRepository.deleteAll(taskAssignmentRepository.findByTaskId(id));
        taskDependencyRepository.deleteAll(taskDependencyRepository.findByPredecessorTaskId(id));
        taskDependencyRepository.deleteAll(taskDependencyRepository.findBySuccessorTaskId(id));
        taskGateRepository.deleteAll(taskGateRepository.findByTaskId(id));

        taskRepository.deleteById(id);

        if (projectId != null) {
            try {
                projectScheduleService.recalculateSchedule(projectId);
            } catch (Exception e) {
                log.warn("Could not recalculate schedule on task delete: {}", e.getMessage());
            }
        }
    }

    private void normalizeStatusAndProgress(Task task, TaskStatus requestedStatus, Integer requestedProgress, boolean isUpdate) {
        TaskStatus currentStatus = task.getStatus();
        int currentProgress = task.getProgress() != null ? task.getProgress() : 0;

        TaskStatus targetStatus = requestedStatus != null ? requestedStatus : currentStatus;
        Integer targetProgress = requestedProgress != null ? requestedProgress : (isUpdate ? currentProgress : 0);

        if (targetProgress != null) {
            targetProgress = Math.max(0, Math.min(100, targetProgress));
        }

        // Rule 1: Completion requires 100% progress, stage gate satisfaction, and predecessor completion
        if (targetStatus == TaskStatus.COMPLETED || (targetProgress != null && targetProgress == 100)) {
            // Stage gate completion check
            deliverableManagementService.validateTaskCanBeCompleted(task);

            // Predecessor tasks completion check
            if (task.getId() != null) {
                List<TaskDependency> predecessors = taskDependencyRepository.findBySuccessorTaskId(task.getId());
                for (TaskDependency dep : predecessors) {
                    Task pred = dep.getPredecessorTask();
                    if (pred != null && pred.getStatus() != TaskStatus.COMPLETED) {
                        throw new IllegalStateException(String.format(
                                "Task '%s' cannot be marked COMPLETED because predecessor task '%s' is not completed (Current status: %s).",
                                task.getName(), pred.getName(), pred.getStatus()
                        ));
                    }
                }
            }

            task.setStatus(TaskStatus.COMPLETED);
            task.setProgress(100);
            return;
        }

        // Rule 2: TODO tasks must have 0% progress
        if (targetStatus == TaskStatus.TODO) {
            if (targetProgress != null && targetProgress > 0) {
                // If progress is greater than 0, transition to IN_PROGRESS (validating start gates)
                if (task.getId() != null) {
                    deliverableManagementService.validateTaskCanStart(task);
                }
                task.setStatus(TaskStatus.IN_PROGRESS);
                task.setProgress(targetProgress);
            } else {
                task.setStatus(TaskStatus.TODO);
                task.setProgress(0);
            }
            return;
        }

        // Rule 3: BLOCKED tasks retain blocked status
        if (targetStatus == TaskStatus.BLOCKED) {
            task.setStatus(TaskStatus.BLOCKED);
            task.setProgress(targetProgress != null ? targetProgress : currentProgress);
            return;
        }

        // Rule 4: IN_PROGRESS
        if (task.getId() != null && currentStatus == TaskStatus.TODO) {
            deliverableManagementService.validateTaskCanStart(task);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        if (targetProgress != null && targetProgress > 0 && targetProgress < 100) {
            task.setProgress(targetProgress);
        } else if (targetProgress == null || targetProgress == 0) {
            task.setProgress(10); // default starting progress for in-progress
        } else {
            task.setProgress(targetProgress);
        }
    }

    private void handleWorkerAssignment(Task task, Long assignedWorkerId) {
        if (assignedWorkerId == null) {
            // Null means keep existing assignment intact
            return;
        }

        List<TaskAssignment> existingAssignments = taskAssignmentRepository.findByTaskId(task.getId());

        if (assignedWorkerId <= 0) {
            // Explicit unassignment requested
            existingAssignments.stream()
                    .filter(a -> a.getAssignmentType() == AssignmentType.WORKER)
                    .forEach(taskAssignmentRepository::delete);
            return;
        }

        // Assign to new worker
        Worker worker = workerRepository.findById(assignedWorkerId).orElseThrow(
                () -> new IllegalArgumentException("Worker not found: " + assignedWorkerId)
        );

        if (task.getProject() != null && task.getProject().getOrganization() != null
                && worker.getOrganization() != null
                && !worker.getOrganization().getId().equals(task.getProject().getOrganization().getId())) {
            throw new IllegalArgumentException("Worker belongs to a different organization than the project.");
        }

        if (worker.getStatus() == WorkerStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("Cannot assign worker '" + worker.getName() + "' because they are currently unavailable.");
        }

        TaskAssignment assignment = existingAssignments.stream()
                .filter(a -> a.getAssignmentType() == AssignmentType.WORKER)
                .findFirst()
                .orElseGet(() -> {
                    TaskAssignment a = new TaskAssignment();
                    a.setTask(task);
                    a.setAssignmentType(AssignmentType.WORKER);
                    a.setAllocationPercentage(100);
                    return a;
                });

        assignment.setWorker(worker);
        taskAssignmentRepository.save(assignment);
    }

    private void validateTenantAccess(Task task) {
        if (task.getProject() != null) {
            validateProjectTenantAccess(task.getProject());
        }
    }

    private void validateProjectTenantAccess(Project project) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && project.getOrganization() != null && !project.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }
    }

    private TaskDto mapToDto(Task task) {
        TaskDto dto = new TaskDto();
        BeanUtils.copyProperties(task, dto);

        if (task.getProject() != null) {
            dto.setProjectId(task.getProject().getId());
            dto.setProjectName(task.getProject().getName());
        }

        // Worker assignment
        List<TaskAssignment> assignments = taskAssignmentRepository.findByTaskId(task.getId());
        Optional<TaskAssignment> workerAssignment = assignments.stream()
                .filter(a -> a.getAssignmentType() == AssignmentType.WORKER && a.getWorker() != null)
                .findFirst();

        if (workerAssignment.isPresent()) {
            Worker worker = workerAssignment.get().getWorker();
            dto.setAssignedWorkerId(worker.getId());
            dto.setAssignedWorkerName(worker.getName());
            dto.setAssignedWorkerSkills(worker.getSkills());
        }

        // Dependencies & Blocked status
        List<TaskDependency> predecessors = taskDependencyRepository.findBySuccessorTaskId(task.getId());
        List<TaskDependency> successors = taskDependencyRepository.findByPredecessorTaskId(task.getId());
        dto.setPredecessorCount(predecessors.size());
        dto.setSuccessorCount(successors.size());

        boolean blocked = false;
        String reason = null;

        for (TaskDependency dep : predecessors) {
            if (dep.getPredecessorTask() != null && dep.getPredecessorTask().getStatus() != TaskStatus.COMPLETED) {
                blocked = true;
                reason = "Predecessor task '" + dep.getPredecessorTask().getName() + "' is not completed (" + dep.getPredecessorTask().getStatus() + ").";
                break;
            }
        }

        if (!blocked) {
            List<TaskApprovalGate> gates = taskGateRepository.findByTaskId(task.getId());
            for (TaskApprovalGate gate : gates) {
                if (Boolean.TRUE.equals(gate.getIsMandatory())
                        && gate.getDeliverable() != null
                        && gate.getDeliverable().getStatus() != DeliverableStatus.APPROVED) {
                    blocked = true;
                    reason = "Mandatory approval gate deliverable '" + gate.getDeliverable().getName() + "' is " + gate.getDeliverable().getStatus() + ".";
                    break;
                }
            }
        }

        if (task.getStatus() == TaskStatus.BLOCKED) {
            blocked = true;
            if (reason == null) {
                reason = "Task marked as BLOCKED.";
            }
        }

        dto.setIsBlocked(blocked);
        dto.setBlockedReason(reason);

        return dto;
    }
}
