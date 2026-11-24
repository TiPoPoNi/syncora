package com.prs.delay;

import com.prs.cpm.CpmTaskResult;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.DelayImpactDto;
import com.prs.dto.TaskDelayDto;
import com.prs.entity.*;
import com.prs.graph.DependencyGraph;
import com.prs.graph.TaskNode;
import com.prs.graph.TopologicalSorter;
import com.prs.repository.ProjectBaselineRepository;
import com.prs.repository.TaskBaselineRepository;
import com.prs.repository.TaskDelayRepository;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DelayPropagationService {

    private final WorkingDayCalculator workingDayCalculator;
    private final CriticalPathService criticalPathService;
    private final TaskDelayRepository taskDelayRepository;
    private final ProjectBaselineRepository baselineRepository;
    private final TaskBaselineRepository taskBaselineRepository;
    private final DelayReportingService delayReportingService;

    public DelayImpactDto analyzeDelayImpact(Project project, List<Task> tasks, List<TaskDependency> dependencies) {
        Long projectId = project.getId();
        List<TaskDelayDto> reportedDelays = delayReportingService.getDelaysForProject(projectId);
        Optional<ProjectBaseline> latestBaseline = baselineRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId);

        // Map baseline dates by task ID
        Map<Long, LocalDate> baselineFinishDates = new HashMap<>();
        Map<Long, LocalDate> baselineStartDates = new HashMap<>();
        LocalDate baselineCompletion = project.getExpectedCompletionDate();

        if (latestBaseline.isPresent()) {
            baselineCompletion = latestBaseline.get().getExpectedCompletionDate();
            List<TaskBaseline> taskBaselines = taskBaselineRepository.findByBaselineId(latestBaseline.get().getId());
            for (TaskBaseline tb : taskBaselines) {
                baselineFinishDates.put(tb.getTask().getId(), tb.getPlannedFinishDate());
                baselineStartDates.put(tb.getTask().getId(), tb.getPlannedStartDate());
            }
        } else {
            for (Task t : tasks) {
                if (t.getPlannedFinishDate() != null) baselineFinishDates.put(t.getId(), t.getPlannedFinishDate());
                if (t.getPlannedStartDate() != null) baselineStartDates.put(t.getId(), t.getPlannedStartDate());
            }
        }

        // Calculate original CPM before delays
        CriticalPathResult originalCpm = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);

        for (CpmTaskResult ctr : originalCpm.getTaskResults()) {
            baselineFinishDates.putIfAbsent(ctr.getTaskId(), ctr.getEarlyFinishDate());
            baselineStartDates.putIfAbsent(ctr.getTaskId(), ctr.getEarlyStartDate());
        }
        if (baselineCompletion == null) {
            baselineCompletion = originalCpm.getEarliestProjectedCompletion();
        }

        // Sum active delays per task
        Map<Long, Integer> delayDaysPerTask = new HashMap<>();
        for (TaskDelayDto delay : reportedDelays) {
            if (delay.getStatus() != DelayStatus.RESOLVED) {
                delayDaysPerTask.merge(delay.getTaskId(), delay.getDelayDurationDays(), Integer::sum);
            }
        }

        // Build temporary clone/forecast map of tasks to compute delay propagation
        DependencyGraph graph = new DependencyGraph();
        graph.buildGraph(tasks, dependencies);
        TopologicalSorter sorter = new TopologicalSorter();
        List<TaskNode> sortedNodes = sorter.sort(graph);

        LocalDate effectiveStart = workingDayCalculator.getEffectiveStartDate(
                project.getStartDate() != null ? project.getStartDate() : LocalDate.now()
        );

        Map<Long, LocalDate> forecastStarts = new HashMap<>();
        Map<Long, LocalDate> forecastFinishes = new HashMap<>();

        for (TaskNode node : sortedNodes) {
            Task task = node.getTask();
            long taskId = task.getId();

            if (task.getStatus() == TaskStatus.COMPLETED && task.getPlannedStartDate() != null && task.getPlannedFinishDate() != null) {
                // Completed tasks retain their historical planned dates
                forecastStarts.put(taskId, task.getPlannedStartDate());
                forecastFinishes.put(taskId, task.getPlannedFinishDate());
                continue;
            }

            LocalDate earliestStart = effectiveStart;
            for (TaskNode pred : node.getPredecessors()) {
                long predId = pred.getTask().getId();
                LocalDate predFinish = forecastFinishes.get(predId);
                if (predFinish != null) {
                    LocalDate nextDay = workingDayCalculator.getNextWorkingDay(predFinish);
                    if (nextDay.isAfter(earliestStart)) {
                        earliestStart = nextDay;
                    }
                }
            }

            if (!workingDayCalculator.isWorkingDay(earliestStart)) {
                earliestStart = workingDayCalculator.getNextWorkingDay(earliestStart);
            }

            forecastStarts.put(taskId, earliestStart);

            int baseDuration = (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 0)
                    ? task.getEstimatedDuration() : 1;
            int additionalDelay = delayDaysPerTask.getOrDefault(taskId, 0);
            int effectiveDuration = baseDuration + additionalDelay;

            LocalDate finish = workingDayCalculator.calculateFinishDate(earliestStart, effectiveDuration);
            forecastFinishes.put(taskId, finish);
        }

        LocalDate forecastCompletion = effectiveStart;
        for (LocalDate f : forecastFinishes.values()) {
            if (f.isAfter(forecastCompletion)) {
                forecastCompletion = f;
            }
        }

        // Determine affected vs unaffected tasks
        List<String> affectedTasks = new ArrayList<>();
        List<String> unaffectedTasks = new ArrayList<>();
        List<String> criticalTasksAffected = new ArrayList<>();

        for (Task task : tasks) {
            long taskId = task.getId();
            LocalDate origFinish = baselineFinishDates.get(taskId);
            LocalDate newFinish = forecastFinishes.get(taskId);

            boolean moved = (origFinish != null && newFinish != null && newFinish.isAfter(origFinish));
            if (moved) {
                affectedTasks.add(task.getName());
                if (originalCpm.getCriticalTaskIds().contains(taskId)) {
                    criticalTasksAffected.add(task.getName());
                }
            } else {
                unaffectedTasks.add(task.getName());
            }
        }

        // Revised CPM
        List<Task> virtualDelayedTasks = new ArrayList<>();
        for (Task t : tasks) {
            Task copy = new Task();
            copy.setId(t.getId());
            copy.setName(t.getName());
            copy.setStatus(t.getStatus());
            int baseDur = (t.getEstimatedDuration() != null && t.getEstimatedDuration() > 0) ? t.getEstimatedDuration() : 1;
            int extra = delayDaysPerTask.getOrDefault(t.getId(), 0);
            copy.setEstimatedDuration(baseDur + extra);
            virtualDelayedTasks.add(copy);
        }

        CriticalPathResult revisedCpm = criticalPathService.calculate(project.getStartDate(), virtualDelayedTasks, dependencies);

        boolean criticalPathChanged = !originalCpm.getRepresentativeCriticalPath().equals(revisedCpm.getRepresentativeCriticalPath());

        // Variances
        int varianceFromBaseline = (baselineCompletion != null)
                ? workingDayCalculator.calculateWorkingDaysBetween(baselineCompletion, forecastCompletion) : 0;

        int varianceFromDeadline = (project.getDeadline() != null)
                ? workingDayCalculator.calculateWorkingDaysBetween(project.getDeadline(), forecastCompletion) : 0;

        String deadlineStatus = "ON_TRACK";
        if (project.getDeadline() != null && forecastCompletion.isAfter(project.getDeadline())) {
            deadlineStatus = "DELAYED";
        } else if (varianceFromDeadline >= -2 || !criticalTasksAffected.isEmpty()) {
            deadlineStatus = "AT_RISK";
        }

        List<String> primaryReasons = reportedDelays.stream()
                .map(TaskDelayDto::getDelayReason)
                .distinct()
                .collect(Collectors.toList());

        // Human readable explanation
        StringBuilder explanation = new StringBuilder();
        if (reportedDelays.isEmpty()) {
            explanation.append("No active delays reported. Schedule is operating on the planned timeline.");
        } else {
            explanation.append(String.format("Project forecast completion shifted from %s to %s (%+d working days vs baseline). ",
                    baselineCompletion != null ? baselineCompletion : "N/A",
                    forecastCompletion,
                    varianceFromBaseline));

            if (!affectedTasks.isEmpty()) {
                explanation.append(String.format("%d task(s) moved due to dependency chains: %s. ",
                        affectedTasks.size(), String.join(", ", affectedTasks)));
            }

            if (!unaffectedTasks.isEmpty()) {
                explanation.append(String.format("%d task(s) absorbed delays through slack or parallel paths: %s. ",
                        unaffectedTasks.size(), String.join(", ", unaffectedTasks)));
            }

            if (criticalPathChanged) {
                explanation.append("The critical path has changed as downstream bottlenecks shifted.");
            }
        }

        return DelayImpactDto.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .projectStartDate(project.getStartDate())
                .baselineCompletionDate(baselineCompletion)
                .forecastCompletionDate(forecastCompletion)
                .clientDeadline(project.getDeadline())
                .varianceFromBaselineWorkingDays(varianceFromBaseline)
                .varianceFromDeadlineWorkingDays(varianceFromDeadline)
                .deadlineStatus(deadlineStatus)
                .reportedDelays(reportedDelays)
                .affectedTasks(affectedTasks)
                .unaffectedTasks(unaffectedTasks)
                .criticalTasksAffected(criticalTasksAffected)
                .primaryDelayReasons(primaryReasons)
                .explanation(explanation.toString().trim())
                .criticalPathChanged(criticalPathChanged)
                .originalCriticalPath(originalCpm.getCriticalTaskNames())
                .revisedCriticalPath(revisedCpm.getCriticalTaskNames())
                .build();
    }

    /**
     * Applies delay propagation and persists the updated forecast dates into the database.
     */
    public void propagateAndPersistSchedule(Project project, List<Task> tasks, List<TaskDependency> dependencies) {
        List<TaskDelayDto> reportedDelays = delayReportingService.getDelaysForProject(project.getId());
        Map<Long, Integer> delayDaysPerTask = new HashMap<>();
        for (TaskDelayDto delay : reportedDelays) {
            if (delay.getStatus() != DelayStatus.RESOLVED) {
                delayDaysPerTask.merge(delay.getTaskId(), delay.getDelayDurationDays(), Integer::sum);
            }
        }

        DependencyGraph graph = new DependencyGraph();
        graph.buildGraph(tasks, dependencies);
        TopologicalSorter sorter = new TopologicalSorter();
        List<TaskNode> sortedNodes = sorter.sort(graph);

        LocalDate effectiveStart = workingDayCalculator.getEffectiveStartDate(
                project.getStartDate() != null ? project.getStartDate() : LocalDate.now()
        );

        for (TaskNode node : sortedNodes) {
            Task task = node.getTask();
            if (task.getStatus() == TaskStatus.COMPLETED && task.getPlannedStartDate() != null && task.getPlannedFinishDate() != null) {
                continue;
            }

            LocalDate earliestStart = effectiveStart;
            for (TaskNode pred : node.getPredecessors()) {
                Task predTask = pred.getTask();
                if (predTask.getPlannedFinishDate() != null) {
                    LocalDate nextDay = workingDayCalculator.getNextWorkingDay(predTask.getPlannedFinishDate());
                    if (nextDay.isAfter(earliestStart)) {
                        earliestStart = nextDay;
                    }
                }
            }

            if (!workingDayCalculator.isWorkingDay(earliestStart)) {
                earliestStart = workingDayCalculator.getNextWorkingDay(earliestStart);
            }

            task.setPlannedStartDate(earliestStart);

            int baseDuration = (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 0)
                    ? task.getEstimatedDuration() : 1;
            int delay = delayDaysPerTask.getOrDefault(task.getId(), 0);
            int effectiveDuration = baseDuration + delay;

            LocalDate finish = workingDayCalculator.calculateFinishDate(earliestStart, effectiveDuration);
            task.setPlannedFinishDate(finish);
        }

        LocalDate completion = effectiveStart;
        for (Task t : tasks) {
            if (t.getPlannedFinishDate() != null && t.getPlannedFinishDate().isAfter(completion)) {
                completion = t.getPlannedFinishDate();
            }
        }
        project.setExpectedCompletionDate(completion);
    }
}
