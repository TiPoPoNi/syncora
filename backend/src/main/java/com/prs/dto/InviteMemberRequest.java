package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteMemberRequest {
    private String email;
    private String fullName;
    private String role; // 'ORGANIZATION_OWNER', 'ORGANIZATION_ADMIN', 'PROJECT_MANAGER', 'WORKER', 'CLIENT', 'VENDOR'
    private String customEmployeeId;
}
