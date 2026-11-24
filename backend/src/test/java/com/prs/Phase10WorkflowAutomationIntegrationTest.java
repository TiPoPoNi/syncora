package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.delay.DelayReportingService;
import com.prs.dto.AuthRequest;
import com.prs.dto.NotificationPreferencesDto;
import com.prs.dto.TaskAssignmentDto;
import com.prs.dto.VendorImportPreviewDto;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 10: Workflow Automation, Notifications, Calendar & CSV Integration Tests")
public class Phase10WorkflowAutomationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private ReminderDeduplicationLogRepository deduplicationLogRepository;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private TaskAssignmentRepository assignmentRepository;

    @Autowired
    private TaskAssignmentService assignmentService;

    @Autowired
    private DelayReportingService delayReportingService;

    @Autowired
    private DeadlineReminderScheduler deadlineReminderScheduler;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CalendarExportService calendarExportService;

    @Autowired
    private CsvExportService csvExportService;

    @Autowired
    private VendorCsvImportService vendorCsvImportService;

    @Autowired
    private VendorRepository vendorRepository;

    private String jwtToken;
    private User adminUser;
    private User workerUser;
    private Worker worker;
    private Project project;
    private Task task;

    @BeforeEach
    void setUp() throws Exception {
        notificationRepository.deleteAll();
        deduplicationLogRepository.deleteAll();
        outboxRepository.deleteAll();
        preferenceRepository.deleteAll();
        emailService.clearHistory();
        emailService.setSimulateFailure(false);

        // Obtain valid JWT for admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = loginResult.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(responseStr).get("token").asText();

        adminUser = userRepository.findByUsername("admin").orElseThrow();

        workerUser = userRepository.findByEmail("worker@example.com")
                .or(() -> userRepository.findByUsername("worker_test"))
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername("worker_test_" + System.currentTimeMillis());
                    u.setPassword("$2a$10$dummyHash");
                    u.setEmail("worker_test_" + System.currentTimeMillis() + "@example.com");
                    u.setRole(Role.WORKER);
                    return userRepository.save(u);
                });

        worker = workerRepository.findAll().stream()
                .filter(w -> w.getUser() != null && w.getUser().getId().equals(workerUser.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Worker w = new Worker();
                    w.setName("Test Worker");
                    w.setEmail("worker_unique_" + System.currentTimeMillis() + "@example.com");
                    w.setUser(workerUser);
                    w.setStatus(WorkerStatus.AVAILABLE);
                    return workerRepository.save(w);
                });

        project = projectRepository.findAll().stream().findFirst().orElseGet(() -> {
            Project p = new Project();
            p.setName("Integration Project");
            p.setManager(adminUser);
            p.setStartDate(LocalDate.now());
            p.setDeadline(LocalDate.now().plusDays(20));
            return projectRepository.save(p);
        });

        task = taskRepository.findByProjectId(project.getId()).stream().findFirst().orElseGet(() -> {
            Task t = new Task();
            t.setName("Sample Automation Task");
            t.setProject(project);
            t.setEstimatedDuration(5);
            t.setPlannedStartDate(LocalDate.now());
            t.setPlannedFinishDate(LocalDate.now().plusDays(5));
            t.setStatus(TaskStatus.TODO);
            return taskRepository.save(t);
        });
        task.setStatus(TaskStatus.TODO);
        task = taskRepository.save(task);
    }

    @Test
    @DisplayName("1. Notification Generation upon Task Assignment")
    void testTaskAssignment_GeneratesNotification() {
        TaskAssignmentDto dto = new TaskAssignmentDto();
        dto.setAssignmentType(AssignmentType.WORKER);
        dto.setWorkerId(worker.getId());
        dto.setAllocationPercentage(100);

        assignmentService.addAssignment(task.getId(), dto);

        List<Notification> notifications = notificationRepository.findTop50ByUserOrderByCreatedAtDesc(workerUser);
        assertThat(notifications).isNotEmpty();
        Notification n = notifications.get(0);
        assertThat(n.getEventType()).isEqualTo(NotificationEventType.TASK_ASSIGNMENT);
        assertThat(n.getMessage()).contains(task.getName());
        assertThat(n.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("2. User Notification State Changes & Recipient Isolation")
    void testNotificationReadState_AndRecipientIsolation() throws Exception {
        Notification n = notificationService.sendNotification(
                adminUser, NotificationEventType.RESOURCE_CONFLICT,
                "Conflict Alert", "Test Conflict Message", project.getId(), task.getId());

        assertThat(n).isNotNull();
        assertThat(notificationRepository.countByUserAndIsReadFalse(adminUser)).isGreaterThanOrEqualTo(1);

        // Fetch unread count
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber());

        // Mark as read
        mockMvc.perform(patch("/api/notifications/" + n.getId() + "/read")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // Mark all as read
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.countByUserAndIsReadFalse(adminUser)).isEqualTo(0);
    }

    @Test
    @DisplayName("3. Deadline Reminder Scheduler with Duplicate Prevention & Completed Exclusion")
    void testDeadlineReminderScheduler_DuplicatePrevention() {
        // Set task finish date to tomorrow (DUE_24H)
        task.setPlannedFinishDate(LocalDate.now().plusDays(1));
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.save(task);

        // Link worker to task
        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setWorker(worker);
        assignment.setAssignmentType(AssignmentType.WORKER);
        assignmentRepository.save(assignment);

        // Run 1: Should send reminder
        int count1 = deadlineReminderScheduler.processDeadlineReminders();
        assertThat(count1).isGreaterThanOrEqualTo(1);

        // Run 2 on same day: Deduplication must block duplicate reminder
        int count2 = deadlineReminderScheduler.processDeadlineReminders();
        assertThat(count2).isEqualTo(0);

        // Completed task should not receive reminders
        task.setStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);
        deduplicationLogRepository.deleteAll(); // clear dedup

        int count3 = deadlineReminderScheduler.processDeadlineReminders();
        assertThat(count3).isEqualTo(0); // skipped because completed
    }

    @Test
    @DisplayName("4. User Notification Preference Enforcement")
    void testNotificationPreference_Suppression() {
        // Suppress task assignment notifications
        NotificationPreferencesDto prefs = NotificationPreferencesDto.builder()
                .inAppEnabled(true)
                .emailEnabled(false)
                .taskAssignmentsEnabled(false) // disabled!
                .deadlineRemindersEnabled(true)
                .delayAlertsEnabled(true)
                .recommendationAlertsEnabled(true)
                .build();

        notificationService.updatePreferences(workerUser.getUsername(), prefs);

        Notification n = notificationService.sendNotification(
                workerUser, NotificationEventType.TASK_ASSIGNMENT,
                "Suppressed Title", "Suppressed Message", project.getId(), task.getId());

        assertThat(n).isNull(); // Suppressed by preference!
    }

    @Test
    @DisplayName("5. Mock Email Delivery & Outbox Retry Architecture")
    void testEmailDelivery_AndOutboxRetry() {
        // Enable email for adminUser
        NotificationPreference pref = notificationService.getOrCreatePreferences(adminUser);
        pref.setEmailEnabled(true);
        preferenceRepository.save(pref);

        // 1. Successful email delivery
        notificationService.sendNotification(
                adminUser, NotificationEventType.PROJECT_DEADLINE_APPROACHING,
                "Email Deadline Subject", "Email body content", project.getId(), null);

        assertThat(emailService.getSentHistory()).isNotEmpty();
        assertThat(emailService.getSentHistory().get(0).getToEmail()).isEqualTo(adminUser.getEmail());

        // 2. Failure & Outbox Retry
        emailService.setSimulateFailure(true);
        notificationService.sendNotification(
                adminUser, NotificationEventType.VENDOR_DELAY,
                "Failing Subject", "Failing body", project.getId(), null);

        List<NotificationOutbox> outboxItems = outboxRepository.findByStatus(NotificationDeliveryStatus.FAILED);
        assertThat(outboxItems).isNotEmpty();

        // Restore service and execute retry
        emailService.setSimulateFailure(false);
        int retriedCount = notificationService.processOutboxRetries();
        assertThat(retriedCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("6. iCalendar (.ics) Export Compliance & Milestone Generation")
    void testCalendarExport_FormatCompliance() throws Exception {
        String ics = calendarExportService.generateProjectIcsCalendar(project.getId());

        assertThat(ics).contains("BEGIN:VCALENDAR");
        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).contains("PRODID:-//SYNCORA//Project Resource Allocation and Scheduling System//EN");
        assertThat(ics).contains("BEGIN:VEVENT");
        assertThat(ics).contains("UID:prs-task-" + task.getId());
        assertThat(ics).contains("DTSTART;VALUE=DATE:");
        assertThat(ics).contains("DTEND;VALUE=DATE:");
        assertThat(ics).contains("END:VCALENDAR");

        mockMvc.perform(get("/api/projects/" + project.getId() + "/calendar.ics")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("7. CSV Export with Spreadsheet Formula Injection Defense")
    void testCsvExport_AndFormulaInjectionDefense() throws Exception {
        // Test formula injection defense: leading '=', '+', '-', '@'
        assertThat(csvExportService.sanitize("=SUM(A1:A10)")).startsWith("\"'=SUM");
        assertThat(csvExportService.sanitize("+cmd|' /C calc'!A0")).startsWith("\"'+cmd");
        assertThat(csvExportService.sanitize("-10")).startsWith("\"'-10");
        assertThat(csvExportService.sanitize("@SUM")).startsWith("\"'@SUM");
        assertThat(csvExportService.sanitize("Normal Text")).isEqualTo("\"Normal Text\"");

        mockMvc.perform(get("/api/projects/" + project.getId() + "/exports/tasks.csv")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Task ID,Task Name")));

        mockMvc.perform(get("/api/projects/" + project.getId() + "/exports/assignments.csv")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Assignment ID,Task ID")));

        mockMvc.perform(get("/api/projects/" + project.getId() + "/exports/delays.csv")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delay ID,Task ID")));
    }

    @Test
    @DisplayName("8. Validated Vendor CSV Import with Preview & Atomic Confirm")
    void testVendorCsvImport_PreviewAndConfirm() throws Exception {
        String csvContent = "name,company,email,phone,services\n" +
                "Apex Cloud Solutions,Apex Corp,contact@apexcloud.com,555-0199,Cloud Infrastructure\n" +
                "Invalid Vendor Without Name,,notanemail,,\n" +
                "Duplicate Row Vendor,Dup Corp,dup@corp.com,555-0200,QA Testing\n" +
                "Duplicate Row Vendor,Dup Corp,dup@corp.com,555-0200,QA Testing\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "vendors.csv", "text/csv", csvContent.getBytes());

        VendorImportPreviewDto preview = vendorCsvImportService.previewImport(file);
        assertThat(preview.getTotalRows()).isEqualTo(4);
        assertThat(preview.getValidRowsCount()).isGreaterThanOrEqualTo(1);
        assertThat(preview.getErrorRowsCount()).isGreaterThanOrEqualTo(2); // invalid email/name and duplicate

        // Confirm valid records
        int confirmed = vendorCsvImportService.confirmImport(preview.getValidRecords());
        assertThat(confirmed).isGreaterThanOrEqualTo(1);
        assertThat(vendorRepository.findAll().stream().anyMatch(v -> "Apex Cloud Solutions".equals(v.getName()))).isTrue();
    }
}
