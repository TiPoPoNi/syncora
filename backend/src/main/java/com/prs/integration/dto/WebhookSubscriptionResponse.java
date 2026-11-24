package com.prs.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionResponse {
    private Long id;
    private Long organizationId;
    private String name;
    private String destinationUrl;
    private String secretMasked;
    private Boolean isEnabled;
    private List<String> subscribedEvents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastDeliveredAt;
    private Integer failureCount;
    private String status;
}
