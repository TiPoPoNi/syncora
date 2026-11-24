package com.prs.integration.webhook;

import com.prs.entity.Organization;
import com.prs.entity.WebhookDelivery;
import com.prs.entity.WebhookSubscription;
import com.prs.integration.dto.WebhookDeliveryResponse;
import com.prs.integration.dto.WebhookSubscriptionRequest;
import com.prs.integration.dto.WebhookSubscriptionResponse;
import com.prs.integration.event.IntegrationDomainEvent;
import com.prs.integration.security.WebhookUrlValidator;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.WebhookDeliveryRepository;
import com.prs.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final OrganizationRepository organizationRepository;
    private final WebhookUrlValidator urlValidator;
    private final WebhookDispatcher dispatcher;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public WebhookSubscriptionResponse createSubscription(Long organizationId, WebhookSubscriptionRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with id: " + organizationId));

        urlValidator.validateUrl(request.getDestinationUrl());

        String secret = request.getSecret();
        if (secret == null || secret.isBlank()) {
            secret = generateSecret();
        }

        List<String> events = (request.getSubscribedEvents() == null || request.getSubscribedEvents().isEmpty())
                ? List.of("project.created", "project.updated", "task.created", "task.completed", "schedule.recalculated")
                : request.getSubscribedEvents();

        WebhookSubscription subscription = WebhookSubscription.builder()
                .organization(organization)
                .name(request.getName().trim())
                .destinationUrl(request.getDestinationUrl().trim())
                .secret(secret)
                .isEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true)
                .subscribedEvents(String.join(",", events))
                .status("ACTIVE")
                .build();

        WebhookSubscription saved = subscriptionRepository.save(subscription);
        return toSubscriptionResponse(saved);
    }

    @Transactional
    public WebhookSubscriptionResponse updateSubscription(Long organizationId, Long webhookId, WebhookSubscriptionRequest request) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));

        if (request.getDestinationUrl() != null && !request.getDestinationUrl().isBlank()) {
            urlValidator.validateUrl(request.getDestinationUrl());
            subscription.setDestinationUrl(request.getDestinationUrl().trim());
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            subscription.setName(request.getName().trim());
        }

        if (request.getIsEnabled() != null) {
            subscription.setIsEnabled(request.getIsEnabled());
        }

        if (request.getSubscribedEvents() != null && !request.getSubscribedEvents().isEmpty()) {
            subscription.setSubscribedEvents(String.join(",", request.getSubscribedEvents()));
        }

        if (request.getSecret() != null && !request.getSecret().isBlank()) {
            subscription.setSecret(request.getSecret().trim());
        }

        WebhookSubscription updated = subscriptionRepository.save(subscription);
        return toSubscriptionResponse(updated);
    }

    @Transactional
    public void deleteSubscription(Long organizationId, Long webhookId) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));
        subscriptionRepository.delete(subscription);
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionResponse> getSubscriptions(Long organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId).stream()
                .map(this::toSubscriptionResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> getDeliveries(Long organizationId, Long webhookId) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));

        return deliveryRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscription.getId()).stream()
                .map(this::toDeliveryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WebhookDeliveryResponse retryDelivery(Long organizationId, Long webhookId, Long deliveryId) {
        subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));

        WebhookDelivery delivery = deliveryRepository.findByIdAndSubscriptionId(deliveryId, webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery record not found for webhook: " + deliveryId));

        WebhookDelivery redelivered = dispatcher.redeliver(delivery);
        return toDeliveryResponse(redelivered);
    }

    @Transactional
    public WebhookSubscriptionResponse rotateSecret(Long organizationId, Long webhookId) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));

        subscription.setSecret(generateSecret());
        WebhookSubscription updated = subscriptionRepository.save(subscription);
        return toSubscriptionResponse(updated);
    }

    @Transactional
    public WebhookDeliveryResponse testPing(Long organizationId, Long webhookId) {
        WebhookSubscription subscription = subscriptionRepository.findByIdAndOrganizationId(webhookId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found for active organization."));

        IntegrationDomainEvent pingEvent = IntegrationDomainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("webhook.test_ping")
                .organizationId(organizationId)
                .data(Map.of("message", "Test ping from PRS External Integrations System", "subscriptionId", webhookId))
                .build();

        WebhookDelivery delivery = dispatcher.dispatch(subscription, pingEvent);
        return toDeliveryResponse(delivery);
    }

    public void dispatchDomainEvent(IntegrationDomainEvent event) {
        if (event == null || event.getEventType() == null) return;

        List<WebhookSubscription> subscriptions;
        if (event.getOrganizationId() != null) {
            subscriptions = subscriptionRepository.findByOrganizationIdAndIsEnabledTrue(event.getOrganizationId());
        } else {
            subscriptions = subscriptionRepository.findByIsEnabledTrue();
        }

        for (WebhookSubscription sub : subscriptions) {
            try {
                if (matchesEvent(sub.getSubscribedEvents(), event.getEventType())) {
                    dispatcher.dispatch(sub, event);
                }
            } catch (Exception e) {
                log.warn("Error dispatching event {} to subscription {}: {}", event.getEventType(), sub.getId(), e.getMessage());
            }
        }
    }

    private boolean matchesEvent(String subscribedEventsStr, String eventType) {
        if (subscribedEventsStr == null || subscribedEventsStr.isBlank()) return false;
        if (subscribedEventsStr.equals("*") || subscribedEventsStr.contains("*")) return true;
        List<String> events = Arrays.stream(subscribedEventsStr.split(","))
                .map(String::trim)
                .toList();
        return events.contains(eventType);
    }

    private String generateSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("whsec_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private WebhookSubscriptionResponse toSubscriptionResponse(WebhookSubscription s) {
        List<String> events = s.getSubscribedEvents() != null
                ? Arrays.stream(s.getSubscribedEvents().split(",")).map(String::trim).collect(Collectors.toList())
                : List.of();

        String maskedSecret = (s.getSecret() != null && s.getSecret().length() > 8)
                ? s.getSecret().substring(0, 6) + "***" + s.getSecret().substring(s.getSecret().length() - 4)
                : "********";

        return WebhookSubscriptionResponse.builder()
                .id(s.getId())
                .organizationId(s.getOrganization() != null ? s.getOrganization().getId() : null)
                .name(s.getName())
                .destinationUrl(s.getDestinationUrl())
                .secretMasked(maskedSecret)
                .isEnabled(s.getIsEnabled())
                .subscribedEvents(events)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .lastDeliveredAt(s.getLastDeliveredAt())
                .failureCount(s.getFailureCount())
                .status(s.getStatus())
                .build();
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery d) {
        return WebhookDeliveryResponse.builder()
                .id(d.getId())
                .subscriptionId(d.getSubscription() != null ? d.getSubscription().getId() : null)
                .eventId(d.getEventId())
                .eventType(d.getEventType())
                .payload(d.getPayload())
                .responseStatusCode(d.getResponseStatusCode())
                .responseBody(d.getResponseBody())
                .durationMs(d.getDurationMs())
                .status(d.getStatus())
                .attemptCount(d.getAttemptCount())
                .errorMessage(d.getErrorMessage())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
