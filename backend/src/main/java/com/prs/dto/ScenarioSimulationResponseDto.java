package com.prs.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScenarioSimulationResponseDto {
    private String scenarioType;
    private Long targetTaskId;
    private String targetTaskName;
    private String originalForecast;
    private String simulatedForecast;
    private int workingDaysDifference;
    private String originalDeadlineStatus;
    private String simulatedDeadlineStatus;
    private String clientDeadline;
    private List<Map<String, Object>> affectedTasks;
    private int conflictsResolvedCount;
    private int newConflictsCount;
    private boolean isHypothetical = true;

    // AI generated explanation
    private String aiExplanation;
    private String impactSummary;
    private List<String> risksAndAssumptions;
    private String recommendedNextStep;
    private List<String> citations;
}
