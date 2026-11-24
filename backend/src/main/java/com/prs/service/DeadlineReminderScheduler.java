package com.prs.service;

import com.prs.entity.*;
import com.prs.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final ReminderDeduplicationLogRepository deduplicationLogRepository;
    private final NotificationService notificationService;

    @Value("${app.reminders.enabled:true}")
    private boolean remindersEnabled;

    /**
     * Executes scheduled deadline reminder evaluation.
     * Evaluates task and project deadlines against deduplication logs.
     * Can also be called manually in tests.
     * Runs every 15 minutes by default when scheduling is enabled.
     */
    @Scheduled(cron = "${app.reminders.cron:0 */15 * * * *}")
    @Transactional
    public int processDeadlineReminders() {
        if (!remindersEnabled) {
            log.debug("Deadline reminder processing is disabled in configuration.");
            return 0;
        }

        LocalDate today = LocalDate.now();
        int remindersSent = 0;

        // 1. Process Task Deadlines
        List<Task> activeTasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.BLOCKED)
                .filter(t -> (t.getPlannedFinishDate() != null || t.getDeadline() != null))
                .toList();

        for (Task task : activeTasks) {
            LocalDate finishDate = task.getPlannedFinishDate() != null ? task.getPlannedFinishDate() : task.getDeadline();
            long daysUntilFinish = ChronoUnit.DAYS.between(today, finishDate);

            // Determine reminder interval type
            String reminderType = null;
            NotificationEventType eventType = null;
            String title = null;
            String message = null;

            if (daysUntilFinish < 0) {
                // Overdue
                reminderType = "OVERDUE";
                eventType = NotificationEventType.TASK_OVERDUE;
                title = "Task Overdue: " + task.getName();
                message = String.format("Task '%s' in project '%s' was due on %s (%d days ago). Please update task progress.",
                        task.getName(), task.getProject().getName(), finishDate, Math.abs(daysUntilFinish));
            } else if (daysUntilFinish <= 1) {
                // Due within 24 hours
                reminderType = "DUE_24H";
                eventType = NotificationEventType.TASK_DEADLINE_APPROACHING;
                title = "Task Due Soon: " + task.getName();
                message = String.format("Task '%s' in project '%s' is due tomorrow or today (%s).",
                        task.getName(), task.getProject().getName(), finishDate);
            } else if (daysUntilFinish <= 3) {
                // Due within 3 days
                reminderType = "DUE_3D";
                eventType = NotificationEventType.TASK_DEADLINE_APPROACHING;
                title = "Upcoming Task Deadline: " + task.getName();
                message = String.format("Task '%s' in project '%s' is due in %d days (%s).",
                        task.getName(), task.getProject().getName(), daysUntilFinish, finishDate);
            }

            if (reminderType != null) {
                // Find target recipients: Assigned workers with User accounts, and Project Manager
                Set<User> recipients = new HashSet<>();
                List<TaskAssignment> assignments = assignmentRepository.findByTaskId(task.getId());
                for (TaskAssignment assignment : assignments) {
                    if (assignment.getWorker() != null && assignment.getWorker().getUser() != null) {
                        recipients.add(assignment.getWorker().getUser());
                    }
                }
                if (task.getProject().getManager() != null) {
                    recipients.add(task.getProject().getManager());
                }

                for (User recipient : recipients) {
                    // Check deduplication
                    boolean alreadySent = deduplicationLogRepository
                            .existsByTargetTypeAndTargetIdAndReminderTypeAndEventDateAndRecipientUserId(
                                    "TASK", task.getId(), reminderType, today, recipient.getId());

                    if (!alreadySent) {
                        notificationService.sendNotification(
                                recipient, eventType, title, message, task.getProject().getId(), task.getId());

                        // Record deduplication
                        ReminderDeduplicationLog logEntry = new ReminderDeduplicationLog();
                        logEntry.setTargetType("TASK");
                        logEntry.setTargetId(task.getId());
                        logEntry.setReminderType(reminderType);
                        logEntry.setEventDate(today);
                        logEntry.setRecipientUserId(recipient.getId());
                        deduplicationLogRepository.save(logEntry);
                        remindersSent++;
                    }
                }
            }
        }

        // 2. Process Project Deadlines
        List<Project> activeProjects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.CANCELLED)
                .filter(p -> p.getDeadline() != null && p.getManager() != null)
                .toList();

        for (Project project : activeProjects) {
            long daysToDeadline = ChronoUnit.DAYS.between(today, project.getDeadline());
            if (daysToDeadline <= 5 && daysToDeadline >= 0) {
                String reminderType = "PROJECT_DEADLINE";
                User manager = project.getManager();

                boolean alreadySent = deduplicationLogRepository
                        .existsByTargetTypeAndTargetIdAndReminderTypeAndEventDateAndRecipientUserId(
                                "PROJECT", project.getId(), reminderType, today, manager.getId());

                if (!alreadySent) {
                    String title = "Project Deadline Approaching: " + project.getName();
                    String msg = String.format("Project '%s' target delivery deadline is in %d days (%s).",
                            project.getName(), daysToDeadline, project.getDeadline());

                    notificationService.sendNotification(
                            manager, NotificationEventType.PROJECT_DEADLINE_APPROACHING, title, msg, project.getId(), null);

                    ReminderDeduplicationLog logEntry = new ReminderDeduplicationLog();
                    logEntry.setTargetType("PROJECT");
                    logEntry.setTargetId(project.getId());
                    logEntry.setReminderType(reminderType);
                    logEntry.setEventDate(today);
                    logEntry.setRecipientUserId(manager.getId());
                    deduplicationLogRepository.save(logEntry);
                    remindersSent++;
                }
            }
        }

        if (remindersSent > 0) {
            log.info("Deadline reminder run completed: {} new notifications dispatched with duplicate prevention.", remindersSent);
        }
        return remindersSent;
    }
}
