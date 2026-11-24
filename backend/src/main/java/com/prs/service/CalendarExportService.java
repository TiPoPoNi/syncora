package com.prs.service;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.entity.Project;
import com.prs.entity.Task;
import com.prs.entity.TaskDependency;
import com.prs.repository.ProjectRepository;
import com.prs.repository.TaskDependencyRepository;
import com.prs.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CalendarExportService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final CriticalPathService criticalPathService;

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ICS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    @Transactional(readOnly = true)
    public String generateProjectIcsCalendar(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        if (com.prs.tenant.TenantContext.getTenantId() != null && project.getOrganization() != null
                && !project.getOrganization().getId().equals(com.prs.tenant.TenantContext.getTenantId())) {
            throw new com.prs.tenant.TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        Set<Long> criticalTaskIds = Set.of();
        try {
            LocalDate pStart = project.getStartDate() != null ? project.getStartDate() : LocalDate.now();
            CriticalPathResult cpm = criticalPathService.calculate(pStart, tasks, dependencies);
            criticalTaskIds = cpm.getCriticalTaskIds();
        } catch (Exception ignored) {}

        String nowUtc = LocalDateTime.now().format(ICS_TIMESTAMP);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//SYNCORA//Project Resource Allocation and Scheduling System//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:").append(escapeIcsText(project.getName())).append(" - SYNCORA Schedule\r\n");
        sb.append("X-WR-TIMEZONE:UTC\r\n");

        for (Task task : tasks) {
            LocalDate startDate = task.getPlannedStartDate() != null ? task.getPlannedStartDate() : task.getStartDate();
            LocalDate finishDate = task.getPlannedFinishDate() != null ? task.getPlannedFinishDate() : task.getDeadline();

            if (startDate == null || finishDate == null) continue;

            boolean isCritical = criticalTaskIds.contains(task.getId());
            String dtStart = startDate.format(ICS_DATE);
            // RFC 5545 end date for whole day events is exclusive (day after finish)
            String dtEnd = finishDate.plusDays(1).format(ICS_DATE);

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:prs-task-").append(task.getId()).append("@prs.system\r\n");
            sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(dtStart).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(dtEnd).append("\r\n");
            sb.append("SUMMARY:").append(escapeIcsText(task.getName()))
                    .append(isCritical ? " [CRITICAL PATH]" : "")
                    .append(" (").append(task.getPriority()).append(")\r\n");

            StringBuilder desc = new StringBuilder();
            desc.append("Project: ").append(project.getName()).append("\\n");
            desc.append("Status: ").append(task.getStatus()).append("\\n");
            desc.append("Duration: ").append(task.getEstimatedDuration() != null ? task.getEstimatedDuration() : 1).append(" working days\\n");
            desc.append("Critical Path: ").append(isCritical ? "YES" : "NO").append("\\n");
            if (task.getDescription() != null && !task.getDescription().isBlank()) {
                desc.append("Details: ").append(task.getDescription());
            }

            sb.append("DESCRIPTION:").append(escapeIcsText(desc.toString())).append("\r\n");
            sb.append("CATEGORIES:").append(isCritical ? "CRITICAL_PATH,TASK" : "TASK").append("\r\n");
            sb.append("STATUS:CONFIRMED\r\n");
            sb.append("END:VEVENT\r\n");
        }

        // Project Milestone / Deadline Event
        if (project.getDeadline() != null) {
            String deadlineStart = project.getDeadline().format(ICS_DATE);
            String deadlineEnd = project.getDeadline().plusDays(1).format(ICS_DATE);

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:prs-deadline-project-").append(project.getId()).append("@prs.system\r\n");
            sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(deadlineStart).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(deadlineEnd).append("\r\n");
            sb.append("SUMMARY:MILESTONE: ").append(escapeIcsText(project.getName())).append(" Target Deadline\r\n");
            sb.append("DESCRIPTION:Contractual completion deadline for project ").append(escapeIcsText(project.getName())).append("\r\n");
            sb.append("CATEGORIES:MILESTONE,DEADLINE\r\n");
            sb.append("STATUS:CONFIRMED\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String escapeIcsText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\r\n", "\\n")
                   .replace("\n", "\\n");
    }
}
