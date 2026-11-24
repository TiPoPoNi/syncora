package com.prs.controller;

import com.prs.dto.DashboardDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor

public class DashboardController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final VendorRepository vendorRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ConflictDetector conflictDetector;
    private final OptimizationAuditLogRepository optimizationAuditLogRepository;
    private final UserRepository userRepository;

    @GetMapping
    public DashboardDto getDashboardStats(org.springframework.security.core.Authentication authentication) {
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        List<Project> allProjects;
        List<Worker> allWorkers;
        List<Vendor> allVendors;
        if (tenantId != null) {
            allProjects = projectRepository.findByOrganizationId(tenantId);
            allWorkers = workerRepository.findByOrganizationId(tenantId);
            allVendors = vendorRepository.findByOrganizationId(tenantId);
        } else {
            // Check if current user is explicitly a global platform administrator
            boolean isGlobalAdmin = false;
            if (authentication != null) {
                User currentUser = userRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);
                isGlobalAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;
            }
            if (isGlobalAdmin) {
                allProjects = projectRepository.findAll();
                allWorkers = workerRepository.findAll();
                allVendors = vendorRepository.findAll();
            } else {
                allProjects = Collections.emptyList();
                allWorkers = Collections.emptyList();
                allVendors = Collections.emptyList();
            }
        }

        Set<Long> projectIds = allProjects.stream().map(Project::getId).collect(Collectors.toSet());
        List<Task> allTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && projectIds.contains(t.getProject().getId()))
                .collect(Collectors.toList());
        List<TaskAssignment> allAssignments = taskAssignmentRepository.findAll().stream()
                .filter(a -> a.getTask() != null && a.getTask().getProject() != null && projectIds.contains(a.getTask().getProject().getId()))
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        LocalDate in30Days = today.plusDays(30);

        DashboardDto dto = new DashboardDto();
        dto.setTotalProjects(allProjects.size());
        dto.setTotalWorkers(allWorkers.size());
        dto.setTotalVendors(allVendors.size());

        long activeCount = allProjects.stream()
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED)
                .count();
        dto.setActiveProjects(activeCount);

        long completedCount = allProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.COMPLETED)
                .count();
        dto.setCompletedProjects(completedCount);

        // Delayed projects: expectedCompletionDate is after deadline
        List<Project> delayedProjectList = allProjects.stream()
                .filter(p -> p.getDeadline() != null && p.getExpectedCompletionDate() != null
                        && p.getExpectedCompletionDate().isAfter(p.getDeadline()))
                .collect(Collectors.toList());
        dto.setDelayedProjects(delayedProjectList.size());

        // Resource Conflicts across all assignments
        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(allTasks, allAssignments);
        dto.setResourceConflictsCount(conflicts.size());

        // Map conflicts to projects and conflicting workers
        Map<Long, List<String>> projectConflictDetails = new HashMap<>();
        Set<String> conflictingWorkerNames = new HashSet<>();

        // Group tasks by name to determine which project owns which conflicting task
        Map<String, List<Task>> tasksByName = allTasks.stream()
                .collect(Collectors.groupingBy(Task::getName));

        for (ResourceConflict c : conflicts) {
            if (c.getResource() != null) {
                conflictingWorkerNames.add(c.getResource());
            }

            // Link to project(s) of task1
            List<Task> t1List = tasksByName.getOrDefault(c.getConflictingTask1(), Collections.emptyList());
            for (Task t1 : t1List) {
                if (t1.getProject() != null) {
                    String desc = String.format("Resource '%s' has concurrent overlap on '%s' with '%s' (%s to %s)",
                            c.getResource(), c.getConflictingTask1(), c.getConflictingTask2(), c.getOverlapStart(), c.getOverlapEnd());
                    projectConflictDetails.computeIfAbsent(t1.getProject().getId(), k -> new ArrayList<>()).add(desc);
                }
            }
            // Link to project(s) of task2
            List<Task> t2List = tasksByName.getOrDefault(c.getConflictingTask2(), Collections.emptyList());
            for (Task t2 : t2List) {
                if (t2.getProject() != null) {
                    String desc = String.format("Resource '%s' has concurrent overlap on '%s' with '%s' (%s to %s)",
                            c.getResource(), c.getConflictingTask2(), c.getConflictingTask1(), c.getOverlapStart(), c.getOverlapEnd());
                    projectConflictDetails.computeIfAbsent(t2.getProject().getId(), k -> new ArrayList<>()).add(desc);
                }
            }
        }

        // Helper to transform project into summary DTO with accurate risk classification
        java.util.function.Function<Project, DashboardDto.DashboardProjectSummaryDto> toSummaryDto = p -> {
            DashboardDto.DashboardProjectSummaryDto ps = new DashboardDto.DashboardProjectSummaryDto();
            ps.setId(p.getId());
            ps.setName(p.getName());
            ps.setClientName(p.getClient() != null ? p.getClient().getName() : "Unassigned");
            ps.setStatus(p.getStatus() != null ? p.getStatus().name() : "PLANNED");
            ps.setProgress(p.getProgress() != null ? p.getProgress() : 0);
            ps.setDeadline(p.getDeadline());
            ps.setExpectedCompletionDate(p.getExpectedCompletionDate());

            boolean delayed = p.getDeadline() != null && p.getExpectedCompletionDate() != null
                    && p.getExpectedCompletionDate().isAfter(p.getDeadline());
            ps.setDelayed(delayed);

            List<String> conflictList = projectConflictDetails.getOrDefault(p.getId(), Collections.emptyList());
            boolean hasConflict = !conflictList.isEmpty();
            ps.setHasResourceConflict(hasConflict);
            ps.setConflictDetails(conflictList);

            boolean atRisk = delayed || hasConflict;
            ps.setAtRisk(atRisk);

            if (delayed && hasConflict) {
                ps.setRiskReason("Critical-path slippage & concurrent resource conflict");
            } else if (delayed) {
                ps.setRiskReason("Forecast delayed beyond deadline (calculated CPM)");
            } else if (hasConflict) {
                ps.setRiskReason(conflictList.get(0));
            } else {
                ps.setRiskReason("Tracking to schedule");
            }
            return ps;
        };

        // Projects at risk: delayed or has resource conflicts
        long atRiskCount = allProjects.stream()
                .filter(p -> {
                    boolean delayed = p.getDeadline() != null && p.getExpectedCompletionDate() != null
                            && p.getExpectedCompletionDate().isAfter(p.getDeadline());
                    boolean hasConflict = projectConflictDetails.containsKey(p.getId());
                    return delayed || hasConflict;
                }).count();
        dto.setProjectsAtRisk(atRiskCount);

        // Upcoming deadlines (projects with deadline in next 30 days)
        long upcoming = allProjects.stream()
                .filter(p -> p.getDeadline() != null
                        && !p.getDeadline().isBefore(today)
                        && !p.getDeadline().isAfter(in30Days)
                        && p.getStatus() != ProjectStatus.COMPLETED)
                .count();
        dto.setUpcomingDeadlines(upcoming);

        // Overdue tasks
        long overdue = allTasks.stream()
                .filter(t -> t.getDeadline() != null
                        && t.getDeadline().isBefore(today)
                        && t.getStatus() != TaskStatus.COMPLETED)
                .count();
        dto.setOverdueTasks(overdue);

        // All project summaries mapped
        List<DashboardDto.DashboardProjectSummaryDto> allSummaries = allProjects.stream()
                .map(toSummaryDto)
                .collect(Collectors.toList());

        // Projects requiring attention: any project at risk (delayed or has conflicts)
        List<DashboardDto.DashboardProjectSummaryDto> attentionList = allSummaries.stream()
                .filter(DashboardDto.DashboardProjectSummaryDto::isAtRisk)
                .sorted(Comparator.comparing(DashboardDto.DashboardProjectSummaryDto::isDelayed).reversed()
                        .thenComparing(DashboardDto.DashboardProjectSummaryDto::getId))
                .collect(Collectors.toList());
        dto.setProjectsRequiringAttention(attentionList);

        // Recent Projects Summary
        List<DashboardDto.DashboardProjectSummaryDto> recentSummaries = allSummaries.stream()
                .sorted(Comparator.comparing(DashboardDto.DashboardProjectSummaryDto::getId).reversed())
                .limit(5)
                .collect(Collectors.toList());
        dto.setRecentProjects(recentSummaries);

        // Upcoming Milestones / Critical Tasks
        List<DashboardDto.DashboardMilestoneDto> milestones = allTasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .sorted(Comparator.comparing(t -> t.getPlannedFinishDate() != null ? t.getPlannedFinishDate() : LocalDate.MAX))
                .limit(6)
                .map(t -> {
                    DashboardDto.DashboardMilestoneDto m = new DashboardDto.DashboardMilestoneDto();
                    m.setId(t.getId());
                    m.setTaskName(t.getName());
                    m.setProjectName(t.getProject() != null ? t.getProject().getName() : "Unknown");
                    m.setDeadline(t.getDeadline());
                    m.setPlannedFinishDate(t.getPlannedFinishDate());
                    m.setPriority(t.getPriority() != null ? t.getPriority().name() : "MEDIUM");
                    m.setStatus(t.getStatus() != null ? t.getStatus().name() : "TODO");

                    if (t.getDeadline() != null && t.getPlannedFinishDate() != null) {
                        boolean exceeded = t.getPlannedFinishDate().isAfter(t.getDeadline());
                        m.setDeadlineExceeded(exceeded);
                        if (exceeded) {
                            m.setDelayDays(java.time.temporal.ChronoUnit.DAYS.between(t.getDeadline(), t.getPlannedFinishDate()));
                        }
                    }
                    return m;
                }).collect(Collectors.toList());
        dto.setUpcomingMilestones(milestones);

        // Resource Workload
        Map<Long, Long> taskCountByWorker = allAssignments.stream()
                .filter(a -> a.getWorker() != null)
                .collect(Collectors.groupingBy(a -> a.getWorker().getId(), Collectors.counting()));

        List<DashboardDto.DashboardWorkerWorkloadDto> workloadList = allWorkers.stream()
                .map(w -> {
                    DashboardDto.DashboardWorkerWorkloadDto wl = new DashboardDto.DashboardWorkerWorkloadDto();
                    wl.setWorkerId(w.getId());
                    wl.setName(w.getName());
                    wl.setSkills(w.getSkills());

                    String roster = w.getStatus() != null ? w.getStatus().name() : "AVAILABLE";
                    wl.setStatus(roster);
                    wl.setRosterStatus(roster);

                    int count = taskCountByWorker.getOrDefault(w.getId(), 0L).intValue();
                    wl.setActiveAssignedTasks(count);

                    boolean inConflict = conflictingWorkerNames.contains(w.getName());
                    wl.setHasConflict(inConflict);

                    if ("UNAVAILABLE".equalsIgnoreCase(roster)) {
                        wl.setWorkloadStatus("UNAVAILABLE");
                        wl.setWorkloadSummary("Unavailable (Roster Inactive)");
                    } else if (inConflict) {
                        wl.setWorkloadStatus("OVERALLOCATED");
                        wl.setWorkloadSummary("Overallocated (" + count + " tasks - Schedule Conflict)");
                    } else if (count > 0) {
                        wl.setWorkloadStatus("ACTIVE");
                        wl.setWorkloadSummary(count == 1 ? "1 Active Task" : count + " Active Tasks");
                    } else {
                        wl.setWorkloadStatus("AVAILABLE");
                        wl.setWorkloadSummary("Available (0 Tasks)");
                    }
                    return wl;
                })
                .sorted(Comparator.comparing(DashboardDto.DashboardWorkerWorkloadDto::isHasConflict).reversed()
                        .thenComparing(Comparator.comparing(DashboardDto.DashboardWorkerWorkloadDto::getActiveAssignedTasks).reversed()))
                .limit(6)
                .collect(Collectors.toList());
        dto.setResourceWorkload(workloadList);

        // Recent Activities from project audit logs
        List<OptimizationAuditLog> auditLogs;
        if (!projectIds.isEmpty()) {
            auditLogs = optimizationAuditLogRepository.findAll().stream()
                    .filter(a -> a.getProject() != null && projectIds.contains(a.getProject().getId()))
                    .sorted(Comparator.comparing(OptimizationAuditLog::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(8)
                    .collect(Collectors.toList());
        } else {
            auditLogs = Collections.emptyList();
        }

        List<DashboardDto.DashboardActivityDto> activities = auditLogs.stream().map(a -> {
            DashboardDto.DashboardActivityDto act = new DashboardDto.DashboardActivityDto();
            act.setId(a.getId());
            act.setAction(a.getAction());
            act.setProjectName(a.getProject() != null ? a.getProject().getName() : "General");
            act.setPerformedBy(a.getPerformedBy());
            act.setDetails(a.getDetails());
            act.setTimestamp(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
            return act;
        }).collect(Collectors.toList());
        dto.setRecentActivities(activities);

        return dto;
    }
}
