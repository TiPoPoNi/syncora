package com.prs.ai;

import com.prs.baseline.ProjectBaselineService;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.cpm.CpmTaskResult;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectContextBuilder {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskDelayRepository taskDelayRepository;
    private final OptimizationRecommendationRepository recommendationRepository;
    private final ProjectBaselineService baselineService;
    private final CriticalPathService criticalPathService;
    private final ConflictDetector conflictDetector;
    private final WorkingDayCalculator workingDayCalculator;

    @Transactional(readOnly = true)
    public Map<String, Object> buildProjectContext(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = taskDependencyRepository.findBySuccessorTaskProjectId(projectId);
        List<TaskAssignment> projectAssignments = taskAssignmentRepository.findByTaskProjectId(projectId);
        List<TaskAssignment> allSystemAssignments = taskAssignmentRepository.findAll();
        List<TaskDelay> delays = taskDelayRepository.findByProjectId(projectId);
        List<OptimizationRecommendation> recommendations = recommendationRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        // CPM Result
        CriticalPathResult cpmResult = null;
        try {
            cpmResult = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);
        } catch (Exception e) {
            log.warn("CPM calculation error in context builder: {}", e.getMessage());
        }

        Map<Long, CpmTaskResult> cpmTaskMap = new HashMap<>();
        if (cpmResult != null && cpmResult.getTaskResults() != null) {
            for (CpmTaskResult tr : cpmResult.getTaskResults()) {
                cpmTaskMap.put(tr.getTaskId(), tr);
            }
        }

        // Detect conflicts
        List<Task> allGlobalTasks = allSystemAssignments.stream()
                .map(TaskAssignment::getTask)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<ResourceConflict> allConflicts = conflictDetector.detectConflicts(allGlobalTasks, allSystemAssignments);

        // Filter conflicts involving this project's tasks
        Set<String> projectTaskNames = tasks.stream().map(Task::getName).collect(Collectors.toSet());
        List<Map<String, Object>> conflictList = new ArrayList<>();
        for (ResourceConflict c : allConflicts) {
            if (projectTaskNames.contains(c.getConflictingTask1()) || projectTaskNames.contains(c.getConflictingTask2())) {
                Map<String, Object> conf = new HashMap<>();
                conf.put("resourceName", c.getResource());
                conf.put("conflictingTask1", c.getConflictingTask1());
                conf.put("conflictingTask2", c.getConflictingTask2());
                conf.put("overlapStart", c.getOverlapStart().toString());
                conf.put("overlapEnd", c.getOverlapEnd().toString());
                conflictList.add(conf);
            }
        }

        // Map assignments by taskId
        Map<Long, TaskAssignment> assignmentMap = new HashMap<>();
        for (TaskAssignment a : projectAssignments) {
            assignmentMap.put(a.getTask().getId(), a);
        }

        // Dependencies maps
        Map<Long, List<String>> predsMap = new HashMap<>();
        Map<Long, List<String>> succsMap = new HashMap<>();
        for (TaskDependency dep : dependencies) {
            predsMap.computeIfAbsent(dep.getSuccessorTask().getId(), k -> new ArrayList<>())
                    .add(dep.getPredecessorTask().getName());
            succsMap.computeIfAbsent(dep.getPredecessorTask().getId(), k -> new ArrayList<>())
                    .add(dep.getSuccessorTask().getName());
        }

        // Tasks context
        List<Map<String, Object>> taskContextList = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> tm = new HashMap<>();
            tm.put("id", t.getId());
            tm.put("name", t.getName());
            tm.put("durationDays", t.getEstimatedDuration() != null ? t.getEstimatedDuration() : 1);
            tm.put("plannedStartDate", t.getPlannedStartDate() != null ? t.getPlannedStartDate().toString() : null);
            tm.put("plannedFinishDate", t.getPlannedFinishDate() != null ? t.getPlannedFinishDate().toString() : null);
            tm.put("status", t.getStatus() != null ? t.getStatus().name() : "TODO");
            tm.put("priority", t.getPriority() != null ? t.getPriority().name() : "MEDIUM");
            tm.put("requiredSkills", t.getRequiredSkills());

            TaskAssignment a = assignmentMap.get(t.getId());
            if (a != null) {
                if (a.getWorker() != null) {
                    tm.put("assignedResource", a.getWorker().getName());
                    tm.put("assignedResourceType", "WORKER");
                } else if (a.getVendor() != null) {
                    tm.put("assignedResource", a.getVendor().getName());
                    tm.put("assignedResourceType", "VENDOR");
                }
            }

            CpmTaskResult cpmRes = cpmTaskMap.get(t.getId());
            if (cpmRes != null) {
                tm.put("isCritical", cpmRes.isCritical());
                tm.put("totalSlack", cpmRes.getTotalSlack());
            } else {
                tm.put("isCritical", false);
                tm.put("totalSlack", 0);
            }

            tm.put("predecessors", predsMap.getOrDefault(t.getId(), Collections.emptyList()));
            tm.put("successors", succsMap.getOrDefault(t.getId(), Collections.emptyList()));
            taskContextList.add(tm);
        }

        // Delays context
        List<Map<String, Object>> delayList = new ArrayList<>();
        for (TaskDelay d : delays) {
            Map<String, Object> dm = new HashMap<>();
            dm.put("taskId", d.getTask().getId());
            dm.put("taskName", d.getTask().getName());
            dm.put("delayDurationDays", d.getDelayDurationDays());
            dm.put("delayReason", d.getDelayReason());
            dm.put("reportedDate", d.getReportedDate().toString());
            dm.put("notes", d.getNotes());
            delayList.add(dm);
        }

        // Recommendations context
        List<Map<String, Object>> recList = new ArrayList<>();
        for (OptimizationRecommendation r : recommendations) {
            Map<String, Object> rm = new HashMap<>();
            rm.put("id", r.getId());
            rm.put("summary", r.getSummary());
            rm.put("category", r.getCategory().name());
            rm.put("status", r.getStatus().name());
            rm.put("explanation", r.getExplanation());
            rm.put("estimatedImpact", r.getEstimatedImpact());
            recList.add(rm);
        }

        // Baseline
        var latestBaseline = baselineService.getLatestBaseline(projectId);
        String baselineDate = latestBaseline.map(b -> b.getExpectedCompletionDate().toString()).orElse(null);

        // Completion & Deadline status
        LocalDate forecast = project.getExpectedCompletionDate() != null
                ? project.getExpectedCompletionDate()
                : (cpmResult != null ? cpmResult.getEarliestProjectedCompletion() : LocalDate.now());

        String deadlineStatus = "ON_TRACK";
        int variance = 0;
        if (project.getDeadline() != null) {
            if (forecast.isAfter(project.getDeadline())) {
                deadlineStatus = "DELAYED";
                variance = workingDayCalculator.calculateWorkingDaysBetween(project.getDeadline(), forecast);
            } else {
                int slack = workingDayCalculator.calculateWorkingDaysBetween(forecast, project.getDeadline());
                if (slack <= 1 || !delays.isEmpty()) {
                    deadlineStatus = "AT_RISK";
                }
            }
        }

        Map<String, Object> context = new HashMap<>();
        context.put("projectId", project.getId());
        context.put("projectName", project.getName());
        context.put("startDate", project.getStartDate() != null ? project.getStartDate().toString() : null);
        context.put("deadline", project.getDeadline() != null ? project.getDeadline().toString() : null);
        context.put("currentForecastCompletion", forecast.toString());
        context.put("approvedBaselineCompletion", baselineDate);
        context.put("deadlineStatus", deadlineStatus);
        context.put("workingDaysVariance", variance);
        context.put("criticalPathSequence", cpmResult != null ? cpmResult.getCriticalTaskNames() : Collections.emptyList());
        context.put("minimumProjectDurationDays", cpmResult != null ? cpmResult.getMinimumDurationWorkingDays() : 0);
        context.put("tasks", taskContextList);
        context.put("reportedDelays", delayList);
        context.put("resourceConflicts", conflictList);
        context.put("activeRecommendations", recList);
        context.put("snapshotTimestamp", LocalDateTime.now().toString());

        return context;
    }
}
