package com.prs.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ScheduleAlternativeDto {
    private Long id;
    private Long optimizationRunId;
    private Integer alternativeRank;
    private String title;
    private String summary;
    private String proposedReassignments;
    private String proposedScheduleChanges;
    private LocalDate projectCompletionForecast;
    private String deadlineStatus;
    private Integer workingDaysSaved;
    private Integer conflictsResolved;
    private Integer remainingConflicts;
    private Integer disruptionCount;
    private Double objectiveScore;
    private String constraintStatus;
    private String tradeOffs;
    private LocalDateTime createdAt;
}
