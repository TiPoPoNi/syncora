package com.prs.cpm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriticalPathResult {
    private int minimumDurationWorkingDays;
    private LocalDate projectStartDate;
    private LocalDate earliestProjectedCompletion;

    @Builder.Default
    private List<CpmTaskResult> taskResults = new ArrayList<>();

    @Builder.Default
    private Set<Long> criticalTaskIds = new HashSet<>();

    @Builder.Default
    private List<Long> representativeCriticalPath = new ArrayList<>();

    @Builder.Default
    private List<List<Long>> allCriticalPaths = new ArrayList<>();

    @Builder.Default
    private List<String> criticalTaskNames = new ArrayList<>();
}
