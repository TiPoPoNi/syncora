package com.prs.optimization;

import com.prs.entity.RecommendationCategory;
import lombok.Data;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
public class OptimizationCandidate {
    private int rank;
    private String title;
    private String summary;
    private RecommendationCategory category = RecommendationCategory.WORKER_REASSIGNMENT;

    // Changes proposed by this candidate
    private Map<Long, Long> proposedWorkerReassignments = new HashMap<>(); // taskId -> newWorkerId
    private Map<Long, Long> proposedVendorSubstitutions = new HashMap<>(); // taskId -> newVendorId
    private Map<Long, LocalDate> proposedTaskStartDates = new HashMap<>(); // taskId -> newPlannedStart

    // Forecast and metrics
    private LocalDate projectCompletion;
    private String deadlineStatus = "ON_TRACK";
    private int workingDaysSaved = 0;
    private int conflictsResolved = 0;
    private int remainingConflicts = 0;
    private int disruptionCount = 0;
    private double objectiveScore = 0.0;
    private String constraintStatus = "VALID";

    // Explanations and trade-offs
    private String tradeOffs;
    private String explanation;
}
