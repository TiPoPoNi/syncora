package com.prs.dto;

import com.prs.entity.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesDto {
    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean deadlineRemindersEnabled;
    private Boolean taskAssignmentsEnabled;
    private Boolean delayAlertsEnabled;
    private Boolean recommendationAlertsEnabled;

    public static NotificationPreferencesDto fromEntity(NotificationPreference entity) {
        return NotificationPreferencesDto.builder()
                .inAppEnabled(entity.getInAppEnabled())
                .emailEnabled(entity.getEmailEnabled())
                .deadlineRemindersEnabled(entity.getDeadlineRemindersEnabled())
                .taskAssignmentsEnabled(entity.getTaskAssignmentsEnabled())
                .delayAlertsEnabled(entity.getDelayAlertsEnabled())
                .recommendationAlertsEnabled(entity.getRecommendationAlertsEnabled())
                .build();
    }
}
