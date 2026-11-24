package com.prs.integration.calendar;

import com.prs.entity.*;
import com.prs.integration.apikey.ApiKeyService;
import com.prs.integration.dto.CalendarEventDto;
import com.prs.integration.dto.CalendarIntegrationStatusResponse;
import com.prs.integration.dto.CalendarSyncResult;
import com.prs.integration.dto.ConnectCalendarRequest;
import com.prs.repository.*;
import com.prs.tenant.TenantAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarSyncService {

    private final ExternalCalendarIntegrationRepository integrationRepository;
    private final CalendarEventMappingRepository mappingRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final DeliverableRepository deliverableRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    private final GoogleCalendarSyncProvider googleProvider;
    private final OutlookCalendarSyncProvider outlookProvider;

    public CalendarSyncProvider getProviderImplementation(CalendarProvider provider) {
        if (provider == CalendarProvider.GOOGLE_CALENDAR) {
            return googleProvider;
        } else if (provider == CalendarProvider.OUTLOOK_CALENDAR) {
            return outlookProvider;
        }
        throw new IllegalArgumentException("Unsupported calendar provider: " + provider);
    }

    @Transactional(readOnly = true)
    public List<CalendarIntegrationStatusResponse> getProviderStatuses(Long organizationId) {
        List<CalendarIntegrationStatusResponse> results = new ArrayList<>();

        for (CalendarProvider provider : CalendarProvider.values()) {
            CalendarSyncProvider impl = getProviderImplementation(provider);
            boolean configured = impl.isConfigured();

            Optional<ExternalCalendarIntegration> integrationOpt =
                    integrationRepository.findByOrganizationIdAndProvider(organizationId, provider);

            if (integrationOpt.isPresent()) {
                ExternalCalendarIntegration integration = integrationOpt.get();
                int mappedEvents = mappingRepository.findByIntegrationId(integration.getId()).size();

                results.add(CalendarIntegrationStatusResponse.builder()
                        .provider(provider)
                        .providerName(impl.getDisplayName())
                        .configured(configured)
                        .status(integration.getStatus())
                        .accountEmail(integration.getAccountEmail())
                        .calendarId(integration.getCalendarId())
                        .lastSyncAt(integration.getLastSyncAt())
                        .lastError(integration.getLastError())
                        .mappedEventCount(mappedEvents)
                        .authorizationUrl(configured ? impl.buildAuthorizationUrl("org-" + organizationId) : null)
                        .build());
            } else {
                CalendarSyncStatus status = configured ? CalendarSyncStatus.CONFIGURED : CalendarSyncStatus.NOT_CONFIGURED;
                results.add(CalendarIntegrationStatusResponse.builder()
                        .provider(provider)
                        .providerName(impl.getDisplayName())
                        .configured(configured)
                        .status(status)
                        .accountEmail(null)
                        .calendarId(null)
                        .lastSyncAt(null)
                        .lastError(null)
                        .mappedEventCount(0)
                        .authorizationUrl(configured ? impl.buildAuthorizationUrl("org-" + organizationId) : null)
                        .build());
            }
        }

        return results;
    }

    @Transactional
    public CalendarIntegrationStatusResponse connect(Long organizationId, Long userId, CalendarProvider provider, ConnectCalendarRequest request) {
        CalendarSyncProvider impl = getProviderImplementation(provider);

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with id: " + organizationId));

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        String accessToken;
        String refreshToken = null;
        LocalDateTime expiresAt = null;
        String accountEmail = request != null ? request.getAccountEmail() : null;
        String calendarId = (request != null && request.getCalendarId() != null) ? request.getCalendarId() : "primary";

        if (request != null && request.getAuthorizationCode() != null && !request.getAuthorizationCode().isBlank()) {
            OAuthTokenResponse tokens = impl.exchangeCodeForTokens(request.getAuthorizationCode(), request.getRedirectUri());
            accessToken = tokens.getAccessToken();
            refreshToken = tokens.getRefreshToken();
            expiresAt = tokens.getExpiresAt();
            if (tokens.getAccountEmail() != null) accountEmail = tokens.getAccountEmail();
            if (tokens.getCalendarId() != null) calendarId = tokens.getCalendarId();
        } else {
            // Manual test / direct token connection
            accessToken = "mock_token_" + UUID.randomUUID().toString();
            expiresAt = LocalDateTime.now().plusDays(30);
        }

        ExternalCalendarIntegration integration = integrationRepository
                .findByOrganizationIdAndProvider(organizationId, provider)
                .orElse(ExternalCalendarIntegration.builder()
                        .organization(organization)
                        .user(user)
                        .provider(provider)
                        .build());

        integration.setAccountEmail(accountEmail != null ? accountEmail : "user@" + provider.name().toLowerCase() + ".com");
        integration.setCalendarId(calendarId);
        integration.setAccessToken(accessToken);
        integration.setRefreshToken(refreshToken);
        integration.setTokenExpiresAt(expiresAt);
        integration.setStatus(CalendarSyncStatus.CONNECTED);
        integration.setLastError(null);

        ExternalCalendarIntegration saved = integrationRepository.save(integration);

        return CalendarIntegrationStatusResponse.builder()
                .provider(provider)
                .providerName(impl.getDisplayName())
                .configured(true)
                .status(saved.getStatus())
                .accountEmail(saved.getAccountEmail())
                .calendarId(saved.getCalendarId())
                .lastSyncAt(saved.getLastSyncAt())
                .lastError(null)
                .mappedEventCount(0)
                .build();
    }

    @Transactional
    public void disconnect(Long organizationId, CalendarProvider provider) {
        Optional<ExternalCalendarIntegration> integrationOpt =
                integrationRepository.findByOrganizationIdAndProvider(organizationId, provider);

        if (integrationOpt.isPresent()) {
            ExternalCalendarIntegration integration = integrationOpt.get();
            integration.setStatus(CalendarSyncStatus.DISCONNECTED);
            integration.setAccessToken(null);
            integration.setRefreshToken(null);
            integration.setTokenExpiresAt(null);
            integrationRepository.save(integration);
        }
    }

    @Transactional
    public CalendarSyncResult syncProject(Long organizationId, Long projectId, CalendarProvider provider) {
        ExternalCalendarIntegration integration = integrationRepository
                .findByOrganizationIdAndProvider(organizationId, provider)
                .orElseThrow(() -> new IllegalStateException(provider + " is not connected for this organization."));

        if (integration.getStatus() != CalendarSyncStatus.CONNECTED && integration.getStatus() != CalendarSyncStatus.SYNCED) {
            throw new IllegalStateException("Calendar integration status is " + integration.getStatus() + ". Must be CONNECTED before syncing.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        if (project.getOrganization() != null && !project.getOrganization().getId().equals(organizationId)) {
            throw new TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }

        CalendarSyncProvider impl = getProviderImplementation(provider);

        List<CalendarEventDto> eventsToSync = collectProjectEvents(project);
        int total = eventsToSync.size();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();

        integration.setStatus(CalendarSyncStatus.SYNCING);
        integrationRepository.save(integration);

        for (CalendarEventDto eventDto : eventsToSync) {
            String hashInput = eventDto.getTitle() + "|" + eventDto.getStartDate() + "|" + eventDto.getEndDate() + "|" + (eventDto.getDescription() != null ? eventDto.getDescription() : "");
            String syncHash = ApiKeyService.sha256Hex(hashInput);

            Optional<CalendarEventMapping> mappingOpt = mappingRepository
                    .findByIntegrationIdAndEntityTypeAndEntityId(integration.getId(), eventDto.getEntityType(), eventDto.getEntityId());

            if (mappingOpt.isPresent()) {
                CalendarEventMapping mapping = mappingOpt.get();
                if (syncHash.equals(mapping.getSyncHash()) && mapping.getSyncStatus() == CalendarEventStatus.SYNCED) {
                    unchanged++;
                    details.add("Unchanged: [" + eventDto.getEntityType() + " #" + eventDto.getEntityId() + "] " + eventDto.getTitle());
                } else {
                    try {
                        impl.updateEvent(integration.getAccessToken(), integration.getCalendarId(), mapping.getExternalEventId(), eventDto);
                        mapping.setSyncHash(syncHash);
                        mapping.setSyncStatus(CalendarEventStatus.SYNCED);
                        mapping.setLastSyncedAt(LocalDateTime.now());
                        mapping.setLastError(null);
                        mappingRepository.save(mapping);
                        updated++;
                        details.add("Updated: [" + eventDto.getEntityType() + " #" + eventDto.getEntityId() + "] " + eventDto.getTitle());
                    } catch (Exception e) {
                        failed++;
                        mapping.setSyncStatus(CalendarEventStatus.FAILED);
                        mapping.setLastError(e.getMessage());
                        mappingRepository.save(mapping);
                        details.add("Failed update: [" + eventDto.getEntityType() + " #" + eventDto.getEntityId() + "] " + e.getMessage());
                    }
                }
            } else {
                try {
                    String externalEventId = impl.createEvent(integration.getAccessToken(), integration.getCalendarId(), eventDto);
                    CalendarEventMapping newMapping = CalendarEventMapping.builder()
                            .integration(integration)
                            .entityType(eventDto.getEntityType())
                            .entityId(eventDto.getEntityId())
                            .externalEventId(externalEventId)
                            .syncHash(syncHash)
                            .syncStatus(CalendarEventStatus.SYNCED)
                            .lastSyncedAt(LocalDateTime.now())
                            .build();
                    mappingRepository.save(newMapping);
                    created++;
                    details.add("Created: [" + eventDto.getEntityType() + " #" + eventDto.getEntityId() + "] " + eventDto.getTitle());
                } catch (Exception e) {
                    failed++;
                    details.add("Failed create: [" + eventDto.getEntityType() + " #" + eventDto.getEntityId() + "] " + e.getMessage());
                }
            }
        }

        integration.setLastSyncAt(LocalDateTime.now());
        if (failed == 0 || (created + updated + unchanged > 0)) {
            integration.setStatus(CalendarSyncStatus.SYNCED);
            integration.setLastError(null);
        } else {
            integration.setStatus(CalendarSyncStatus.FAILED);
            integration.setLastError("Failed to sync records with external calendar provider.");
        }
        integrationRepository.save(integration);

        boolean success = failed == 0;
        String msg = String.format("Calendar sync complete. %d created, %d updated, %d unchanged, %d failed.", created, updated, unchanged, failed);

        return CalendarSyncResult.builder()
                .provider(provider)
                .projectId(projectId)
                .totalRecords(total)
                .eventsCreated(created)
                .eventsUpdated(updated)
                .eventsSkippedUnchanged(unchanged)
                .eventsFailed(failed)
                .success(success)
                .message(msg)
                .details(details)
                .build();
    }

    private List<CalendarEventDto> collectProjectEvents(Project project) {
        List<CalendarEventDto> list = new ArrayList<>();

        // 1. Tasks
        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        for (Task t : tasks) {
            LocalDate start = t.getPlannedStartDate() != null ? t.getPlannedStartDate() : t.getStartDate();
            LocalDate end = t.getPlannedFinishDate() != null ? t.getPlannedFinishDate() : t.getDeadline();
            if (start != null && end != null) {
                list.add(CalendarEventDto.builder()
                        .title("[Task] " + t.getName() + " (" + project.getName() + ")")
                        .description("Task status: " + t.getStatus() + "\nPriority: " + t.getPriority() + (t.getDescription() != null ? "\n" + t.getDescription() : ""))
                        .startDate(start)
                        .endDate(end)
                        .isAllDay(true)
                        .entityType("TASK")
                        .entityId(t.getId())
                        .build());
            }
        }

        // 2. Project Milestone / Deadline
        if (project.getDeadline() != null) {
            list.add(CalendarEventDto.builder()
                    .title("[Milestone] " + project.getName() + " Final Deadline")
                    .description("Project final delivery deadline for " + project.getName())
                    .startDate(project.getDeadline())
                    .endDate(project.getDeadline())
                    .isAllDay(true)
                    .entityType("MILESTONE")
                    .entityId(project.getId())
                    .build());
        }

        // 3. Project Deliverables
        List<Deliverable> deliverables = deliverableRepository.findByProjectId(project.getId());
        for (Deliverable d : deliverables) {
            LocalDate decisionDate = d.getRequestedDecisionDate() != null ? d.getRequestedDecisionDate() : d.getRequiredSubmissionDate();
            if (decisionDate != null) {
                list.add(CalendarEventDto.builder()
                        .title("[Deliverable] " + d.getName() + " (" + d.getStatus() + ")")
                        .description("Deliverable submission deadline for " + project.getName() + "\nType: " + d.getDeliverableType())
                        .startDate(decisionDate)
                        .endDate(decisionDate)
                        .isAllDay(true)
                        .entityType("DELIVERABLE")
                        .entityId(d.getId())
                        .build());
            }
        }

        return list;
    }
}
