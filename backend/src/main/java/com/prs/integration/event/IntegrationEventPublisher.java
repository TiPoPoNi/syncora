package com.prs.integration.event;

import com.prs.integration.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationEventPublisher {

    private final WebhookService webhookService;

    /**
     * Publishes a domain event to all authorized external webhook endpoints.
     * Guaranteed to be non-blocking and catch all errors so it never rolls back
     * core business operations or database transactions.
     */
    public void publishEvent(String eventType, Long organizationId, Long projectId, Map<String, Object> data) {
        try {
            IntegrationDomainEvent event = IntegrationDomainEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .eventTimestamp(LocalDateTime.now())
                    .organizationId(organizationId)
                    .projectId(projectId)
                    .data(data)
                    .build();

            webhookService.dispatchDomainEvent(event);
        } catch (Exception e) {
            log.warn("Integration event publish error for type {}: {}", eventType, e.getMessage());
        }
    }
}
