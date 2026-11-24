package com.prs.dto;

import com.prs.entity.DependencyType;
import com.prs.entity.TaskStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class TaskDependencyDetailsDto {
    private Long taskId;
    private String taskName;
    private Long projectId;
    private String projectName;
    private TaskStatus status;
    private Integer progress;
    private boolean isBlocked;
    private List<String> blockingReasons = new ArrayList<>();
    private List<TaskDependencyItemDto> predecessors = new ArrayList<>();
    private List<TaskDependencyItemDto> successors = new ArrayList<>();
    private List<TaskGateItemDto> approvalGates = new ArrayList<>();

    @Data
    public static class TaskDependencyItemDto {
        private Long dependencyId;
        private Long taskId;
        private String taskName;
        private TaskStatus status;
        private Integer progress;
        private Integer estimatedDuration;
        private LocalDate plannedStartDate;
        private LocalDate plannedFinishDate;
        private DependencyType dependencyType;
        private boolean isBlocking;
    }

    @Data
    public static class TaskGateItemDto {
        private Long gateId;
        private Long deliverableId;
        private String deliverableName;
        private String deliverableStatus;
        private String gateType;
        private Boolean isMandatory;
        private boolean isSatisfied;
    }
}
