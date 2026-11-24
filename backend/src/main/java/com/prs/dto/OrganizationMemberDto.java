package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberDto {
    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private String joinedAt;
    private String employeeId;
    private String invitationStatus;
    private String invitationExpiresAt;
    private String activatedAt;
    private String activationLink;

    public Long getMembershipId() {
        return id;
    }
}
