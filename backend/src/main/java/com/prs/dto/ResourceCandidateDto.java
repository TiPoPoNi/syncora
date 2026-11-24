package com.prs.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResourceCandidateDto {
    private Long id;
    private String name;
    private String type; // "WORKER" or "VENDOR"
    private String skillsOrServices;
    private Integer experience;
    private Integer dailyCapacity;
    private boolean isAvailable;
    private int activeAssignmentsCount;
    private boolean hasRequiredSkills;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private double suitabilityScore; // 0 to 100
    private String recommendationReason;
}
