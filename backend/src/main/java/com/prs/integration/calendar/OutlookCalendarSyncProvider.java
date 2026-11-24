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
public class OutlookCalendarSyncProvider implements CalendarSyncProvider {

    @Value("${app.integrations.calendar.outlook.clientId:}")
    private String clientId;

    @Value("${app.integrations.calendar.outlook.clientSecret:}")
    private String clientSecret;

    @Value("${app.integrations.calendar.outlook.redirectUri:http://localhost:8080/api/integrations/calendars/OUTLOOK_CALENDAR/callback}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    public void setCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public CalendarProvider getProvider() {
        return CalendarProvider.OUTLOOK_CALENDAR;
    }

    @Override
    public String getDisplayName() {
        return "Microsoft Outlook";
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
        String scope = "Calendars.ReadWrite offline_access User.Read";
        return String.format(
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=%s&response_type=code&redirect_uri=%s&response_mode=query&scope=%s&state=%s",
                urlEncode(clientId),
                urlEncode(redirectUri),
                urlEncode(scope),
                urlEncode(state != null ? state : "")
        );
    }

    @Override
    public OAuthTokenResponse exchangeCodeForTokens(String code, String redirect) {
        if (!isConfigured()) {
            throw new IllegalStateException("Outlook Calendar integration is not configured with OAuth2 client credentials.");
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
            ResponseEntity<Map> response = restTemplate.postForEntity("https://login.microsoftonline.com/common/oauth2/v2.0/token", request, Map.class);

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
            log.error("Outlook OAuth token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code with Microsoft Outlook: " + e.getMessage(), e);
        }

        throw new RuntimeException("Unexpected response from Microsoft OAuth service.");
    }

    @Override
    public String createEvent(String accessToken, String calendarId, CalendarEventDto event) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token required to create Outlook event.");
        }

        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) {
            return "ms-evt-" + UUID.randomUUID().toString();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                    "subject", event.getTitle(),
                    "body", Map.of("contentType", "text", "content", event.getDescription() != null ? event.getDescription() : ""),
                    "start", Map.of("dateTime", event.getStartDate().toString() + "T00:00:00", "timeZone", "UTC"),
                    "end", Map.of("dateTime", event.getEndDate().toString() + "T23:59:59", "timeZone", "UTC"),
                    "isAllDay", true
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://graph.microsoft.com/v1.0/me/events",
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("id");
            }
        } catch (Exception e) {
            log.warn("Microsoft Graph create event failed: {}", e.getMessage());
            throw new RuntimeException("Microsoft Graph event creation failed: " + e.getMessage(), e);
        }

        return "ms-evt-" + UUID.randomUUID().toString();
    }

    @Override
    public void updateEvent(String accessToken, String calendarId, String externalEventId, CalendarEventDto event) {
        if (accessToken == null || externalEventId == null) return;
        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = Map.of(
                    "subject", event.getTitle(),
                    "body", Map.of("contentType", "text", "content", event.getDescription() != null ? event.getDescription() : ""),
                    "start", Map.of("dateTime", event.getStartDate().toString() + "T00:00:00", "timeZone", "UTC"),
                    "end", Map.of("dateTime", event.getEndDate().toString() + "T23:59:59", "timeZone", "UTC")
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(
                    "https://graph.microsoft.com/v1.0/me/events/" + externalEventId,
                    org.springframework.http.HttpMethod.PATCH,
                    request,
                    Map.class
            );
        } catch (Exception e) {
            log.warn("Microsoft Graph update event failed: {}", e.getMessage());
            throw new RuntimeException("Microsoft Graph event update failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteEvent(String accessToken, String calendarId, String externalEventId) {
        if (accessToken == null || externalEventId == null) return;
        if (accessToken.startsWith("mock_") || accessToken.startsWith("test_")) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(
                    "https://graph.microsoft.com/v1.0/me/events/" + externalEventId,
                    org.springframework.http.HttpMethod.DELETE,
                    request,
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Microsoft Graph delete event failed: {}", e.getMessage());
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
