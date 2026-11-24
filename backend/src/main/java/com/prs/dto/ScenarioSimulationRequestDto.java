package com.prs.dto;

import lombok.Data;

@Data
public class ScenarioSimulationRequestDto {
    private String scenarioType = "TASK_DELAY"; // TASK_DELAY, WORKER_REASSIGNMENT, VENDOR_SUBSTITUTION
    private Long taskId;
    private Integer additionalDelayDays;
    private String delayReason;
    private Long newWorkerId;
    private Long newVendorId;
}
