package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    private String username;
    private String role; // 'ORGANIZATION_ADMIN', 'PROJECT_MANAGER', 'CLIENT', 'WORKER', 'VENDOR'
}
