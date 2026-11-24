package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String jobTitle;
    private String department;
    private String avatarUrl;
    private String employeeId;
    private Long organizationId;
    private String organizationName;
    private String role;
    private boolean hasPendingChangeRequest;
    private Long pendingChangeRequestId;
}
