package com.prs.controller;

import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.ProjectScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
public class DemoDataController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final OptimizationRecommendationRepository recommendationRepository;
    private final OptimizationAuditLogRepository auditLogRepository;
    private final ProjectScheduleService projectScheduleService;
    private final TaskOutcomeRecordRepository taskOutcomeRecordRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Value("${app.auth.dev-mode:true}")
    private boolean devMode;

    @PostMapping("/reset")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> resetDemoEnvironment() {
        if (!devMode) {
            log.warn("Blocked attempt to invoke demo reset in production mode");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "Demo environment reset is disabled in production mode."
            ));
        }

        log.info("Resetting local demo environment to canonical demonstration state...");

        // 1. Reset pending / applied recommendations & audit logs
        recommendationRepository.deleteAll();
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();

        // 2. Find Rahul Kumar and Vikram Mehta
        Optional<Worker> rahulOpt = workerRepository.findAll().stream()
                .filter(w -> "Rahul Kumar".equalsIgnoreCase(w.getName())).findFirst();
        Optional<Worker> vikramOpt = workerRepository.findAll().stream()
                .filter(w -> "Vikram Mehta".equalsIgnoreCase(w.getName())).findFirst();

        if (rahulOpt.isPresent()) {
            Worker rahul = rahulOpt.get();
            // Re-assign Backend Development (Task 3) back to Rahul Kumar to recreate the deliberate overlap conflict
            Optional<Task> backendTask = taskRepository.findAll().stream()
                    .filter(t -> "Backend Development".equalsIgnoreCase(t.getName())).findFirst();
            if (backendTask.isPresent()) {
                List<TaskAssignment> assignments = taskAssignmentRepository.findByTaskId(backendTask.get().getId());
                if (!assignments.isEmpty()) {
                    TaskAssignment a = assignments.get(0);
                    a.setWorker(rahul);
                    a.setVendor(null);
                    a.setAssignmentType(AssignmentType.WORKER);
                    taskAssignmentRepository.save(a);
                }
            }
        }

        // 3. Recalculate all project schedules
        for (Project p : projectRepository.findAll()) {
            try {
                projectScheduleService.recalculateSchedule(p.getId());
            } catch (Exception e) {
                log.warn("Could not recalculate schedule for project {}: {}", p.getId(), e.getMessage());
            }
        }

        // 4. Ensure demo historical task outcomes exist
        if (taskOutcomeRecordRepository.count() == 0 && !projectRepository.findAll().isEmpty()) {
            Project p = projectRepository.findAll().get(0);
            Worker rahul = workerRepository.findAll().stream().filter(w -> "Rahul Kumar".equalsIgnoreCase(w.getName())).findFirst().orElse(null);
            Worker priya = workerRepository.findAll().stream().filter(w -> "Priya Singh".equalsIgnoreCase(w.getName())).findFirst().orElse(null);
            Worker sneha = workerRepository.findAll().stream().filter(w -> "Sneha Gupta".equalsIgnoreCase(w.getName())).findFirst().orElse(null);
            Worker amit = workerRepository.findAll().stream().filter(w -> "Amit Patel".equalsIgnoreCase(w.getName())).findFirst().orElse(null);

            taskOutcomeRecordRepository.save(TaskOutcomeRecord.builder()
                    .project(p).taskName("Database Architecture Setup")
                    .plannedStartDate(LocalDate.of(2026, 9, 1)).plannedFinishDate(LocalDate.of(2026, 9, 4))
                    .actualStartDate(LocalDate.of(2026, 9, 1)).actualFinishDate(LocalDate.of(2026, 9, 4))
                    .plannedDuration(4).actualDuration(4).varianceWorkingDays(0).isDelayed(false)
                    .assignedWorker(rahul).wasCriticalPath(true).build());

            taskOutcomeRecordRepository.save(TaskOutcomeRecord.builder()
                    .project(p).taskName("Initial Wireframes")
                    .plannedStartDate(LocalDate.of(2026, 9, 7)).plannedFinishDate(LocalDate.of(2026, 9, 9))
                    .actualStartDate(LocalDate.of(2026, 9, 7)).actualFinishDate(LocalDate.of(2026, 9, 9))
                    .plannedDuration(3).actualDuration(3).varianceWorkingDays(0).isDelayed(false)
                    .assignedWorker(priya).wasCriticalPath(false).build());

            taskOutcomeRecordRepository.save(TaskOutcomeRecord.builder()
                    .project(p).taskName("Security Compliance Review")
                    .plannedStartDate(LocalDate.of(2026, 9, 10)).plannedFinishDate(LocalDate.of(2026, 9, 14))
                    .actualStartDate(LocalDate.of(2026, 9, 10)).actualFinishDate(LocalDate.of(2026, 9, 14))
                    .plannedDuration(3).actualDuration(3).varianceWorkingDays(0).isDelayed(false)
                    .assignedWorker(sneha).wasCriticalPath(true).build());

            taskOutcomeRecordRepository.save(TaskOutcomeRecord.builder()
                    .project(p).taskName("Cloud Infrastructure Provisioning")
                    .plannedStartDate(LocalDate.of(2026, 9, 15)).plannedFinishDate(LocalDate.of(2026, 9, 17))
                    .actualStartDate(LocalDate.of(2026, 9, 15)).actualFinishDate(LocalDate.of(2026, 9, 22))
                    .plannedDuration(3).actualDuration(6).varianceWorkingDays(3).isDelayed(true)
                    .primaryDelayReason("Cloud provider quota increase delay")
                    .totalDelaysReported(1).assignedWorker(sneha).wasCriticalPath(true).build());

            taskOutcomeRecordRepository.save(TaskOutcomeRecord.builder()
                    .project(p).taskName("Test Suite Scaffold")
                    .plannedStartDate(LocalDate.of(2026, 9, 23)).plannedFinishDate(LocalDate.of(2026, 9, 25))
                    .actualStartDate(LocalDate.of(2026, 9, 23)).actualFinishDate(LocalDate.of(2026, 9, 25))
                    .plannedDuration(3).actualDuration(3).varianceWorkingDays(0).isDelayed(false)
                    .assignedWorker(amit).wasCriticalPath(false).build());
        }

        // 4. Seed Canonical Workflow Notifications for Demo
        Optional<User> adminOpt = userRepository.findByUsername("admin");
        if (adminOpt.isPresent()) {
            User admin = adminOpt.get();
            Project p = projectRepository.findAll().stream().findFirst().orElse(null);
            Long projId = p != null ? p.getId() : 1L;

            Notification n1 = new Notification();
            n1.setUser(admin);
            n1.setEventType(NotificationEventType.RESOURCE_CONFLICT);
            n1.setTitle("Resource Overlap Conflict Detected");
            n1.setMessage("Worker 'Rahul Kumar' is scheduled concurrently on overlapping tasks in E-Commerce Website Launch.");
            n1.setRelatedProjectId(projId);
            n1.setIsRead(false);
            n1.setDeliveryChannel(NotificationDeliveryChannel.IN_APP);
            n1.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            n1.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(25));
            notificationRepository.save(n1);

            Notification n2 = new Notification();
            n2.setUser(admin);
            n2.setEventType(NotificationEventType.TASK_DEADLINE_APPROACHING);
            n2.setTitle("Upcoming Milestone Deadline");
            n2.setMessage("Task 'Frontend Development' in project E-Commerce Website Launch is approaching planned finish date.");
            n2.setRelatedProjectId(projId);
            n2.setIsRead(false);
            n2.setDeliveryChannel(NotificationDeliveryChannel.IN_APP);
            n2.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            n2.setCreatedAt(java.time.LocalDateTime.now().minusHours(2));
            notificationRepository.save(n2);

            Notification n3 = new Notification();
            n3.setUser(admin);
            n3.setEventType(NotificationEventType.VENDOR_DELAY);
            n3.setTitle("Vendor Delivery Delay Reported");
            n3.setMessage("Cloud provider quota delay (3 days) reported on infrastructure provisioning.");
            n3.setRelatedProjectId(projId);
            n3.setIsRead(true);
            n3.setDeliveryChannel(NotificationDeliveryChannel.IN_APP);
            n3.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            n3.setCreatedAt(java.time.LocalDateTime.now().minusDays(1));
            n3.setReadAt(java.time.LocalDateTime.now().minusHours(5));
            notificationRepository.save(n3);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Local demo environment successfully reset to canonical pre-optimization state.",
                "timestamp", LocalDate.now().toString()
        ));
    }
}
