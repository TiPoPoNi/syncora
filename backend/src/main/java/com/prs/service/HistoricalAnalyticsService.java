package com.prs.service;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.CompleteTaskRequest;
import com.prs.dto.HistoricalOutcomesDto;
import com.prs.dto.TaskDelayRiskDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalAnalyticsService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskOutcomeRecordRepository outcomeRepository;
    private final TaskDelayPredictionRepository predictionRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskDelayRepository delayRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final CriticalPathService criticalPathService;
    private final NotificationService notificationService;
    private final DeliverableManagementService deliverableManagementService;

    public static final int MIN_REQUIRED_TRAINING_SAMPLES = 100;

    /**
     * Mark a task completed, computing actual working-day duration and archiving immutable outcome.
     */
    @Transactional
    public TaskOutcomeRecord completeTask(Long taskId, CompleteTaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));

        // Enforce mandatory deliverable approval gates
        deliverableManagementService.validateTaskCanBeCompleted(task);

        LocalDate actualFinish = request.getActualFinishDate();
        LocalDate actualStart = request.getActualStartDate() != null ? request.getActualStartDate() : task.getPlannedStartDate();
        if (actualStart == null) {
            actualStart = task.getStartDate() != null ? task.getStartDate() : actualFinish;
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setProgress(100);
        task.setActualStartDate(actualStart);
        task.setActualFinishDate(actualFinish);
        taskRepository.save(task);

        LocalDate plannedFinish = task.getPlannedFinishDate() != null ? task.getPlannedFinishDate() : actualFinish;
        LocalDate plannedStart = task.getPlannedStartDate() != null ? task.getPlannedStartDate() : actualStart;

        int plannedDuration = task.getEstimatedDuration() != null ? task.getEstimatedDuration() : 1;
        int actualDuration = Math.max(1, workingDayCalculator.calculateWorkingDaysBetween(actualStart, actualFinish) + 1);
        int varianceDays = workingDayCalculator.calculateWorkingDaysBetween(plannedFinish, actualFinish);
        boolean isDelayed = actualFinish.isAfter(plannedFinish);

        // Fetch primary delay reason if available
        List<TaskDelay> delays = delayRepository.findByTaskId(taskId);
        String reason = request.getPrimaryDelayReason();
        if (reason == null && !delays.isEmpty()) {
            reason = delays.get(0).getDelayReason();
        }

        // Fetch assigned worker
        Worker worker = null;
        List<TaskAssignment> assignments = assignmentRepository.findByTaskId(taskId);
        for (TaskAssignment a : assignments) {
            if (a.getWorker() != null) {
                worker = a.getWorker();
                break;
            }
        }

        // Check if task was on critical path
        boolean wasCritical = false;
        try {
            List<Task> projectTasks = taskRepository.findByProjectId(task.getProject().getId());
            List<TaskDependency> deps = dependencyRepository.findBySuccessorTaskProjectId(task.getProject().getId());
            CriticalPathResult cpm = criticalPathService.calculate(task.getProject().getStartDate(), projectTasks, deps);
            wasCritical = cpm.getCriticalTaskIds().contains(taskId);
        } catch (Exception e) {
            log.debug("CPM calculation skipped for outcome: {}", e.getMessage());
        }

        TaskOutcomeRecord outcome = TaskOutcomeRecord.builder()
                .task(task)
                .project(task.getProject())
                .taskName(task.getName())
                .plannedStartDate(plannedStart)
                .plannedFinishDate(plannedFinish)
                .actualStartDate(actualStart)
                .actualFinishDate(actualFinish)
                .plannedDuration(plannedDuration)
                .actualDuration(actualDuration)
                .varianceWorkingDays(varianceDays)
                .isDelayed(isDelayed)
                .primaryDelayReason(reason)
                .totalDelaysReported(delays.size())
                .assignedWorker(worker)
                .wasCriticalPath(wasCritical)
                .recordedAt(LocalDateTime.now())
                .build();

        TaskOutcomeRecord savedOutcome = outcomeRepository.save(outcome);

        if (task.getProject() != null && task.getProject().getManager() != null) {
            notificationService.sendNotification(
                    task.getProject().getManager(),
                    com.prs.entity.NotificationEventType.MILESTONE_COMPLETED,
                    "Task Completed: " + task.getName(),
                    String.format("Task '%s' in project '%s' has been marked COMPLETED (Schedule Variance: %+dd).",
                            task.getName(), task.getProject().getName(), varianceDays),
                    task.getProject().getId(),
                    task.getId()
            );
        }

        return savedOutcome;
    }

    /**
     * Get historical project completion statistics and reliability metrics.
     */
    @Transactional(readOnly = true)
    public HistoricalOutcomesDto getHistoricalOutcomes(Long projectId) {
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        List<TaskOutcomeRecord> outcomes;
        if (projectId != null) {
            outcomes = outcomeRepository.findByProjectId(projectId);
        } else if (tenantId != null) {
            outcomes = outcomeRepository.findByProjectOrganizationId(tenantId);
        } else {
            outcomes = outcomeRepository.findAll();
        }

        long total = outcomes.size();
        long delayed = outcomes.stream().filter(TaskOutcomeRecord::getIsDelayed).count();
        long onTime = total - delayed;
        double onTimeRate = total > 0 ? (double) onTime / total * 100.0 : 0.0;

        double avgVariance = total > 0
                ? outcomes.stream().mapToInt(o -> o.getVarianceWorkingDays() != null ? o.getVarianceWorkingDays() : 0).average().orElse(0.0)
                : 0.0;

        // Group delay reasons
        Map<String, Long> reasons = outcomes.stream()
                .filter(TaskOutcomeRecord::getIsDelayed)
                .map(TaskOutcomeRecord::getPrimaryDelayReason)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        // Group worker reliability
        Map<String, List<TaskOutcomeRecord>> byWorker = outcomes.stream()
                .filter(o -> o.getAssignedWorker() != null)
                .collect(Collectors.groupingBy(o -> o.getAssignedWorker().getName()));

        List<HistoricalOutcomesDto.WorkerReliabilityDto> workerStats = new ArrayList<>();
        for (Map.Entry<String, List<TaskOutcomeRecord>> entry : byWorker.entrySet()) {
            long wTotal = entry.getValue().size();
            long wDelayed = entry.getValue().stream().filter(TaskOutcomeRecord::getIsDelayed).count();
            double wRate = wTotal > 0 ? (double) (wTotal - wDelayed) / wTotal * 100.0 : 0.0;
            workerStats.add(HistoricalOutcomesDto.WorkerReliabilityDto.builder()
                    .workerName(entry.getKey())
                    .totalTasks(wTotal)
                    .delayedTasks(wDelayed)
                    .onTimeRatePercent(Math.round(wRate * 10.0) / 10.0)
                    .build());
        }

        List<HistoricalOutcomesDto.TaskOutcomeSummaryDto> recent = outcomes.stream()
                .sorted(Comparator.comparing(TaskOutcomeRecord::getRecordedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(o -> HistoricalOutcomesDto.TaskOutcomeSummaryDto.builder()
                        .id(o.getId())
                        .taskId(o.getTask() != null ? o.getTask().getId() : null)
                        .taskName(o.getTaskName())
                        .projectName(o.getProject() != null ? o.getProject().getName() : null)
                        .plannedFinishDate(o.getPlannedFinishDate())
                        .actualFinishDate(o.getActualFinishDate())
                        .varianceWorkingDays(o.getVarianceWorkingDays() != null ? o.getVarianceWorkingDays() : 0)
                        .delayed(o.getIsDelayed())
                        .delayReason(o.getPrimaryDelayReason())
                        .workerName(o.getAssignedWorker() != null ? o.getAssignedWorker().getName() : "Unassigned")
                        .build())
                .collect(Collectors.toList());

        return HistoricalOutcomesDto.builder()
                .totalCompletedTasks(total)
                .onTimeTasks(onTime)
                .delayedTasks(delayed)
                .onTimeRatePercent(Math.round(onTimeRate * 10.0) / 10.0)
                .averageVarianceDays(Math.round(avgVariance * 10.0) / 10.0)
                .delayReasonsDistribution(reasons)
                .workerReliability(workerStats)
                .recentOutcomes(recent)
                .build();
    }

    /**
     * Get delay risk estimates for active project tasks, strictly distinguishing deterministic status from ML risk.
     */
    @Transactional(readOnly = true)
    public List<TaskDelayRiskDto> getProjectDelayRisk(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with ID: " + projectId));

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> deps = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        CriticalPathResult cpm = null;
        try {
            cpm = criticalPathService.calculate(project.getStartDate(), tasks, deps);
        } catch (Exception e) {
            log.warn("CPM calculation error during risk assessment: {}", e.getMessage());
        }

        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        long completedOutcomesCount = tenantId != null
                ? outcomeRepository.countByProjectOrganizationId(tenantId)
                : outcomeRepository.count();
        boolean mlReady = completedOutcomesCount >= MIN_REQUIRED_TRAINING_SAMPLES;

        List<TaskDelayRiskDto> results = new ArrayList<>();

        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                continue; // Skip already completed tasks
            }

            // 1. Authoritative Deterministic Calculation
            boolean isCritical = false;
            int slack = 0;
            if (cpm != null) {
                isCritical = cpm.getCriticalTaskIds().contains(task.getId());
                slack = cpm.getTaskResults().stream()
                        .filter(r -> r.getTaskId() == task.getId())
                        .mapToInt(r -> r.getTotalSlack())
                        .findFirst()
                        .orElse(0);
            }

            boolean isProjectedDelayed = isCritical && task.getDeadline() != null && task.getPlannedFinishDate() != null
                    && task.getPlannedFinishDate().isAfter(task.getDeadline());

            String scheduleStatus = isProjectedDelayed ? "PROJECTED_DELAY" : (isCritical ? "CRITICAL_BOTTLENECK" : "ON_SCHEDULE");

            List<String> contributingFactors = new ArrayList<>();
            if (isCritical) contributingFactors.add("Task lies on the Critical Path (Float = 0 days)");
            if (task.getPriority() == Priority.HIGH) contributingFactors.add("High priority milestone");
            List<TaskDelay> delays = delayRepository.findByTaskId(task.getId());
            if (!delays.isEmpty()) contributingFactors.add(delays.size() + " delay(s) reported on this task");

            // 2. ML Advisory Assessment (Governed strictly: No fabricated probabilities!)
            TaskDelayRiskDto dto = TaskDelayRiskDto.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .projectId(projectId)
                    .deterministicScheduleStatus(scheduleStatus)
                    .deterministicSlackDays(slack)
                    .isOnCriticalPath(isCritical)
                    .mlPredictionStatus(mlReady ? "ENABLED" : "NOT_READY")
                    .delayRiskScore(null) // Governed: Not fabricated when training data is below gate
                    .riskLevel(mlReady ? "EVALUATED" : "UNASSESSED")
                    .modelVersion(mlReady ? "task_delay_risk_v1.0" : "None (Data Threshold Gate: " + completedOutcomesCount + "/" + MIN_REQUIRED_TRAINING_SAMPLES + " completed tasks)")
                    .contributingFactors(contributingFactors)
                    .limitations(mlReady ? "Model trained on historical dataset" : "Active production dataset contains " + completedOutcomesCount + " completed task records. A minimum of " + MIN_REQUIRED_TRAINING_SAMPLES + " verified task outcomes is required to train a reliable Scikit-Learn baseline. Model training pipeline is validated on test fixtures in ai-service/app/ml/.")
                    .assessedAt(LocalDateTime.now())
                    .build();

            results.add(dto);
        }

        return results;
    }
}
