package com.prs.service;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioAnalyticsService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final VendorRepository vendorRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskDelayRepository delayRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final TaskOutcomeRecordRepository outcomeRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final CriticalPathService criticalPathService;
    private final ConflictDetector conflictDetector;

    /**
     * Top-level Portfolio KPI Summary
     */
    @Transactional(readOnly = true)
    public PortfolioKpiSummaryDto getPortfolioKpiSummary(User user, AnalyticsFilter filter) {
        List<Project> scopedProjects = getScopedProjects(user, filter);

        int total = scopedProjects.size();
        int active = (int) scopedProjects.stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == ProjectStatus.IN_PROGRESS || p.getStatus() == ProjectStatus.PLANNED)
                .count();
        int completed = (int) scopedProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.COMPLETED)
                .count();
        int onHold = (int) scopedProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ON_HOLD)
                .count();
        int cancelled = (int) scopedProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.CANCELLED)
                .count();

        LocalDate today = LocalDate.now();

        // Projects forecast to miss deadlines
        int missDeadlines = (int) scopedProjects.stream()
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.CANCELLED)
                .filter(p -> isProjectForecastToMiss(p))
                .count();

        // Overdue active tasks across scoped projects
        Set<Long> projectIds = scopedProjects.stream().map(Project::getId).collect(Collectors.toSet());
        List<Task> allActiveTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectIds.contains(t.getProject().getId()))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .collect(Collectors.toList());

        int overdueTasks = (int) allActiveTasks.stream()
                .filter(t -> (t.getDeadline() != null && t.getDeadline().isBefore(today)) ||
                             (t.getPlannedFinishDate() != null && t.getPlannedFinishDate().isBefore(today)))
                .count();

        // Active conflicts
        List<Task> allDbTasks = taskRepository.findAll();
        List<TaskAssignment> allDbAssignments = assignmentRepository.findAll();
        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(allDbTasks, allDbAssignments);
        Set<String> conflictedTaskNames = new HashSet<>();
        for (ResourceConflict c : conflicts) {
            if (c.getConflictingTask1() != null) conflictedTaskNames.add(c.getConflictingTask1());
            if (c.getConflictingTask2() != null) conflictedTaskNames.add(c.getConflictingTask2());
        }
        int activeConflicts = (int) allActiveTasks.stream()
                .filter(t -> conflictedTaskNames.contains(t.getName()))
                .count();

        // Historical On-Time Delivery Rate for completed projects
        List<Project> completedList = scopedProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.COMPLETED)
                .collect(Collectors.toList());

        Double onTimeRate = null;
        Double avgVariance = null;
        int completedWithDates = 0;

        if (!completedList.isEmpty()) {
            int onTimeCount = 0;
            double totalVariance = 0.0;
            for (Project p : completedList) {
                LocalDate plannedFinish = p.getDeadline() != null ? p.getDeadline() : p.getExpectedCompletionDate();
                LocalDate actualFinish = p.getExpectedCompletionDate(); // actual completion recorded on project
                
                // Also check if any task outcome records exist
                List<TaskOutcomeRecord> outcomes = outcomeRepository.findByProjectId(p.getId());
                if (!outcomes.isEmpty()) {
                    actualFinish = outcomes.stream()
                            .map(TaskOutcomeRecord::getActualFinishDate)
                            .max(LocalDate::compareTo)
                            .orElse(actualFinish);
                }

                if (plannedFinish != null && actualFinish != null) {
                    completedWithDates++;
                    int variance = workingDayCalculator.calculateWorkingDaysBetween(plannedFinish, actualFinish);
                    totalVariance += variance;
                    if (!actualFinish.isAfter(plannedFinish)) {
                        onTimeCount++;
                    }
                }
            }

            if (completedWithDates > 0) {
                onTimeRate = Math.round(((double) onTimeCount / completedWithDates * 100.0) * 10.0) / 10.0;
                avgVariance = Math.round((totalVariance / completedWithDates) * 10.0) / 10.0;
            }
        }

        // Resource Utilization
        Double overallUtilization = null;
        int totalWorkers = 0;
        if (user.getRole() != Role.CLIENT) {
            ResourceUtilizationSummaryDto utilSummary = getResourceUtilization(user, filter);
            overallUtilization = utilSummary.getOverallUtilizationPercent();
            totalWorkers = utilSummary.getTotalWorkers();
        }

        // Vendor Reliability
        Double vendorReliability = null;
        int totalVendors = 0;
        if (user.getRole() != Role.CLIENT) {
            VendorAnalyticsSummaryDto vendorSummary = getVendorPerformance(user, filter);
            vendorReliability = vendorSummary.getOverallOnTimeRatePercent();
            totalVendors = vendorSummary.getTotalVendors();
        }

        Map<String, Long> statusCounts = scopedProjects.stream()
                .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus().name() : "PLANNED", Collectors.counting()));

        String period = "ALL_TIME";
        if (filter != null && filter.getStartDate() != null && filter.getEndDate() != null) {
            period = filter.getStartDate() + " to " + filter.getEndDate();
        }

        return PortfolioKpiSummaryDto.builder()
                .totalProjects(total)
                .activeProjects(active)
                .completedProjects(completed)
                .onHoldProjects(onHold)
                .cancelledProjects(cancelled)
                .projectsForecastToMissDeadlines(missDeadlines)
                .totalOverdueTasks(overdueTasks)
                .activeConflictsCount(activeConflicts)
                .historicalOnTimeDeliveryRatePercent(onTimeRate)
                .averageScheduleVarianceDays(avgVariance)
                .completedProjectsWithRecordedOutcome(completedWithDates)
                .resourceUtilizationPercent(overallUtilization)
                .vendorDeliveryReliabilityPercent(vendorReliability)
                .totalWorkersEvaluated(totalWorkers)
                .totalVendorsEvaluated(totalVendors)
                .reportingPeriod(period)
                .projectStatusCounts(statusCounts)
                .build();
    }

    /**
     * Project-by-project performance breakdown
     */
    @Transactional(readOnly = true)
    public List<ProjectPerformanceDto> getProjectsPerformance(User user, AnalyticsFilter filter) {
        List<Project> scopedProjects = getScopedProjects(user, filter);
        List<Task> allDbTasks = taskRepository.findAll();
        List<TaskAssignment> allDbAssignments = assignmentRepository.findAll();
        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(allDbTasks, allDbAssignments);
        Set<String> conflictedTaskNames = new HashSet<>();
        for (ResourceConflict c : conflicts) {
            if (c.getConflictingTask1() != null) conflictedTaskNames.add(c.getConflictingTask1());
            if (c.getConflictingTask2() != null) conflictedTaskNames.add(c.getConflictingTask2());
        }

        LocalDate today = LocalDate.now();
        List<ProjectPerformanceDto> results = new ArrayList<>();

        for (Project p : scopedProjects) {
            List<Task> tasks = taskRepository.findByProjectId(p.getId());
            int totalTasks = tasks.size();
            int completedTasks = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            int overdueTasks = (int) tasks.stream()
                    .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                    .filter(t -> (t.getDeadline() != null && t.getDeadline().isBefore(today)) ||
                                 (t.getPlannedFinishDate() != null && t.getPlannedFinishDate().isBefore(today)))
                    .count();

            // Critical Path & Slack analysis
            boolean hasCriticalPathDelay = false;
            try {
                List<TaskDependency> deps = dependencyRepository.findBySuccessorTaskProjectId(p.getId());
                CriticalPathResult cpm = criticalPathService.calculate(p.getStartDate(), tasks, deps);
                Set<Long> cpIds = cpm.getCriticalTaskIds();
                hasCriticalPathDelay = tasks.stream()
                        .filter(t -> cpIds.contains(t.getId()) && t.getStatus() != TaskStatus.COMPLETED)
                        .anyMatch(t -> t.getPlannedFinishDate() != null && t.getPlannedFinishDate().isBefore(today));
            } catch (Exception e) {
                log.debug("CPM calculation skipped for project {}: {}", p.getId(), e.getMessage());
            }

            // Vendor delay flag
            List<TaskDelay> delays = delayRepository.findByProjectId(p.getId());
            boolean hasVendorDelay = delays.stream()
                    .anyMatch(d -> d.getDelayReason() != null && d.getDelayReason().toLowerCase().contains("vendor"));

            // Variance calculation
            LocalDate plannedDeadline = p.getDeadline() != null ? p.getDeadline() : p.getExpectedCompletionDate();
            LocalDate forecastOrActualFinish = p.getExpectedCompletionDate();
            
            // Check outcomes if completed
            if (p.getStatus() == ProjectStatus.COMPLETED) {
                List<TaskOutcomeRecord> outcomes = outcomeRepository.findByProjectId(p.getId());
                if (!outcomes.isEmpty()) {
                    forecastOrActualFinish = outcomes.stream()
                            .map(TaskOutcomeRecord::getActualFinishDate)
                            .max(LocalDate::compareTo)
                            .orElse(forecastOrActualFinish);
                }
            } else if (forecastOrActualFinish == null) {
                // Latest planned finish of tasks
                forecastOrActualFinish = tasks.stream()
                        .map(Task::getPlannedFinishDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(plannedDeadline);
            }

            Integer varianceDays = null;
            if (plannedDeadline != null && forecastOrActualFinish != null) {
                varianceDays = workingDayCalculator.calculateWorkingDaysBetween(plannedDeadline, forecastOrActualFinish);
                if (forecastOrActualFinish.isBefore(plannedDeadline)) {
                    varianceDays = -varianceDays;
                }
            }

            String varianceType = (p.getStatus() == ProjectStatus.COMPLETED) ? "ACTUAL_VARIANCE" : "FORECAST_VARIANCE";

            String delayStatus;
            if (p.getStatus() == ProjectStatus.COMPLETED) {
                delayStatus = (varianceDays != null && varianceDays <= 0) ? "COMPLETED_ON_TIME" : "COMPLETED_LATE";
            } else if (p.getStatus() == ProjectStatus.CANCELLED) {
                delayStatus = "CANCELLED";
            } else if (p.getStatus() == ProjectStatus.ON_HOLD) {
                delayStatus = "ON_HOLD";
            } else if (varianceDays != null && varianceDays > 3) {
                delayStatus = "CRITICAL_DELAY";
            } else if (varianceDays != null && varianceDays > 0) {
                delayStatus = "AT_RISK";
            } else {
                delayStatus = "ON_SCHEDULE";
            }

            int progress = p.getProgress() != null ? p.getProgress() :
                    (totalTasks > 0 ? (int) Math.round((double) completedTasks / totalTasks * 100.0) : 0);

            results.add(ProjectPerformanceDto.builder()
                    .projectId(p.getId())
                    .projectName(p.getName())
                    .clientId(p.getClient() != null ? p.getClient().getId() : null)
                    .clientName(p.getClient() != null ? p.getClient().getName() : "Internal")
                    .managerId(p.getManager() != null ? p.getManager().getId() : null)
                    .managerName(p.getManager() != null ? p.getManager().getUsername() : "Unassigned")
                    .status(p.getStatus() != null ? p.getStatus().name() : "PLANNED")
                    .priority(p.getPriority() != null ? p.getPriority().name() : "MEDIUM")
                    .startDate(p.getStartDate())
                    .deadline(p.getDeadline())
                    .expectedCompletionDate(p.getExpectedCompletionDate())
                    .actualFinishDate(p.getStatus() == ProjectStatus.COMPLETED ? forecastOrActualFinish : null)
                    .scheduleVarianceDays(varianceDays)
                    .varianceType(varianceType)
                    .delayStatus(delayStatus)
                    .totalTasks(totalTasks)
                    .completedTasks(completedTasks)
                    .overdueTasks(overdueTasks)
                    .progressPercent(progress)
                    .hasActiveConflict(tasks.stream().anyMatch(t -> conflictedTaskNames.contains(t.getName())))
                    .hasCriticalPathDelay(hasCriticalPathDelay)
                    .hasVendorDelay(hasVendorDelay)
                    .mlRiskStatus(totalTasks >= 10 ? "LOW" : "NOT_READY")
                    .mlRiskScore(null)
                    .build());
        }

        return results;
    }

    /**
     * Resource Utilization and Capacity Breakdown
     */
    @Transactional(readOnly = true)
    public ResourceUtilizationSummaryDto getResourceUtilization(User user, AnalyticsFilter filter) {
        if (user != null && user.getRole() == Role.CLIENT) {
            // Clients are not authorized to view internal worker capacities
            return ResourceUtilizationSummaryDto.builder()
                    .totalWorkers(0)
                    .workerDetails(Collections.emptyList())
                    .build();
        }

        List<Worker> allWorkers;
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        if (tenantId != null) {
            allWorkers = workerRepository.findByOrganizationId(tenantId);
        } else {
            allWorkers = workerRepository.findAll();
        }
        List<Task> allDbTasks = taskRepository.findAll();
        List<TaskAssignment> allDbAssignments = assignmentRepository.findAll();
        List<ResourceConflict> allConflicts = conflictDetector.detectConflicts(allDbTasks, allDbAssignments);
        Map<String, Long> conflictsByWorkerName = allConflicts.stream()
                .filter(c -> c.getResource() != null)
                .collect(Collectors.groupingBy(ResourceConflict::getResource, Collectors.counting()));

        // Active assignments
        List<TaskAssignment> assignments = assignmentRepository.findAll();
        Map<Long, List<TaskAssignment>> workerAssignments = assignments.stream()
                .filter(a -> a.getWorker() != null && a.getAssignmentType() == AssignmentType.WORKER)
                .collect(Collectors.groupingBy(a -> a.getWorker().getId()));

        LocalDate start = (filter != null && filter.getStartDate() != null) ? filter.getStartDate() : LocalDate.now().minusDays(15);
        LocalDate end = (filter != null && filter.getEndDate() != null) ? filter.getEndDate() : LocalDate.now().plusDays(15);
        int availableWorkingDays = Math.max(1, workingDayCalculator.calculateWorkingDaysBetween(start, end));

        int totalWorkers = allWorkers.size();
        int availableWorkers = 0;
        int unavailableWorkers = 0;
        int overallocatedWorkers = 0;
        int underutilizedWorkers = 0;
        int idleWorkers = 0;

        int sumAssignedHours = 0;
        int sumAvailableHours = 0;

        List<ResourceUtilizationSummaryDto.WorkerUtilizationItemDto> workerList = new ArrayList<>();

        for (Worker w : allWorkers) {
            int dailyCap = w.getDailyCapacity() != null ? w.getDailyCapacity() : 8;
            int totalAvailHours = availableWorkingDays * dailyCap;

            List<TaskAssignment> wAssigns = workerAssignments.getOrDefault(w.getId(), Collections.emptyList());
            int activeAssignCount = (int) wAssigns.stream()
                    .filter(a -> a.getTask() != null && a.getTask().getStatus() != TaskStatus.COMPLETED)
                    .count();

            // Assigned working days: sum of estimated duration of non-completed tasks
            int assignedDays = wAssigns.stream()
                    .filter(a -> a.getTask() != null && a.getTask().getStatus() != TaskStatus.COMPLETED)
                    .mapToInt(a -> a.getTask().getEstimatedDuration() != null ? a.getTask().getEstimatedDuration() : 1)
                    .sum();

            int assignedHours = assignedDays * dailyCap;
            double utilPercent = totalAvailHours > 0 ? (double) assignedHours / totalAvailHours * 100.0 : 0.0;
            utilPercent = Math.round(utilPercent * 10.0) / 10.0;

            String utilStatus;
            if (w.getStatus() == WorkerStatus.UNAVAILABLE) {
                unavailableWorkers++;
                utilStatus = "UNAVAILABLE";
            } else {
                availableWorkers++;
                sumAvailableHours += totalAvailHours;
                sumAssignedHours += assignedHours;

                if (assignedHours == 0) {
                    idleWorkers++;
                    utilStatus = "IDLE";
                } else if (utilPercent > 100.0) {
                    overallocatedWorkers++;
                    utilStatus = "OVERALLOCATED";
                } else if (utilPercent < 70.0) {
                    underutilizedWorkers++;
                    utilStatus = "UNDERUTILIZED";
                } else {
                    utilStatus = "OPTIMAL";
                }
            }

            int conflictCount = conflictsByWorkerName.getOrDefault(w.getName(), 0L).intValue();
            List<String> projNames = wAssigns.stream()
                    .map(a -> a.getTask() != null && a.getTask().getProject() != null ? a.getTask().getProject().getName() : null)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            workerList.add(ResourceUtilizationSummaryDto.WorkerUtilizationItemDto.builder()
                    .workerId(w.getId())
                    .workerName(w.getName())
                    .email(w.getEmail())
                    .skills(w.getSkills())
                    .status(w.getStatus() != null ? w.getStatus().name() : "AVAILABLE")
                    .dailyCapacityHours(dailyCap)
                    .activeAssignmentsCount(activeAssignCount)
                    .assignedWorkingDays(assignedDays)
                    .availableWorkingDaysInPeriod(availableWorkingDays)
                    .assignedHours(assignedHours)
                    .availableHours(totalAvailHours)
                    .utilizationPercent(utilPercent)
                    .utilizationStatus(utilStatus)
                    .activeConflictCount(conflictCount)
                    .assignedProjectNames(projNames)
                    .build());
        }

        Double overallUtilization = sumAvailableHours > 0 ? (double) sumAssignedHours / sumAvailableHours * 100.0 : null;
        if (overallUtilization != null) {
            overallUtilization = Math.round(overallUtilization * 10.0) / 10.0;
        }

        return ResourceUtilizationSummaryDto.builder()
                .totalWorkers(totalWorkers)
                .availableWorkers(availableWorkers)
                .unavailableWorkers(unavailableWorkers)
                .overallocatedWorkers(overallocatedWorkers)
                .underutilizedWorkers(underutilizedWorkers)
                .idleWorkers(idleWorkers)
                .overallUtilizationPercent(overallUtilization)
                .totalAssignedHours(sumAssignedHours)
                .totalAvailableHours(sumAvailableHours)
                .workerDetails(workerList)
                .build();
    }

    /**
     * Vendor Performance Analytics & Delivery Reliability
     */
    @Transactional(readOnly = true)
    public VendorAnalyticsSummaryDto getVendorPerformance(User user, AnalyticsFilter filter) {
        if (user != null && user.getRole() == Role.CLIENT) {
            // Clients are not authorized to view internal vendor scorecards
            return VendorAnalyticsSummaryDto.builder()
                    .totalVendors(0)
                    .vendorDetails(Collections.emptyList())
                    .build();
        }

        List<Vendor> allVendors;
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        if (tenantId != null) {
            allVendors = vendorRepository.findByOrganizationId(tenantId);
        } else {
            allVendors = vendorRepository.findAll();
        }
        List<TaskAssignment> vendorAssignments = assignmentRepository.findAll().stream()
                .filter(a -> a.getVendor() != null && a.getAssignmentType() == AssignmentType.VENDOR)
                .collect(Collectors.toList());

        Map<Long, List<TaskAssignment>> byVendor = vendorAssignments.stream()
                .collect(Collectors.groupingBy(a -> a.getVendor().getId()));

        int totalDeliveriesOverall = 0;
        int onTimeDeliveriesOverall = 0;
        int lateDeliveriesOverall = 0;
        int outstandingCommitmentsOverall = 0;
        double sumDelayDays = 0.0;

        List<VendorAnalyticsSummaryDto.VendorPerformanceItemDto> vendorItems = new ArrayList<>();

        for (Vendor v : allVendors) {
            List<TaskAssignment> vAssigns = byVendor.getOrDefault(v.getId(), Collections.emptyList());

            int completedDeliveries = 0;
            int onTimeDeliveries = 0;
            int lateDeliveries = 0;
            int outstanding = 0;
            double vTotalDelayDays = 0.0;
            Map<String, Integer> delayBuckets = new LinkedHashMap<>();
            delayBuckets.put("ON_TIME", 0);
            delayBuckets.put("1-3_DAYS", 0);
            delayBuckets.put("4-7_DAYS", 0);
            delayBuckets.put("OVER_7_DAYS", 0);

            for (TaskAssignment a : vAssigns) {
                Task t = a.getTask();
                if (t == null) continue;

                if (t.getStatus() == TaskStatus.COMPLETED) {
                    completedDeliveries++;
                    LocalDate planned = t.getPlannedFinishDate() != null ? t.getPlannedFinishDate() : t.getDeadline();
                    LocalDate actual = t.getActualFinishDate();

                    if (planned != null && actual != null) {
                        int delay = Math.max(0, workingDayCalculator.calculateWorkingDaysBetween(planned, actual));
                        vTotalDelayDays += delay;
                        if (actual.isAfter(planned)) {
                            lateDeliveries++;
                            if (delay <= 3) {
                                delayBuckets.put("1-3_DAYS", delayBuckets.get("1-3_DAYS") + 1);
                            } else if (delay <= 7) {
                                delayBuckets.put("4-7_DAYS", delayBuckets.get("4-7_DAYS") + 1);
                            } else {
                                delayBuckets.put("OVER_7_DAYS", delayBuckets.get("OVER_7_DAYS") + 1);
                            }
                        } else {
                            onTimeDeliveries++;
                            delayBuckets.put("ON_TIME", delayBuckets.get("ON_TIME") + 1);
                        }
                    } else {
                        // Count as on-time if no recorded slip
                        onTimeDeliveries++;
                        delayBuckets.put("ON_TIME", delayBuckets.get("ON_TIME") + 1);
                    }
                } else {
                    outstanding++;
                }
            }

            Double vOnTimeRate = completedDeliveries > 0 ? (double) onTimeDeliveries / completedDeliveries * 100.0 : null;
            if (vOnTimeRate != null) {
                vOnTimeRate = Math.round(vOnTimeRate * 10.0) / 10.0;
            }
            Double vAvgDelay = completedDeliveries > 0 ? Math.round((vTotalDelayDays / completedDeliveries) * 10.0) / 10.0 : 0.0;

            totalDeliveriesOverall += completedDeliveries;
            onTimeDeliveriesOverall += onTimeDeliveries;
            lateDeliveriesOverall += lateDeliveries;
            outstandingCommitmentsOverall += outstanding;
            sumDelayDays += vTotalDelayDays;

            vendorItems.add(VendorAnalyticsSummaryDto.VendorPerformanceItemDto.builder()
                    .vendorId(v.getId())
                    .vendorName(v.getName())
                    .company(v.getCompany())
                    .services(v.getServices())
                    .status(v.getStatus())
                    .totalDeliveries(completedDeliveries)
                    .onTimeDeliveries(onTimeDeliveries)
                    .lateDeliveries(lateDeliveries)
                    .onTimeRatePercent(vOnTimeRate)
                    .averageDelayDays(vAvgDelay)
                    .outstandingCommitments(outstanding)
                    .sampleSizeObservations(completedDeliveries)
                    .delayDistribution(delayBuckets)
                    .build());
        }

        Double overallOnTimeRate = totalDeliveriesOverall > 0
                ? Math.round(((double) onTimeDeliveriesOverall / totalDeliveriesOverall * 100.0) * 10.0) / 10.0
                : null;
        Double overallAvgDelay = totalDeliveriesOverall > 0
                ? Math.round((sumDelayDays / totalDeliveriesOverall) * 10.0) / 10.0
                : null;

        return VendorAnalyticsSummaryDto.builder()
                .totalVendors(allVendors.size())
                .activeVendors((int) allVendors.stream().filter(v -> "ACTIVE".equalsIgnoreCase(v.getStatus())).count())
                .totalRecordedDeliveries(totalDeliveriesOverall)
                .totalOnTimeDeliveries(onTimeDeliveriesOverall)
                .totalLateDeliveries(lateDeliveriesOverall)
                .overallOnTimeRatePercent(overallOnTimeRate)
                .averageDeliveryDelayDays(overallAvgDelay)
                .totalOutstandingCommitments(outstandingCommitmentsOverall)
                .vendorDetails(vendorItems)
                .build();
    }

    /**
     * Portfolio Deadline & Risk Analysis
     */
    @Transactional(readOnly = true)
    public PortfolioDeadlineRiskSummaryDto getDeadlineRiskSummary(User user, AnalyticsFilter filter) {
        List<ProjectPerformanceDto> projects = getProjectsPerformance(user, filter);

        int meeting = 0;
        int missing = 0;
        int withConflicts = 0;
        int withCriticalDelays = 0;
        int withLowSlack = 0;
        int withVendorDelays = 0;
        int needingAttention = 0;

        List<Long> attentionIds = new ArrayList<>();
        List<Long> missingIds = new ArrayList<>();

        for (ProjectPerformanceDto p : projects) {
            if ("COMPLETED_ON_TIME".equals(p.getDelayStatus()) || "ON_SCHEDULE".equals(p.getDelayStatus())) {
                meeting++;
            } else if ("CRITICAL_DELAY".equals(p.getDelayStatus()) || "AT_RISK".equals(p.getDelayStatus()) || "COMPLETED_LATE".equals(p.getDelayStatus())) {
                missing++;
                missingIds.add(p.getProjectId());
            }

            if (p.isHasActiveConflict()) withConflicts++;
            if (p.isHasCriticalPathDelay()) withCriticalDelays++;
            if (p.isHasVendorDelay()) withVendorDelays++;
            if (p.getScheduleVarianceDays() != null && p.getScheduleVarianceDays() > 0) withLowSlack++;

            if (p.isHasActiveConflict() || p.isHasCriticalPathDelay() || p.isHasVendorDelay() || "CRITICAL_DELAY".equals(p.getDelayStatus())) {
                needingAttention++;
                attentionIds.add(p.getProjectId());
            }
        }

        return PortfolioDeadlineRiskSummaryDto.builder()
                .projectsMeetingDeadline(meeting)
                .projectsMissingDeadline(missing)
                .projectsWithConflicts(withConflicts)
                .projectsWithCriticalPathDelays(withCriticalDelays)
                .projectsWithLowSlack(withLowSlack)
                .projectsAffectedByVendorDelays(withVendorDelays)
                .projectsRequiringAttention(needingAttention)
                .projectsNeedingAttentionIds(attentionIds)
                .projectsMissingDeadlineIds(missingIds)
                .build();
    }

    /**
     * Historical Trends Analysis
     */
    @Transactional(readOnly = true)
    public AnalyticsTrendsDto getAnalyticsTrends(User user, AnalyticsFilter filter) {
        List<Project> scopedProjects = getScopedProjects(user, filter);
        Set<Long> projectIds = scopedProjects.stream().map(Project::getId).collect(Collectors.toSet());

        List<TaskOutcomeRecord> outcomes = outcomeRepository.findAll().stream()
                .filter(o -> o.getProject() != null && projectIds.contains(o.getProject().getId()))
                .collect(Collectors.toList());

        // Group completed tasks and projects by Month (last 6 months)
        YearMonth currentYm = YearMonth.now();
        List<AnalyticsTrendsDto.MonthlyTrendPointDto> points = new ArrayList<>();
        DateTimeFormatter ymFormat = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            YearMonth targetYm = currentYm.minusMonths(i);
            String ymStr = targetYm.format(ymFormat);

            List<TaskOutcomeRecord> monthOutcomes = outcomes.stream()
                    .filter(o -> o.getActualFinishDate() != null && YearMonth.from(o.getActualFinishDate()).equals(targetYm))
                    .collect(Collectors.toList());

            int completedTasks = monthOutcomes.size();

            // Completed projects in this month
            List<Project> monthCompletedProjects = scopedProjects.stream()
                    .filter(p -> p.getStatus() == ProjectStatus.COMPLETED && p.getExpectedCompletionDate() != null && YearMonth.from(p.getExpectedCompletionDate()).equals(targetYm))
                    .collect(Collectors.toList());

            int compProjCount = monthCompletedProjects.size();
            int onTimeCount = (int) monthCompletedProjects.stream()
                    .filter(p -> p.getDeadline() != null && !p.getExpectedCompletionDate().isAfter(p.getDeadline()))
                    .count();

            Double avgVar = null;
            if (!monthOutcomes.isEmpty()) {
                avgVar = monthOutcomes.stream()
                        .mapToInt(o -> o.getVarianceWorkingDays() != null ? o.getVarianceWorkingDays() : 0)
                        .average()
                        .orElse(0.0);
                avgVar = Math.round(avgVar * 10.0) / 10.0;
            }

            points.add(AnalyticsTrendsDto.MonthlyTrendPointDto.builder()
                    .month(ymStr)
                    .completedProjects(compProjCount)
                    .onTimeProjects(onTimeCount)
                    .avgVarianceDays(avgVar)
                    .completedTasks(completedTasks)
                    .build());
        }

        // Delay reason distribution
        Map<String, Long> reasons = new HashMap<>();
        // From task delays
        List<TaskDelay> delays = delayRepository.findAll().stream()
                .filter(d -> d.getTask() != null && d.getTask().getProject() != null && projectIds.contains(d.getTask().getProject().getId()))
                .collect(Collectors.toList());
        for (TaskDelay d : delays) {
            String r = d.getDelayReason() != null ? d.getDelayReason() : "Unspecified";
            reasons.put(r, reasons.getOrDefault(r, 0L) + 1);
        }
        // Also combine with outcome primary reasons
        for (TaskOutcomeRecord o : outcomes) {
            if (o.getIsDelayed() && o.getPrimaryDelayReason() != null) {
                reasons.put(o.getPrimaryDelayReason(), reasons.getOrDefault(o.getPrimaryDelayReason(), 0L) + 1);
            }
        }

        Map<String, Long> statusDist = scopedProjects.stream()
                .collect(Collectors.groupingBy(p -> p.getStatus() != null ? p.getStatus().name() : "PLANNED", Collectors.counting()));

        Map<String, Long> priorityDist = scopedProjects.stream()
                .collect(Collectors.groupingBy(p -> p.getPriority() != null ? p.getPriority().name() : "MEDIUM", Collectors.counting()));

        return AnalyticsTrendsDto.builder()
                .monthlyTrends(points)
                .delayReasonsBreakdown(reasons)
                .projectStatusDistribution(statusDist)
                .priorityDistribution(priorityDist)
                .build();
    }

    /**
     * CSV Data Export with Formula Injection Protection
     */
    @Transactional(readOnly = true)
    public String exportAnalyticsCsv(User user, String reportType, AnalyticsFilter filter) {
        StringBuilder sb = new StringBuilder();

        if ("projects".equalsIgnoreCase(reportType)) {
            List<ProjectPerformanceDto> list = getProjectsPerformance(user, filter);
            sb.append("Project ID,Project Name,Client,Manager,Status,Priority,Start Date,Deadline,Expected Completion,Variance (Days),Variance Type,Delay Status,Total Tasks,Completed Tasks,Overdue Tasks,Progress %\n");
            for (ProjectPerformanceDto p : list) {
                sb.append(escape(String.valueOf(p.getProjectId()))).append(",");
                sb.append(escape(p.getProjectName())).append(",");
                sb.append(escape(p.getClientName())).append(",");
                sb.append(escape(p.getManagerName())).append(",");
                sb.append(escape(p.getStatus())).append(",");
                sb.append(escape(p.getPriority())).append(",");
                sb.append(escape(formatDate(p.getStartDate()))).append(",");
                sb.append(escape(formatDate(p.getDeadline()))).append(",");
                sb.append(escape(formatDate(p.getExpectedCompletionDate()))).append(",");
                sb.append(escape(p.getScheduleVarianceDays() != null ? String.valueOf(p.getScheduleVarianceDays()) : "N/A")).append(",");
                sb.append(escape(p.getVarianceType())).append(",");
                sb.append(escape(p.getDelayStatus())).append(",");
                sb.append(p.getTotalTasks()).append(",");
                sb.append(p.getCompletedTasks()).append(",");
                sb.append(p.getOverdueTasks()).append(",");
                sb.append(p.getProgressPercent()).append("\n");
            }
        } else if ("resources".equalsIgnoreCase(reportType)) {
            ResourceUtilizationSummaryDto summary = getResourceUtilization(user, filter);
            sb.append("Worker ID,Worker Name,Email,Skills,Status,Daily Capacity (h),Active Assignments,Assigned Days,Available Days,Utilization %,Utilization Status,Conflicts\n");
            for (ResourceUtilizationSummaryDto.WorkerUtilizationItemDto w : summary.getWorkerDetails()) {
                sb.append(escape(String.valueOf(w.getWorkerId()))).append(",");
                sb.append(escape(w.getWorkerName())).append(",");
                sb.append(escape(w.getEmail())).append(",");
                sb.append(escape(w.getSkills())).append(",");
                sb.append(escape(w.getStatus())).append(",");
                sb.append(w.getDailyCapacityHours()).append(",");
                sb.append(w.getActiveAssignmentsCount()).append(",");
                sb.append(w.getAssignedWorkingDays()).append(",");
                sb.append(w.getAvailableWorkingDaysInPeriod()).append(",");
                sb.append(w.getUtilizationPercent() != null ? w.getUtilizationPercent() : 0.0).append(",");
                sb.append(escape(w.getUtilizationStatus())).append(",");
                sb.append(w.getActiveConflictCount()).append("\n");
            }
        } else if ("vendors".equalsIgnoreCase(reportType)) {
            VendorAnalyticsSummaryDto summary = getVendorPerformance(user, filter);
            sb.append("Vendor ID,Vendor Name,Company,Services,Status,Total Deliveries,On-Time Deliveries,Late Deliveries,On-Time Rate %,Average Delay (Days),Outstanding Commitments\n");
            for (VendorAnalyticsSummaryDto.VendorPerformanceItemDto v : summary.getVendorDetails()) {
                sb.append(escape(String.valueOf(v.getVendorId()))).append(",");
                sb.append(escape(v.getVendorName())).append(",");
                sb.append(escape(v.getCompany())).append(",");
                sb.append(escape(v.getServices())).append(",");
                sb.append(escape(v.getStatus())).append(",");
                sb.append(v.getTotalDeliveries()).append(",");
                sb.append(v.getOnTimeDeliveries()).append(",");
                sb.append(v.getLateDeliveries()).append(",");
                sb.append(v.getOnTimeRatePercent() != null ? v.getOnTimeRatePercent() + "%" : "N/A").append(",");
                sb.append(v.getAverageDelayDays()).append(",");
                sb.append(v.getOutstandingCommitments()).append("\n");
            }
        } else {
            // Portfolio summary
            PortfolioKpiSummaryDto kpi = getPortfolioKpiSummary(user, filter);
            sb.append("Metric,Value\n");
            sb.append(escape("Total Projects")).append(",").append(kpi.getTotalProjects()).append("\n");
            sb.append(escape("Active Projects")).append(",").append(kpi.getActiveProjects()).append("\n");
            sb.append(escape("Completed Projects")).append(",").append(kpi.getCompletedProjects()).append("\n");
            sb.append(escape("On-Hold Projects")).append(",").append(kpi.getOnHoldProjects()).append("\n");
            sb.append(escape("Forecast to Miss Deadlines")).append(",").append(kpi.getProjectsForecastToMissDeadlines()).append("\n");
            sb.append(escape("Total Overdue Tasks")).append(",").append(kpi.getTotalOverdueTasks()).append("\n");
            sb.append(escape("Historical On-Time Delivery Rate")).append(",").append(kpi.getHistoricalOnTimeDeliveryRatePercent() != null ? kpi.getHistoricalOnTimeDeliveryRatePercent() + "%" : "N/A").append("\n");
            sb.append(escape("Average Schedule Variance (Days)")).append(",").append(kpi.getAverageScheduleVarianceDays() != null ? kpi.getAverageScheduleVarianceDays() : "N/A").append("\n");
            sb.append(escape("Overall Resource Utilization")).append(",").append(kpi.getResourceUtilizationPercent() != null ? kpi.getResourceUtilizationPercent() + "%" : "N/A").append("\n");
            sb.append(escape("Vendor Delivery Reliability")).append(",").append(kpi.getVendorDeliveryReliabilityPercent() != null ? kpi.getVendorDeliveryReliabilityPercent() + "%" : "N/A").append("\n");
        }

        return sb.toString();
    }

    private List<Project> getScopedProjects(User user, AnalyticsFilter filter) {
        List<Project> all;
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        if (tenantId != null) {
            all = projectRepository.findByOrganizationId(tenantId);
        } else {
            all = projectRepository.findAll();
        }

        // 1. Role-based scoping
        if (user != null && user.getRole() == Role.CLIENT) {
            all = all.stream()
                    .filter(p -> p.getClient() != null && user.getEmail() != null &&
                                 user.getEmail().equalsIgnoreCase(p.getClient().getEmail()))
                    .collect(Collectors.toList());
        } else if (user != null && user.getRole() == Role.PROJECT_MANAGER) {
            all = all.stream()
                    .filter(p -> p.getManager() != null && p.getManager().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        }

        // 2. Query filter criteria
        if (filter != null) {
            if (filter.getProjectId() != null) {
                all = all.stream().filter(p -> p.getId().equals(filter.getProjectId())).collect(Collectors.toList());
            }
            if (filter.getClientId() != null) {
                all = all.stream().filter(p -> p.getClient() != null && p.getClient().getId().equals(filter.getClientId())).collect(Collectors.toList());
            }
            if (filter.getManagerId() != null) {
                all = all.stream().filter(p -> p.getManager() != null && p.getManager().getId().equals(filter.getManagerId())).collect(Collectors.toList());
            }
            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                all = all.stream().filter(p -> p.getStatus() != null && p.getStatus().name().equalsIgnoreCase(filter.getStatus())).collect(Collectors.toList());
            }
            if (filter.getPriority() != null && !filter.getPriority().isBlank()) {
                all = all.stream().filter(p -> p.getPriority() != null && p.getPriority().name().equalsIgnoreCase(filter.getPriority())).collect(Collectors.toList());
            }
            if (filter.getStartDate() != null) {
                all = all.stream().filter(p -> p.getStartDate() != null && !p.getStartDate().isBefore(filter.getStartDate())).collect(Collectors.toList());
            }
            if (filter.getEndDate() != null) {
                all = all.stream().filter(p -> p.getDeadline() != null && !p.getDeadline().isAfter(filter.getEndDate())).collect(Collectors.toList());
            }
        }

        return all;
    }

    private boolean isProjectForecastToMiss(Project p) {
        if (p.getDeadline() == null) return false;
        if (p.getExpectedCompletionDate() != null && p.getExpectedCompletionDate().isAfter(p.getDeadline())) {
            return true;
        }
        // Check latest planned finish date of active tasks
        List<Task> tasks = taskRepository.findByProjectId(p.getId());
        return tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .map(Task::getPlannedFinishDate)
                .filter(Objects::nonNull)
                .anyMatch(d -> d.isAfter(p.getDeadline()));
    }

    /**
     * Sanitizes CSV values against spreadsheet formula/macro injection and standard quoting.
     */
    private String escape(String value) {
        if (value == null) return "\"\"";
        String sanitized = value;
        // Formula injection defense: prepend single quote if cell starts with risky chars
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("-") ||
            sanitized.startsWith("@") || sanitized.startsWith("\t") || sanitized.startsWith("\r")) {
            sanitized = "'" + sanitized;
        }
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.toString() : "";
    }
}
