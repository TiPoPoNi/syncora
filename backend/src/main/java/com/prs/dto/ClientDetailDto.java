package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailDto {
    private Long id;
    private String name;
    private String company;
    private String email;
    private String phone;
    private String address;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long totalProjects;
    private Long activeProjects;
    private List<ClientProjectSummaryDto> projects;
    private List<ClientDeliverableSummaryDto> deliverables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientProjectSummaryDto {
        private Long id;
        private String name;
        private String description;
        private String status;
        private String priority;
        private Integer progress;
        private LocalDate startDate;
        private LocalDate deadline;
        private LocalDate expectedCompletionDate;
        private Long managerId;
        private String managerName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDeliverableSummaryDto {
        private Long id;
        private Long projectId;
        private String projectName;
        private String name;
        private String description;
        private String deliverableType;
        private String status;
        private LocalDate requiredSubmissionDate;
        private LocalDate actualSubmissionDate;
        private Integer currentVersionNumber;
    }
}
