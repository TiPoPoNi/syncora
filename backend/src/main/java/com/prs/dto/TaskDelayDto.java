package com.prs.dto;

import com.prs.entity.DelayStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDelayDto {
    private Long id;
    private Long taskId;
    private String taskName;
    private Integer delayDurationDays;
    private String delayReason;
    private LocalDate reportedDate;
    private LocalDate updatedExpectedFinish;
    private String notes;
    private String reportedBy;
    private DelayStatus status;
    private LocalDateTime createdAt;
}
