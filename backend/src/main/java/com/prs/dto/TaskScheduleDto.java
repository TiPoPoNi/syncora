package com.prs.dto;

import com.prs.entity.TaskStatus;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TaskScheduleDto {
    private Long id;
    private String name;
    private Integer estimatedDuration;
    private LocalDate plannedStartDate;
    private LocalDate plannedFinishDate;
    private TaskStatus status;
    private List<TaskAssignmentDto> assignments;
    private List<Long> predecessorTaskIds;
}
