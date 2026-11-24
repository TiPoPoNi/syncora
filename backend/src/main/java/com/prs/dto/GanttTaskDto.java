package com.prs.dto;

import com.prs.entity.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GanttTaskDto {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate finishDate;
    private Integer duration;
    private Integer progress;
    private TaskStatus status;
    private boolean critical;
    private boolean delayed;
    private int delayDays;
    private int totalSlack;
    private List<Long> dependencies; // predecessor IDs
    private List<String> assignees;
}
