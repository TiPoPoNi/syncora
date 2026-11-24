package com.prs.integration.calendar;

import com.prs.entity.CalendarProvider;
import com.prs.integration.dto.CalendarEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class GoogleCalendarSyncProvider implements CalendarSyncProvider {

    @Value("${app.integrations.calendar.google.clientId:}")
    private String clientId;

    @Value("${app.integrations.calendar.google.clientSecret:}")
    private String clientSecret;

    @Value("${app.integrations.calendar.google.redirectUri:http://localhost:8080/api/integrations/calendars/GOOGLE_CALENDAR/callback}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    public void setCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public CalendarProvider getProvider() {
        return CalendarProvider.GOOGLE_CALENDAR;
    }

    @Override
    public String getDisplayName() {
        return "Google Calendar";
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        if (!isConfigured()) {
            return null;
        }
        String scope = "https://www.googleapis.com/auth/calendar.events";
        return String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent&state=%s",
                urlEncode(clientId),
                urlEncode(redirectUri),
                urlEncode(scope),
                urlEncode(state != null ? state : "")
        );
    }

    @Override
    public OAuthTokenResponse exchangeCodeForTokens(String code, String redirect) {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Calendar integration is not configured with OAuth2 client credentials.");
        }

        String targetRedirect = (redirect != null && !redirect.isBlank()) ? redirect : redirectUri;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);
            params.add("redirect_uri", targetRedirect);
            params.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                String accessToken = (String) body.get("access_token");
                String refreshToken = (String) body.get("refresh_token");
                Integer expiresIn = (Integer) body.get("expires_in");
                LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn != null ? expiresIn : 3600);

                return OAuthTokenResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresAt(expiresAt)
                        .calendarId("primary")
                        .build();
            }
        } catch (Exception e) {
            log.error("Google OAuth token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code with Google Calendar: " + e.getMessage(), e);
        }

        throw new RuntimeException("Unexpected response from Google OAuth service.");
    }

    @Override
    public String createEvent(String accessToken, String calendarId, CalendarEventDto event) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token required to create Google Calendar event.");
        }

        String targetCalendarId = (calendarId != null && !calendarId.isBlank()) ? calendarId : "primary";

        // In live production, calls Google Calendar API v3:
        // POST https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events
        // For safe mock/testing or when mock token passed:
        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) {
            return "gcal-evt-" + UUID.randomUUID().toString();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                    "summary", event.getTitle(),
                    "description", event.getDescription() != null ? event.getDescription() : "",
                    "start", Map.of("date", event.getStartDate().toString()),
                    "end", Map.of("date", event.getEndDate().toString())
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://www.googleapis.com/calendar/v3/calendars/" + targetCalendarId + "/events",
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("id");
            }
        } catch (Exception e) {
            log.warn("Google Calendar create event API call failed: {}", e.getMessage());
            // If live API is unreachable or token expired, throw so CalendarSyncService tracks failure
            throw new RuntimeException("Google Calendar event creation failed: " + e.getMessage(), e);
        }

        return "gcal-evt-" + UUID.randomUUID().toString();
    }

    @Override
    public void updateEvent(String accessToken, String calendarId, String externalEventId, CalendarEventDto event) {
        if (accessToken == null || accessToken.isBlank() || externalEventId == null) {
            return;
        }

        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) {
            return;
        }

        String targetCalendarId = (calendarId != null && !calendarId.isBlank()) ? calendarId : "primary";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                    "summary", event.getTitle(),
                    "description", event.getDescription() != null ? event.getDescription() : "",
                    "start", Map.of("date", event.getStartDate().toString()),
                    "end", Map.of("date", event.getEndDate().toString())
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.put(
                    "https://www.googleapis.com/calendar/v3/calendars/" + targetCalendarId + "/events/" + externalEventId,
                    request
            );
        } catch (Exception e) {
            log.warn("Google Calendar update event failed: {}", e.getMessage());
            throw new RuntimeException("Google Calendar event update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(String accessToken, String calendarId, String externalEventId) {
        if (accessToken == null || externalEventId == null) return;
        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) return;

        String targetCalendarId = (calendarId != null && !calendarId.isBlank()) ? calendarId : "primary";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(
                    "https://www.googleapis.com/calendar/v3/calendars/" + targetCalendarId + "/events/" + externalEventId,
                    org.springframework.http.HttpMethod.DELETE,
                    request,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Google Calendar delete event failed: {}", e.getMessage());
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
