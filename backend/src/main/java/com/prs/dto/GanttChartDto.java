package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GanttChartDto {
    private Long projectId;
    private String projectName;
    private LocalDate projectStartDate;
    private LocalDate projectDeadline;
    private LocalDate expectedCompletionDate;
    private String deadlineStatus; // ON_TRACK, AT_RISK, DELAYED
    private LocalDate currentDate;
    private List<GanttTaskDto> tasks;
}
