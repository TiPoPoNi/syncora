package com.prs.dto;

import com.prs.entity.AssignmentType;
import com.prs.entity.TaskStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskAssignmentDto {
    private Long id;
    private Long taskId;
    private String taskName;
    private Long workerId;
    private String workerName;
    private Long vendorId;
    private String vendorName;
    private AssignmentType assignmentType;
    private Integer allocationPercentage;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
