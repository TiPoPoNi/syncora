package com.prs.integration.dto;

import com.prs.entity.CalendarProvider;
import com.prs.entity.CalendarSyncStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarIntegrationStatusResponse {
    private CalendarProvider provider;
    private String providerName;
    private boolean configured;
    private CalendarSyncStatus status;
    private String accountEmail;
    private String calendarId;
    private LocalDateTime lastSyncAt;
    private String lastError;
    private int mappedEventCount;
    private String authorizationUrl;
}
