package com.prs.integration.controller;

import com.prs.entity.CalendarProvider;
import com.prs.entity.Organization;
import com.prs.entity.User;
import com.prs.integration.apikey.ApiKeyService;
import com.prs.integration.calendar.CalendarSyncService;
import com.prs.integration.dto.*;
import com.prs.integration.webhook.WebhookService;
import com.prs.repository.OrganizationMembershipRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.UserRepository;
import com.prs.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j

public class IntegrationController {

    private final CalendarSyncService calendarSyncService;
    private final WebhookService webhookService;
    private final ApiKeyService apiKeyService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    private final OrganizationMembershipRepository membershipRepository;

    private Long resolveTenantId(Authentication authentication) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }

        if (authentication != null && authentication.getName() != null) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                var memberships = membershipRepository.findByUserId(user.getId());
                if (!memberships.isEmpty() && memberships.get(0).getOrganization() != null) {
                    return memberships.get(0).getOrganization().getId();
                }
            }
        }

        throw new com.prs.tenant.TenantAccessDeniedException("Active organization context is required to access integrations.");
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByUsername(authentication.getName())
                    .map(User::getId)
                    .orElse(null);
        }
        return null;
    }

    // =========================================================================
    // 1. Calendar Synchronization Endpoints
    // =========================================================================

    @GetMapping("/api/integrations/calendars")
    public ResponseEntity<List<CalendarIntegrationStatusResponse>> getCalendarStatuses(Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(calendarSyncService.getProviderStatuses(orgId));
    }

    @GetMapping("/api/integrations/calendars/{provider}/status")
    public ResponseEntity<CalendarIntegrationStatusResponse> getCalendarStatus(
            @PathVariable CalendarProvider provider,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        List<CalendarIntegrationStatusResponse> statuses = calendarSyncService.getProviderStatuses(orgId);
        return statuses.stream()
                .filter(s -> s.getProvider() == provider)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/integrations/calendars/{provider}/connect")
    public ResponseEntity<CalendarIntegrationStatusResponse> connectCalendar(
            @PathVariable CalendarProvider provider,
            @RequestBody(required = false) ConnectCalendarRequest req,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(calendarSyncService.connect(orgId, userId, provider, req));
    }

    @PostMapping("/api/integrations/calendars/{provider}/disconnect")
    public ResponseEntity<Map<String, String>> disconnectCalendar(
            @PathVariable CalendarProvider provider,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        calendarSyncService.disconnect(orgId, provider);
        return ResponseEntity.ok(Map.of("message", provider + " integration disconnected successfully."));
    }

    @GetMapping("/api/integrations/calendars/{provider}/callback")
    public ResponseEntity<Map<String, String>> handleOAuthCallback(
            @PathVariable CalendarProvider provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(Map.of(
                "message", "OAuth authorization callback received for " + provider,
                "code", code != null ? code : "",
                "state", state != null ? state : ""
        ));
    }

    @PostMapping("/api/projects/{projectId}/calendar/sync")
    public ResponseEntity<CalendarSyncResult> syncProjectCalendar(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "GOOGLE_CALENDAR") CalendarProvider provider,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(calendarSyncService.syncProject(orgId, projectId, provider));
    }

    // =========================================================================
    // 2. Webhook Configuration Endpoints
    // =========================================================================

    @GetMapping("/api/integrations/webhooks")
    public ResponseEntity<List<WebhookSubscriptionResponse>> getWebhooks(Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.getSubscriptions(orgId));
    }

    @PostMapping("/api/integrations/webhooks")
    public ResponseEntity<WebhookSubscriptionResponse> createWebhook(
            @Valid @RequestBody WebhookSubscriptionRequest req,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.createSubscription(orgId, req));
    }

    @PutMapping("/api/integrations/webhooks/{webhookId}")
    public ResponseEntity<WebhookSubscriptionResponse> updateWebhook(
            @PathVariable Long webhookId,
            @RequestBody WebhookSubscriptionRequest req,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.updateSubscription(orgId, webhookId, req));
    }

    @DeleteMapping("/api/integrations/webhooks/{webhookId}")
    public ResponseEntity<Map<String, String>> deleteWebhook(
            @PathVariable Long webhookId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        webhookService.deleteSubscription(orgId, webhookId);
        return ResponseEntity.ok(Map.of("message", "Webhook subscription deleted successfully."));
    }

    @GetMapping("/api/integrations/webhooks/{webhookId}/deliveries")
    public ResponseEntity<List<WebhookDeliveryResponse>> getWebhookDeliveries(
            @PathVariable Long webhookId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.getDeliveries(orgId, webhookId));
    }

    @PostMapping("/api/integrations/webhooks/{webhookId}/deliveries/{deliveryId}/retry")
    public ResponseEntity<WebhookDeliveryResponse> retryDelivery(
            @PathVariable Long webhookId,
            @PathVariable Long deliveryId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.retryDelivery(orgId, webhookId, deliveryId));
    }

    @PostMapping("/api/integrations/webhooks/{webhookId}/test")
    public ResponseEntity<WebhookDeliveryResponse> testPingWebhook(
            @PathVariable Long webhookId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.testPing(orgId, webhookId));
    }

    @PostMapping("/api/integrations/webhooks/{webhookId}/rotate-secret")
    public ResponseEntity<WebhookSubscriptionResponse> rotateWebhookSecret(
            @PathVariable Long webhookId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(webhookService.rotateSecret(orgId, webhookId));
    }

    // =========================================================================
    // 3. Scoped API Keys Endpoints
    // =========================================================================

    @GetMapping("/api/integrations/api-keys")
    public ResponseEntity<List<ApiKeyResponse>> getApiKeys(Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        return ResponseEntity.ok(apiKeyService.getApiKeys(orgId));
    }

    @PostMapping("/api/integrations/api-keys")
    public ResponseEntity<ApiKeyResponse> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest req,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        Long userId = resolveUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.createApiKey(orgId, userId, req));
    }

    @DeleteMapping("/api/integrations/api-keys/{keyId}")
    public ResponseEntity<Map<String, String>> revokeApiKey(
            @PathVariable Long keyId,
            Authentication authentication) {
        Long orgId = resolveTenantId(authentication);
        apiKeyService.revokeApiKey(orgId, keyId);
        return ResponseEntity.ok(Map.of("message", "API Key revoked successfully."));
    }
}
