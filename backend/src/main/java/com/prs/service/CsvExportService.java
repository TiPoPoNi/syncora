package com.prs.service;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.entity.*;
import com.prs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CsvExportService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskDelayRepository delayRepository;
    private final CriticalPathService criticalPathService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public String exportTasksCsv(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (com.prs.tenant.TenantContext.getTenantId() != null && project.getOrganization() != null
                && !project.getOrganization().getId().equals(com.prs.tenant.TenantContext.getTenantId())) {
            throw new com.prs.tenant.TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        Set<Long> criticalIds = Set.of();
        try {
            LocalDate pStart = project.getStartDate() != null ? project.getStartDate() : LocalDate.now();
            CriticalPathResult cpm = criticalPathService.calculate(pStart, tasks, dependencies);
            criticalIds = cpm.getCriticalTaskIds();
        } catch (Exception ignored) {}

        StringBuilder csv = new StringBuilder();
        csv.append("Task ID,Task Name,Project Name,Priority,Status,Duration Days,Start Date,Finish Date,Critical Path,Assignees\n");

        for (Task t : tasks) {
            boolean isCritical = criticalIds.contains(t.getId());
            List<TaskAssignment> assignments = assignmentRepository.findByTaskId(t.getId());
            StringBuilder assignees = new StringBuilder();
            for (TaskAssignment a : assignments) {
                if (assignees.length() > 0) assignees.append("; ");
                if (a.getWorker() != null) assignees.append(a.getWorker().getName());
                else if (a.getVendor() != null) assignees.append("Vendor: ").append(a.getVendor().getName());
            }

            LocalDate startDate = t.getPlannedStartDate() != null ? t.getPlannedStartDate() : t.getStartDate();
            LocalDate finishDate = t.getPlannedFinishDate() != null ? t.getPlannedFinishDate() : t.getDeadline();

            csv.append(sanitize(String.valueOf(t.getId()))).append(",")
               .append(sanitize(t.getName())).append(",")
               .append(sanitize(project.getName())).append(",")
               .append(sanitize(t.getPriority().name())).append(",")
               .append(sanitize(t.getStatus().name())).append(",")
               .append(t.getEstimatedDuration() != null ? t.getEstimatedDuration() : 1).append(",")
               .append(startDate != null ? startDate.format(DATE_FMT) : "").append(",")
               .append(finishDate != null ? finishDate.format(DATE_FMT) : "").append(",")
               .append(isCritical ? "YES" : "NO").append(",")
               .append(sanitize(assignees.toString())).append("\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public String exportAssignmentsCsv(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (com.prs.tenant.TenantContext.getTenantId() != null && project.getOrganization() != null
                && !project.getOrganization().getId().equals(com.prs.tenant.TenantContext.getTenantId())) {
            throw new com.prs.tenant.TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        StringBuilder csv = new StringBuilder();
        csv.append("Assignment ID,Task ID,Task Name,Resource Type,Resource Name,Allocation %,Assignment Status\n");

        for (Task t : tasks) {
            List<TaskAssignment> assignments = assignmentRepository.findByTaskId(t.getId());
            for (TaskAssignment a : assignments) {
                String resourceType = a.getAssignmentType() != null ? a.getAssignmentType().name() : "WORKER";
                String resourceName = a.getWorker() != null ? a.getWorker().getName() :
                        (a.getVendor() != null ? a.getVendor().getName() : "Unassigned");

                csv.append(sanitize(String.valueOf(a.getId()))).append(",")
                   .append(sanitize(String.valueOf(t.getId()))).append(",")
                   .append(sanitize(t.getName())).append(",")
                   .append(sanitize(resourceType)).append(",")
                   .append(sanitize(resourceName)).append(",")
                   .append(a.getAllocationPercentage() != null ? a.getAllocationPercentage() : 100).append(",")
                   .append(sanitize(a.getStatus() != null ? a.getStatus().name() : "TODO")).append("\n");
            }
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public String exportDelaysCsv(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (com.prs.tenant.TenantContext.getTenantId() != null && project.getOrganization() != null
                && !project.getOrganization().getId().equals(com.prs.tenant.TenantContext.getTenantId())) {
            throw new com.prs.tenant.TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        StringBuilder csv = new StringBuilder();
        csv.append("Delay ID,Task ID,Task Name,Delay Working Days,Delay Reason,Status,Reported Date\n");

        for (Task t : tasks) {
            List<TaskDelay> delays = delayRepository.findByTaskId(t.getId());
            for (TaskDelay d : delays) {
                csv.append(sanitize(String.valueOf(d.getId()))).append(",")
                   .append(sanitize(String.valueOf(t.getId()))).append(",")
                   .append(sanitize(t.getName())).append(",")
                   .append(d.getDelayDurationDays() != null ? d.getDelayDurationDays() : 0).append(",")
                   .append(sanitize(d.getDelayReason() != null ? d.getDelayReason() : "")).append(",")
                   .append(sanitize(d.getStatus() != null ? d.getStatus().name() : "REPORTED")).append(",")
                   .append(d.getReportedDate() != null ? d.getReportedDate().format(DATE_FMT) : "").append("\n");
            }
        }
        return csv.toString();
    }

    /**
     * Sanitizes CSV field content:
     * 1. Protects against CSV formula injection by prepending a single quote (') if the text
     *    starts with '=', '+', '-', '@', tab, or carriage return.
     * 2. Escapes double quotes by doubling them (" -> "").
     * 3. Wraps the value in double quotes if it contains commas, quotes, or newlines.
     */
    public String sanitize(String value) {
        if (value == null) return "\"\"";
        String val = value.trim();

        // Formula injection defense: prefix dangerous leading characters with a single quote
        if (!val.isEmpty()) {
            char first = val.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                val = "'" + val;
            }
        }

        // Escape internal quotes
        val = val.replace("\"", "\"\"");

        // Always wrap text fields in quotes for RFC 4180 compliance
        return "\"" + val + "\"";
    }
}
