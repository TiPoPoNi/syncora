package com.prs.cpm;

import com.prs.entity.Task;
import com.prs.entity.TaskDependency;
import com.prs.graph.DependencyGraph;
import com.prs.graph.TaskNode;
import com.prs.graph.TopologicalSorter;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CriticalPathService {

    private final WorkingDayCalculator workingDayCalculator;

    public CriticalPathResult calculate(LocalDate projectStartDate, List<Task> tasks, List<TaskDependency> dependencies) {
        if (tasks == null || tasks.isEmpty()) {
            LocalDate start = projectStartDate != null ? projectStartDate : LocalDate.now();
            return CriticalPathResult.builder()
                    .minimumDurationWorkingDays(0)
                    .projectStartDate(start)
                    .earliestProjectedCompletion(start)
                    .build();
        }

        // Build dependency graph and validate cycles
        DependencyGraph graph = new DependencyGraph();
        graph.buildGraph(tasks, dependencies);

        if (graph.hasCycle()) {
            throw new IllegalStateException("Cannot calculate critical path: Circular dependency detected in the project.");
        }

        TopologicalSorter sorter = new TopologicalSorter();
        List<TaskNode> sortedNodes = sorter.sort(graph);

        Map<Long, Integer> durations = new HashMap<>();
        Map<Long, Integer> earlyStart = new HashMap<>();
        Map<Long, Integer> earlyFinish = new HashMap<>();
        Map<Long, Integer> lateStart = new HashMap<>();
        Map<Long, Integer> lateFinish = new HashMap<>();

        for (Task task : tasks) {
            int d = (task.getEstimatedDuration() != null && task.getEstimatedDuration() > 0)
                    ? task.getEstimatedDuration() : 1;
            durations.put(task.getId(), d);
        }

        // 1. Forward Pass
        for (TaskNode node : sortedNodes) {
            long taskId = node.getTask().getId();
            int duration = durations.get(taskId);
            int es = 0;

            for (TaskNode pred : node.getPredecessors()) {
                long predId = pred.getTask().getId();
                int predEf = earlyFinish.getOrDefault(predId, 0);
                if (predEf > es) {
                    es = predEf;
                }
            }

            earlyStart.put(taskId, es);
            earlyFinish.put(taskId, es + duration);
        }

        // Minimum Project Duration D
        int projectDuration = 0;
        for (int ef : earlyFinish.values()) {
            if (ef > projectDuration) {
                projectDuration = ef;
            }
        }

        // 2. Backward Pass (in reverse topological order)
        for (int i = sortedNodes.size() - 1; i >= 0; i--) {
            TaskNode node = sortedNodes.get(i);
            long taskId = node.getTask().getId();
            int duration = durations.get(taskId);
            int lf = projectDuration;

            if (!node.getSuccessors().isEmpty()) {
                lf = Integer.MAX_VALUE;
                for (TaskNode succ : node.getSuccessors()) {
                    long succId = succ.getTask().getId();
                    int succLs = lateStart.get(succId);
                    if (succLs < lf) {
                        lf = succLs;
                    }
                }
            }

            lateFinish.put(taskId, lf);
            lateStart.put(taskId, lf - duration);
        }

        // 3. Slack & Critical Tasks
        LocalDate effectiveStart = workingDayCalculator.getEffectiveStartDate(
                projectStartDate != null ? projectStartDate : LocalDate.now()
        );

        List<CpmTaskResult> taskResults = new ArrayList<>();
        Set<Long> criticalTaskIds = new HashSet<>();
        Map<Long, CpmTaskResult> resultMap = new HashMap<>();

        for (Task task : tasks) {
            long taskId = task.getId();
            int es = earlyStart.get(taskId);
            int ef = earlyFinish.get(taskId);
            int ls = lateStart.get(taskId);
            int lf = lateFinish.get(taskId);
            int totalSlack = Math.max(0, ls - es);
            boolean isCritical = (totalSlack == 0);

            if (isCritical) {
                criticalTaskIds.add(taskId);
            }

            int dur = durations.get(taskId);
            LocalDate esDate = (es == 0) ? effectiveStart : workingDayCalculator.addWorkingDays(effectiveStart, es);
            LocalDate efDate = workingDayCalculator.calculateFinishDate(esDate, dur);

            LocalDate lsDate = (ls == 0) ? effectiveStart : workingDayCalculator.addWorkingDays(effectiveStart, ls);
            LocalDate lfDate = workingDayCalculator.calculateFinishDate(lsDate, dur);

            TaskNode node = graph.getNodes().get(taskId);
            List<Long> predIds = node != null ? node.getPredecessors().stream().map(p -> p.getTask().getId()).collect(Collectors.toList()) : Collections.emptyList();
            List<Long> succIds = node != null ? node.getSuccessors().stream().map(s -> s.getTask().getId()).collect(Collectors.toList()) : Collections.emptyList();

            CpmTaskResult taskRes = CpmTaskResult.builder()
                    .taskId(taskId)
                    .taskName(task.getName())
                    .duration(dur)
                    .earlyStart(es)
                    .earlyFinish(ef)
                    .lateStart(ls)
                    .lateFinish(lf)
                    .totalSlack(totalSlack)
                    .critical(isCritical)
                    .earlyStartDate(esDate)
                    .earlyFinishDate(efDate)
                    .lateStartDate(lsDate)
                    .lateFinishDate(lfDate)
                    .predecessorTaskIds(predIds)
                    .successorTaskIds(succIds)
                    .build();

            taskResults.add(taskRes);
            resultMap.put(taskId, taskRes);
        }

        // Sort taskResults in topological order
        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
            orderMap.put(sortedNodes.get(i).getTask().getId(), i);
        }
        taskResults.sort(Comparator.comparingInt(t -> orderMap.getOrDefault(t.getTaskId(), 0)));

        // 4. Find Critical Paths
        List<List<Long>> allCriticalPaths = findCriticalPaths(graph, criticalTaskIds, earlyStart, earlyFinish, projectDuration);
        List<Long> representativePath = allCriticalPaths.isEmpty() ? Collections.emptyList() : allCriticalPaths.get(0);

        List<String> criticalTaskNames = representativePath.stream()
                .map(id -> resultMap.get(id).getTaskName())
                .collect(Collectors.toList());

        LocalDate projectedCompletion = effectiveStart;
        for (CpmTaskResult tr : taskResults) {
            if (tr.getEarlyFinishDate() != null && tr.getEarlyFinishDate().isAfter(projectedCompletion)) {
                projectedCompletion = tr.getEarlyFinishDate();
            }
        }

        return CriticalPathResult.builder()
                .minimumDurationWorkingDays(projectDuration)
                .projectStartDate(effectiveStart)
                .earliestProjectedCompletion(projectedCompletion)
                .taskResults(taskResults)
                .criticalTaskIds(criticalTaskIds)
                .representativeCriticalPath(representativePath)
                .allCriticalPaths(allCriticalPaths)
                .criticalTaskNames(criticalTaskNames)
                .build();
    }

    private List<List<Long>> findCriticalPaths(
            DependencyGraph graph,
            Set<Long> criticalTaskIds,
            Map<Long, Integer> earlyStart,
            Map<Long, Integer> earlyFinish,
            int projectDuration) {

        List<List<Long>> paths = new ArrayList<>();
        if (criticalTaskIds.isEmpty()) return paths;

        // Start tasks: critical tasks that have no critical predecessors
        List<Long> startTasks = new ArrayList<>();
        for (Long id : criticalTaskIds) {
            TaskNode node = graph.getNodes().get(id);
            boolean hasCriticalPred = false;
            if (node != null) {
                for (TaskNode pred : node.getPredecessors()) {
                    if (criticalTaskIds.contains(pred.getTask().getId())) {
                        hasCriticalPred = true;
                        break;
                    }
                }
            }
            if (!hasCriticalPred) {
                startTasks.add(id);
            }
        }

        for (Long startId : startTasks) {
            List<Long> currentPath = new ArrayList<>();
            dfsCriticalPath(startId, currentPath, paths, graph, criticalTaskIds, earlyStart, earlyFinish, projectDuration);
        }

        return paths;
    }

    private void dfsCriticalPath(
            Long currentId,
            List<Long> currentPath,
            List<List<Long>> allPaths,
            DependencyGraph graph,
            Set<Long> criticalTaskIds,
            Map<Long, Integer> earlyStart,
            Map<Long, Integer> earlyFinish,
            int projectDuration) {

        currentPath.add(currentId);
        TaskNode node = graph.getNodes().get(currentId);

        List<Long> validCriticalSuccessors = new ArrayList<>();
        if (node != null) {
            int currentEf = earlyFinish.get(currentId);
            for (TaskNode succ : node.getSuccessors()) {
                long succId = succ.getTask().getId();
                if (criticalTaskIds.contains(succId) && earlyStart.get(succId) == currentEf) {
                    validCriticalSuccessors.add(succId);
                }
            }
        }

        if (validCriticalSuccessors.isEmpty()) {
            // Reached terminal task on critical path
            if (earlyFinish.get(currentId) == projectDuration) {
                allPaths.add(new ArrayList<>(currentPath));
            }
        } else {
            for (Long succId : validCriticalSuccessors) {
                dfsCriticalPath(succId, currentPath, allPaths, graph, criticalTaskIds, earlyStart, earlyFinish, projectDuration);
            }
        }

        currentPath.remove(currentPath.size() - 1);
    }
}
