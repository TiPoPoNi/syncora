package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 20)
    private String theme = "SYSTEM"; // 'LIGHT', 'DARK', 'SYSTEM'

    @Column(length = 10)
    private String language = "en"; // 'en', 'hi'

    @Column(name = "time_zone", length = 50)
    private String timeZone = "Asia/Kolkata";

    @Column(name = "date_format", length = 30)
    private String dateFormat = "YYYY-MM-DD";

    @Column(name = "first_day_of_week", length = 20)
    private String firstDayOfWeek = "MONDAY";

    @Column(name = "text_size", length = 20)
    private String textSize = "DEFAULT"; // 'DEFAULT', 'LARGE'

    @Column(name = "display_density", length = 20)
    private String displayDensity = "COMFORTABLE"; // 'COMFORTABLE', 'COMPACT'

    @Column(name = "reduced_motion")
    private Boolean reducedMotion = false;

    @Column(name = "high_contrast_focus")
    private Boolean highContrastFocus = false;

    @Column(name = "in_app_notifications")
    private Boolean inAppNotifications = true;

    @Column(name = "email_notifications")
    private Boolean emailNotifications = false;

    @Column(name = "task_assignments_notify")
    private Boolean taskAssignmentsNotify = true;

    @Column(name = "deadline_reminders_notify")
    private Boolean deadlineRemindersNotify = true;

    @Column(name = "project_risk_alerts_notify")
    private Boolean projectRiskAlertsNotify = true;

    @Column(name = "approval_requests_notify")
    private Boolean approvalRequestsNotify = true;

    @Column(name = "vendor_delay_notify")
    private Boolean vendorDelayNotify = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (theme == null) theme = "SYSTEM";
        if (language == null) language = "en";
        if (timeZone == null) timeZone = "Asia/Kolkata";
        if (dateFormat == null) dateFormat = "YYYY-MM-DD";
        if (firstDayOfWeek == null) firstDayOfWeek = "MONDAY";
        if (textSize == null) textSize = "DEFAULT";
        if (displayDensity == null) displayDensity = "COMFORTABLE";
        if (reducedMotion == null) reducedMotion = false;
        if (highContrastFocus == null) highContrastFocus = false;
        if (inAppNotifications == null) inAppNotifications = true;
        if (emailNotifications == null) emailNotifications = false;
        if (taskAssignmentsNotify == null) taskAssignmentsNotify = true;
        if (deadlineRemindersNotify == null) deadlineRemindersNotify = true;
        if (projectRiskAlertsNotify == null) projectRiskAlertsNotify = true;
        if (approvalRequestsNotify == null) approvalRequestsNotify = true;
        if (vendorDelayNotify == null) vendorDelayNotify = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getTimezone() {
        return timeZone;
    }

    public void setTimezone(String tz) {
        this.timeZone = tz;
    }

    public Boolean getApprovalNotifications() {
        return approvalRequestsNotify;
    }

    public void setApprovalNotifications(Boolean val) {
        this.approvalRequestsNotify = val;
    }

    public Boolean getTaskUpdatesNotifications() {
        return taskAssignmentsNotify;
    }

    public void setTaskUpdatesNotifications(Boolean val) {
        this.taskAssignmentsNotify = val;
    }

    public Boolean getDeadlineRemindersNotifications() {
        return deadlineRemindersNotify;
    }

    public void setDeadlineRemindersNotifications(Boolean val) {
        this.deadlineRemindersNotify = val;
    }
}
