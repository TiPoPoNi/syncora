package com.prs.integration.calendar;

import com.prs.entity.CalendarProvider;
import com.prs.integration.dto.CalendarEventDto;

public interface CalendarSyncProvider {
    CalendarProvider getProvider();
    String getDisplayName();
    boolean isConfigured();
    String buildAuthorizationUrl(String state);
    OAuthTokenResponse exchangeCodeForTokens(String code, String redirectUri);
    String createEvent(String accessToken, String calendarId, CalendarEventDto event);
    void updateEvent(String accessToken, String calendarId, String externalEventId, CalendarEventDto event);
    void deleteEvent(String accessToken, String calendarId, String externalEventId);
}
