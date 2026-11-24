package com.prs.service;

import com.prs.dto.TaskDependencyDetailsDto;
import com.prs.dto.TaskDependencyDto;
import com.prs.entity.DeliverableStatus;
import com.prs.entity.Task;
import com.prs.entity.TaskApprovalGate;
import com.prs.entity.TaskDependency;
import com.prs.entity.TaskStatus;
import com.prs.graph.DependencyGraph;
import com.prs.repository.TaskApprovalGateRepository;
import com.prs.repository.TaskDependencyRepository;
import com.prs.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;
    private final TaskApprovalGateRepository taskGateRepository;
    private final ProjectScheduleService projectScheduleService;

    public List<TaskDependencyDto> getDependencies(Long taskId) {
        // Return dependencies where the task is the successor
        return dependencyRepository.findBySuccessorTaskId(taskId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskDependencyDetailsDto getDependencyDetails(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        TaskDependencyDetailsDto details = new TaskDependencyDetailsDto();
        details.setTaskId(task.getId());
        details.setTaskName(task.getName());
        if (task.getProject() != null) {
            details.setProjectId(task.getProject().getId());
            details.setProjectName(task.getProject().getName());
        }
        details.setStatus(task.getStatus());
        details.setProgress(task.getProgress());

        List<String> blockingReasons = new ArrayList<>();

        // Predecessors (tasks that this task depends on)
        List<TaskDependency> predDeps = dependencyRepository.findBySuccessorTaskId(taskId);
        for (TaskDependency dep : predDeps) {
            Task pred = dep.getPredecessorTask();
            TaskDependencyDetailsDto.TaskDependencyItemDto item = new TaskDependencyDetailsDto.TaskDependencyItemDto();
            item.setDependencyId(dep.getId());
            item.setTaskId(pred.getId());
            item.setTaskName(pred.getName());
            item.setStatus(pred.getStatus());
            item.setProgress(pred.getProgress());
            item.setEstimatedDuration(pred.getEstimatedDuration());
            item.setPlannedStartDate(pred.getPlannedStartDate());
            item.setPlannedFinishDate(pred.getPlannedFinishDate());
            item.setDependencyType(dep.getDependencyType());

            boolean isBlocking = pred.getStatus() != TaskStatus.COMPLETED;
            item.setBlocking(isBlocking);
            if (isBlocking) {
                blockingReasons.add(String.format("Predecessor '%s' is not completed (Current: %s, %d%%).",
                        pred.getName(), pred.getStatus(), pred.getProgress() != null ? pred.getProgress() : 0));
            }
            details.getPredecessors().add(item);
        }

        // Successors (tasks that depend on this task)
        List<TaskDependency> succDeps = dependencyRepository.findByPredecessorTaskId(taskId);
        for (TaskDependency dep : succDeps) {
            Task succ = dep.getSuccessorTask();
            TaskDependencyDetailsDto.TaskDependencyItemDto item = new TaskDependencyDetailsDto.TaskDependencyItemDto();
            item.setDependencyId(dep.getId());
            item.setTaskId(succ.getId());
            item.setTaskName(succ.getName());
            item.setStatus(succ.getStatus());
            item.setProgress(succ.getProgress());
            item.setEstimatedDuration(succ.getEstimatedDuration());
            item.setPlannedStartDate(succ.getPlannedStartDate());
            item.setPlannedFinishDate(succ.getPlannedFinishDate());
            item.setDependencyType(dep.getDependencyType());
            details.getSuccessors().add(item);
        }

        // Approval gates
        List<TaskApprovalGate> gates = taskGateRepository.findByTaskId(taskId);
        for (TaskApprovalGate gate : gates) {
            TaskDependencyDetailsDto.TaskGateItemDto gateItem = new TaskDependencyDetailsDto.TaskGateItemDto();
            gateItem.setGateId(gate.getId());
            gateItem.setDeliverableId(gate.getDeliverable().getId());
            gateItem.setDeliverableName(gate.getDeliverable().getName());
            gateItem.setDeliverableStatus(gate.getDeliverable().getStatus().name());
            gateItem.setGateType(gate.getGateType() != null ? gate.getGateType().name() : "COMPLETION_GATE");
            gateItem.setIsMandatory(gate.getIsMandatory());

            boolean isSatisfied = gate.getDeliverable().getStatus() == DeliverableStatus.APPROVED;
            gateItem.setSatisfied(isSatisfied);

            if (Boolean.TRUE.equals(gate.getIsMandatory()) && !isSatisfied) {
                blockingReasons.add(String.format("Mandatory approval gate '%s' is %s (Requires sign-off before completion).",
                        gate.getDeliverable().getName(), gate.getDeliverable().getStatus()));
            }
            details.getApprovalGates().add(gateItem);
        }

        boolean isBlocked = !blockingReasons.isEmpty() || task.getStatus() == TaskStatus.BLOCKED;
        details.setBlocked(isBlocked);
        details.setBlockingReasons(blockingReasons);

        return details;
    }

    @Transactional
    public TaskDependencyDto addDependency(Long taskId, TaskDependencyDto dto) {
        if (taskId.equals(dto.getPredecessorTaskId())) {
            throw new IllegalArgumentException("Task cannot depend on itself");
        }

        Task successor = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Successor task not found"));
        Task predecessor = taskRepository.findById(dto.getPredecessorTaskId())
                .orElseThrow(() -> new IllegalArgumentException("Predecessor task not found"));
        
        if (successor.getProject() == null || predecessor.getProject() == null
                || !successor.getProject().getId().equals(predecessor.getProject().getId())) {
            throw new IllegalArgumentException("Tasks must belong to the same project");
        }

        if (dependencyRepository.existsByPredecessorTaskIdAndSuccessorTaskId(predecessor.getId(), successor.getId())) {
            throw new IllegalArgumentException("Dependency already exists");
        }

        // Cycle detection before adding
        List<Task> projectTasks = taskRepository.findByProjectId(successor.getProject().getId());
        List<TaskDependency> allDependencies = dependencyRepository.findBySuccessorTaskProjectId(successor.getProject().getId());
        
        DependencyGraph graph = new DependencyGraph();
        graph.buildGraph(projectTasks, allDependencies);
        
        if (graph.wouldCreateCycle(predecessor.getId(), successor.getId())) {
            throw new IllegalArgumentException("Adding this dependency would create a circular dependency");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setSuccessorTask(successor);
        dependency.setPredecessorTask(predecessor);
        if (dto.getDependencyType() != null) {
            dependency.setDependencyType(dto.getDependencyType());
        }

        TaskDependency saved = dependencyRepository.save(dependency);

        // Recalculate schedule if project is set
        try {
            projectScheduleService.recalculateSchedule(successor.getProject().getId());
        } catch (Exception e) {
            log.warn("Could not recalculate schedule after adding dependency: {}", e.getMessage());
        }

        return mapToDto(saved);
    }

    @Transactional
    public void removeDependency(Long dependencyId) {
        TaskDependency dep = dependencyRepository.findById(dependencyId).orElse(null);
        if (dep != null) {
            Long projectId = dep.getSuccessorTask() != null && dep.getSuccessorTask().getProject() != null
                    ? dep.getSuccessorTask().getProject().getId() : null;
            dependencyRepository.deleteById(dependencyId);
            if (projectId != null) {
                try {
                    projectScheduleService.recalculateSchedule(projectId);
                } catch (Exception e) {
                    log.warn("Could not recalculate schedule after removing dependency: {}", e.getMessage());
                }
            }
        }
    }

    private TaskDependencyDto mapToDto(TaskDependency entity) {
        TaskDependencyDto dto = new TaskDependencyDto();
        dto.setId(entity.getId());
        dto.setPredecessorTaskId(entity.getPredecessorTask().getId());
        dto.setPredecessorTaskName(entity.getPredecessorTask().getName());
        dto.setSuccessorTaskId(entity.getSuccessorTask().getId());
        dto.setSuccessorTaskName(entity.getSuccessorTask().getName());
        dto.setDependencyType(entity.getDependencyType());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
