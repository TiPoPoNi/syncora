package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationDto {
    private Long id;
    private String name;
    private String slug;
    private String orgCode;
    private String status;
    private long memberCount;
    private long projectCount;
    private String currentUserRole;
    private String createdAt;
}
