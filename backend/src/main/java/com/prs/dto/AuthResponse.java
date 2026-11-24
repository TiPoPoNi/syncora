package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private String email;
    private Long organizationId;
    private String organizationName;
    private String organizationSlug;
    private String organizationCode;
    private String employeeId;
    private String userRole;
    private List<UserMembershipSummaryDto> memberships;

    public AuthResponse(String token, String username, String role) {
        this.token = token;
        this.username = username;
        this.role = role;
    }
}
