package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterOrganizationRequest {
    private String organizationName;
    private String organizationSlug;
    private String organizationCode;
    private String ownerEmail;
    private String ownerUsername;
    private String ownerPassword;
    private String ownerConfirmPassword;
    private String ownerFullName;
}
