package com.prs.dto;

import lombok.Data;

@Data
public class StartOptimizationRequest {
    private String selectedObjective = "BALANCED"; // BALANCED, DEADLINE_FIRST, MINIMIZE_CONFLICTS
}
