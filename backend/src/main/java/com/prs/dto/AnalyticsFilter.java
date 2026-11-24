package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsFilter {
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String priority;
    private Long clientId;
    private Long managerId;
    private Long projectId;
}
