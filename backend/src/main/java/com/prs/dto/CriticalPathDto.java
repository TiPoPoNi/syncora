package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticalPathDto {
    private Long projectId;
    private String projectName;
    private LocalDate projectStartDate;
    private LocalDate earliestProjectedCompletion;
    private int minimumDurationWorkingDays;
    private List<CpmTaskDto> tasks;
    private Set<Long> criticalTaskIds;
    private List<Long> representativeCriticalPath;
    private List<List<Long>> allCriticalPaths;
    private List<String> criticalTaskNames;
}
