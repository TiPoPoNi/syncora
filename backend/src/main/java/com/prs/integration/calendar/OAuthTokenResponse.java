package com.prs.integration.calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTokenResponse {
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private String accountEmail;
    private String calendarId;
}
