package com.prs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteTaskRequest {
    private LocalDate actualStartDate;

    @NotNull(message = "Actual finish date is required")
    private LocalDate actualFinishDate;

    private String primaryDelayReason;
}
