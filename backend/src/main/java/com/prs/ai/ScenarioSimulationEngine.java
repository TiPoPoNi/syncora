package com.prs.ai;

import com.prs.cpm.CriticalPathService;
import com.prs.dto.ScenarioSimulationRequestDto;
import com.prs.dto.ScenarioSimulationResponseDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.SchedulingEngine;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioSimulationEngine {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final WorkerRepository workerRepository;
    private final VendorRepository vendorRepository;
    private final SchedulingEngine schedulingEngine;
    private final ConflictDetector conflictDetector;
    private final WorkingDayCalculator workingDayCalculator;
    private final CriticalPathService criticalPathService;

    public ScenarioSimulationResponseDto simulate(Long projectId, ScenarioSimulationRequestDto request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<Task> originalTasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = taskDependencyRepository.findBySuccessorTaskProjectId(projectId);
        List<TaskAssignment> projectAssignments = taskAssignmentRepository.findByTaskProjectId(projectId);
        List<TaskAssignment> allSystemAssignments = taskAssignmentRepository.findAll();

        Task targetTask;
        if (request.getTaskId() != null) {
            targetTask = originalTasks.stream()
                    .filter(t -> t.getId().equals(request.getTaskId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Target task not found: " + request.getTaskId()));
        } else if (!originalTasks.isEmpty()) {
            targetTask = originalTasks.get(0);
        } else {
            throw new IllegalArgumentException("No tasks available in project to simulate delay on.");
        }

        LocalDate originalForecast = project.getExpectedCompletionDate();
        if (originalForecast == null) {
            schedulingEngine.calculateSchedule(project, originalTasks, dependencies);
            originalForecast = project.getExpectedCompletionDate();
        }

        // Detect initial conflicts
        List<Task> allGlobalTasks = allSystemAssignments.stream()
                .map(TaskAssignment::getTask)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        int initialConflictsCount = conflictDetector.detectConflicts(allGlobalTasks, allSystemAssignments).size();

        // 1. Clone tasks for in-memory simulation (DO NOT PERSIST)
        Map<Long, Task> simTaskMap = new HashMap<>();
        List<Task> simTasks = new ArrayList<>();
        for (Task t : originalTasks) {
            Task clone = new Task();
            clone.setId(t.getId());
            clone.setName(t.getName());
            clone.setEstimatedDuration(t.getEstimatedDuration());
            clone.setPlannedStartDate(t.getPlannedStartDate());
            clone.setPlannedFinishDate(t.getPlannedFinishDate());
            clone.setStatus(t.getStatus());
            clone.setPriority(t.getPriority());
            clone.setProject(project);
            simTaskMap.put(clone.getId(), clone);
            simTasks.add(clone);
        }

        // Apply hypothetical changes to sim tasks
        Task simTarget = simTaskMap.get(targetTask.getId());
        if ("TASK_DELAY".equalsIgnoreCase(request.getScenarioType())) {
            int extraDays = request.getAdditionalDelayDays() != null ? request.getAdditionalDelayDays() : 1;
            int newDuration = (simTarget.getEstimatedDuration() != null ? simTarget.getEstimatedDuration() : 1) + extraDays;
            simTarget.setEstimatedDuration(newDuration);
        }

        // 2. Clone dependencies with simulated tasks
        List<TaskDependency> simDeps = new ArrayList<>();
        for (TaskDependency d : dependencies) {
            Task pred = simTaskMap.get(d.getPredecessorTask().getId());
            Task succ = simTaskMap.get(d.getSuccessorTask().getId());
            if (pred != null && succ != null) {
                TaskDependency clone = new TaskDependency();
                clone.setId(d.getId());
                clone.setPredecessorTask(pred);
                clone.setSuccessorTask(succ);
                clone.setDependencyType(d.getDependencyType());
                simDeps.add(clone);
            }
        }

        // 3. Recalculate schedule via authoritative SchedulingEngine
        Project simProject = new Project();
        simProject.setId(project.getId());
        simProject.setName(project.getName());
        simProject.setStartDate(project.getStartDate());
        simProject.setDeadline(project.getDeadline());

        schedulingEngine.calculateSchedule(simProject, simTasks, simDeps);

        LocalDate simulatedForecast = simProject.getExpectedCompletionDate();

        // Calculate working days difference
        int diffWorkingDays = 0;
        if (simulatedForecast.isAfter(originalForecast)) {
            diffWorkingDays = workingDayCalculator.calculateWorkingDaysBetween(originalForecast, simulatedForecast);
        } else if (simulatedForecast.isBefore(originalForecast)) {
            diffWorkingDays = -workingDayCalculator.calculateWorkingDaysBetween(simulatedForecast, originalForecast);
        }

        // Evaluate affected tasks
        List<Map<String, Object>> affectedTasks = new ArrayList<>();
        for (Task orig : originalTasks) {
            Task sim = simTaskMap.get(orig.getId());
            if (sim != null && orig.getPlannedFinishDate() != null && sim.getPlannedFinishDate() != null) {
                if (!orig.getPlannedFinishDate().equals(sim.getPlannedFinishDate())) {
                    int shift = workingDayCalculator.calculateWorkingDaysBetween(orig.getPlannedFinishDate(), sim.getPlannedFinishDate());
                    Map<String, Object> af = new HashMap<>();
                    af.put("taskId", orig.getId());
                    af.put("taskName", orig.getName());
                    af.put("previousPlannedFinish", orig.getPlannedFinishDate().toString());
                    af.put("newPlannedFinish", sim.getPlannedFinishDate().toString());
                    af.put("workingDaysShift", shift);
                    af.put("isCritical", false);
                    affectedTasks.add(af);
                }
            }
        }

        // Evaluate deadline statuses
        String origStatus = "ON_TRACK";
        String simStatus = "ON_TRACK";
        if (project.getDeadline() != null) {
            origStatus = originalForecast.isAfter(project.getDeadline()) ? "DELAYED" : "ON_TRACK";
            simStatus = simulatedForecast.isAfter(project.getDeadline()) ? "DELAYED" : "ON_TRACK";
        }

        ScenarioSimulationResponseDto resp = new ScenarioSimulationResponseDto();
        resp.setScenarioType(request.getScenarioType());
        resp.setTargetTaskId(targetTask.getId());
        resp.setTargetTaskName(targetTask.getName());
        resp.setOriginalForecast(originalForecast.toString());
        resp.setSimulatedForecast(simulatedForecast.toString());
        resp.setWorkingDaysDifference(diffWorkingDays);
        resp.setOriginalDeadlineStatus(origStatus);
        resp.setSimulatedDeadlineStatus(simStatus);
        resp.setClientDeadline(project.getDeadline() != null ? project.getDeadline().toString() : "Not set");
        resp.setAffectedTasks(affectedTasks);
        resp.setConflictsResolvedCount(0);
        resp.setNewConflictsCount(0);
        resp.setHypothetical(true);

        return resp;
    }
}
