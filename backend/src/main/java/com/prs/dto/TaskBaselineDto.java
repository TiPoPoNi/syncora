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
public class TaskBaselineDto {
    private Long id;
    private Long taskId;
    private String taskName;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private Integer durationDays;
}
