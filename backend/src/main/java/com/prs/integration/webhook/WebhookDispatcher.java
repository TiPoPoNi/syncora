package com.prs.integration.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.entity.WebhookDelivery;
import com.prs.entity.WebhookDeliveryStatus;
import com.prs.entity.WebhookSubscription;
import com.prs.integration.event.IntegrationDomainEvent;
import com.prs.integration.security.WebhookHmacSigner;
import com.prs.integration.security.WebhookUrlValidator;
import com.prs.repository.WebhookDeliveryRepository;
import com.prs.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatcher {

    private final WebhookHmacSigner hmacSigner;
    private final WebhookUrlValidator urlValidator;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.integrations.webhooks.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${app.integrations.webhooks.max-retries:3}")
    private int maxRetries;

    public WebhookDelivery dispatch(WebhookSubscription subscription, IntegrationDomainEvent event) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize webhook event payload: {}", e.getMessage());
            payloadJson = "{\"eventId\":\"" + event.getEventId() + "\",\"eventType\":\"" + event.getEventType() + "\"}";
        }

        return deliver(subscription, event.getEventId(), event.getEventType(), payloadJson, 1);
    }

    public WebhookDelivery redeliver(WebhookDelivery delivery) {
        WebhookSubscription subscription = delivery.getSubscription();
        int nextAttempt = (delivery.getAttemptCount() != null ? delivery.getAttemptCount() : 1) + 1;
        return deliver(subscription, delivery.getEventId(), delivery.getEventType(), delivery.getPayload(), nextAttempt);
    }

    private WebhookDelivery deliver(WebhookSubscription subscription, String eventId, String eventType, String payloadJson, int attemptCount) {
        long startTime = System.currentTimeMillis();
        Integer statusCode = null;
        String responseBody = null;
        String errorMessage = null;
        WebhookDeliveryStatus deliveryStatus = WebhookDeliveryStatus.FAILED;

        try {
            // 1. SSRF URL validation
            urlValidator.validateUrl(subscription.getDestinationUrl());

            // 2. Compute HMAC-SHA256 signature
            String signature = hmacSigner.signPayload(payloadJson, subscription.getSecret());

            // 3. Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "PRS-Webhook-Dispatcher/1.0");
            headers.set("X-PRS-Signature", signature);
            headers.set("X-PRS-Event", eventType);
            headers.set("X-PRS-Event-Id", eventId);
            headers.set("X-PRS-Timestamp", String.valueOf(System.currentTimeMillis()));

            HttpEntity<String> requestEntity = new HttpEntity<>(payloadJson, headers);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            RestTemplate restTemplate = new RestTemplate(factory);

            ResponseEntity<String> response = restTemplate.exchange(
                    subscription.getDestinationUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            statusCode = response.getStatusCode().value();
            if (response.getBody() != null) {
                responseBody = response.getBody().substring(0, Math.min(response.getBody().length(), 1000));
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                deliveryStatus = WebhookDeliveryStatus.SUCCESS;
            } else {
                deliveryStatus = WebhookDeliveryStatus.FAILED;
                errorMessage = "HTTP returned non-2xx status code: " + statusCode;
            }
        } catch (Exception e) {
            log.warn("Webhook delivery error for subscription {} ({}): {}", subscription.getId(), subscription.getDestinationUrl(), e.getMessage());
            errorMessage = e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown delivery error";
            deliveryStatus = WebhookDeliveryStatus.FAILED;
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Update subscription health
        try {
            if (deliveryStatus == WebhookDeliveryStatus.SUCCESS) {
                subscription.setLastDeliveredAt(LocalDateTime.now());
                subscription.setFailureCount(0);
                subscription.setStatus("ACTIVE");
            } else {
                int currentFailures = subscription.getFailureCount() != null ? subscription.getFailureCount() : 0;
                subscription.setFailureCount(currentFailures + 1);
                if (subscription.getFailureCount() >= 10) {
                    subscription.setStatus("DEGRADED");
                }
            }
            subscriptionRepository.save(subscription);
        } catch (Exception ignored) {}

        // Persist delivery record
        WebhookDelivery delivery = WebhookDelivery.builder()
                .subscription(subscription)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payloadJson)
                .responseStatusCode(statusCode)
                .responseBody(responseBody)
                .durationMs(durationMs)
                .status(deliveryStatus)
                .attemptCount(attemptCount)
                .errorMessage(errorMessage)
                .build();

        return deliveryRepository.save(delivery);
    }
}
