package com.prs.cpm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpmTaskResult {
    private Long taskId;
    private String taskName;
    private int duration;
    
    // Relative working-day indices
    private int earlyStart;  // ES
    private int earlyFinish; // EF
    private int lateStart;   // LS
    private int lateFinish;  // LF
    private int totalSlack;  // LS - ES
    private boolean critical;

    // Calendar dates
    private LocalDate earlyStartDate;
    private LocalDate earlyFinishDate;
    private LocalDate lateStartDate;
    private LocalDate lateFinishDate;

    @Builder.Default
    private List<Long> predecessorTaskIds = new ArrayList<>();

    @Builder.Default
    private List<Long> successorTaskIds = new ArrayList<>();
}
