package com.prs.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventDto {
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isAllDay;
    private String entityType;
    private Long entityId;
}
