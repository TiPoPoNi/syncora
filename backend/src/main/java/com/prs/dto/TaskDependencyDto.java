package com.prs.dto;

import com.prs.entity.DependencyType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskDependencyDto {
    private Long id;
    private Long predecessorTaskId;
    private String predecessorTaskName;
    private Long successorTaskId;
    private String successorTaskName;
    private DependencyType dependencyType;
    private LocalDateTime createdAt;
}
