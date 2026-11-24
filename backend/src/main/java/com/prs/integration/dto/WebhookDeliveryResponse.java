package com.prs.integration.dto;

import com.prs.entity.WebhookDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryResponse {
    private Long id;
    private Long subscriptionId;
    private String eventId;
    private String eventType;
    private String payload;
    private Integer responseStatusCode;
    private String responseBody;
    private Long durationMs;
    private WebhookDeliveryStatus status;
    private Integer attemptCount;
    private String errorMessage;
    private LocalDateTime createdAt;
}
