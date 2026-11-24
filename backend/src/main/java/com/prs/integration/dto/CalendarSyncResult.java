package com.prs.integration.dto;

import com.prs.entity.CalendarProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSyncResult {
    private CalendarProvider provider;
    private Long projectId;
    private int totalRecords;
    private int eventsCreated;
    private int eventsUpdated;
    private int eventsSkippedUnchanged;
    private int eventsFailed;
    private boolean success;
    private String message;
    @Builder.Default
    private List<String> details = new ArrayList<>();
}
