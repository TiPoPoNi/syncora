package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMembershipSummaryDto {
    private Long organizationId;
    private String organizationName;
    private String organizationSlug;
    private String organizationCode;
    private String role;
    private String status;
    private String employeeId;
}
