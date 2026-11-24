package com.prs.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportDelayRequest {
    @NotNull(message = "Delay duration is required")
    @Min(value = 1, message = "Delay duration must be at least 1 working day")
    private Integer delayDurationDays;

    @NotBlank(message = "Delay reason is required")
    private String delayReason;

    private String notes;
}
