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
public class CpmTaskDto {
    private Long taskId;
    private String taskName;
    private int duration;
    private int earlyStart;
    private int earlyFinish;
    private int lateStart;
    private int lateFinish;
    private int totalSlack;
    private boolean critical;
    private LocalDate earlyStartDate;
    private LocalDate earlyFinishDate;
    private LocalDate lateStartDate;
    private LocalDate lateFinishDate;
    private List<Long> predecessorTaskIds;
    private List<Long> successorTaskIds;
}
