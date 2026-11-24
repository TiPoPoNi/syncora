package com.prs.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectCalendarRequest {
    private String authorizationCode;
    private String redirectUri;
    private String calendarId;
    private String accountEmail;
}
