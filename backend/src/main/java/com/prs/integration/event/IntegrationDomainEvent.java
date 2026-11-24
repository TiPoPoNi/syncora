package com.prs.integration.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationDomainEvent {
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    @Builder.Default
    private LocalDateTime eventTimestamp = LocalDateTime.now();
    private Long organizationId;
    private Long projectId;
    private Map<String, Object> data;
}
