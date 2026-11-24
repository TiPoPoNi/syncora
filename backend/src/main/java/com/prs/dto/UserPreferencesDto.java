package com.prs.dto;

import com.prs.entity.UserPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {
    private String theme;
    private String language;
    private String timezone;
    private String dateFormat;
    private String firstDayOfWeek;
    private String textSize;
    private String displayDensity;
    private Boolean reducedMotion;
    private Boolean highContrastFocus;
    private Boolean inAppNotifications;
    private Boolean emailNotifications;
    private Boolean approvalNotifications;
    private Boolean taskUpdatesNotifications;
    private Boolean deadlineRemindersNotifications;

    public static UserPreferencesDto fromEntity(UserPreference entity) {
        if (entity == null) {
            return UserPreferencesDto.builder()
                    .theme("SYSTEM")
                    .language("en")
                    .timezone("UTC")
                    .dateFormat("YYYY-MM-DD")
                    .firstDayOfWeek("MONDAY")
                    .textSize("DEFAULT")
                    .displayDensity("COMFORTABLE")
                    .reducedMotion(false)
                    .highContrastFocus(false)
                    .inAppNotifications(true)
                    .emailNotifications(false)
                    .approvalNotifications(true)
                    .taskUpdatesNotifications(true)
                    .deadlineRemindersNotifications(true)
                    .build();
        }

        return UserPreferencesDto.builder()
                .theme(entity.getTheme() != null ? entity.getTheme() : "SYSTEM")
                .language(entity.getLanguage() != null ? entity.getLanguage() : "en")
                .timezone(entity.getTimezone() != null ? entity.getTimezone() : "Asia/Kolkata")
                .dateFormat(entity.getDateFormat() != null ? entity.getDateFormat() : "YYYY-MM-DD")
                .firstDayOfWeek(entity.getFirstDayOfWeek() != null ? entity.getFirstDayOfWeek() : "MONDAY")
                .textSize(entity.getTextSize() != null ? entity.getTextSize() : "DEFAULT")
                .displayDensity(entity.getDisplayDensity() != null ? entity.getDisplayDensity() : "COMFORTABLE")
                .reducedMotion(entity.getReducedMotion() != null ? entity.getReducedMotion() : false)
                .highContrastFocus(entity.getHighContrastFocus() != null ? entity.getHighContrastFocus() : false)
                .inAppNotifications(entity.getInAppNotifications() != null ? entity.getInAppNotifications() : true)
                .emailNotifications(entity.getEmailNotifications() != null ? entity.getEmailNotifications() : false)
                .approvalNotifications(entity.getApprovalNotifications() != null ? entity.getApprovalNotifications() : true)
                .taskUpdatesNotifications(entity.getTaskUpdatesNotifications() != null ? entity.getTaskUpdatesNotifications() : true)
                .deadlineRemindersNotifications(entity.getDeadlineRemindersNotifications() != null ? entity.getDeadlineRemindersNotifications() : true)
                .build();
    }
}
