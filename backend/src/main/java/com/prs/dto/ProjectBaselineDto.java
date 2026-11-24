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
public class ProjectBaselineDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private String versionName;
    private LocalDate expectedCompletionDate;
    private Integer minimumDurationDays;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<TaskBaselineDto> taskBaselines;
}
