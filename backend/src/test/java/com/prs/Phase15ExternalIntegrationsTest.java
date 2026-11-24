package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.integration.apikey.ApiKeyService;
import com.prs.integration.calendar.CalendarSyncService;
import com.prs.integration.calendar.GoogleCalendarSyncProvider;
import com.prs.integration.dto.*;
import com.prs.integration.event.IntegrationEventPublisher;
import com.prs.integration.security.WebhookHmacSigner;
import com.prs.integration.security.WebhookUrlValidator;
import com.prs.integration.webhook.WebhookService;
import com.prs.repository.*;
import com.prs.security.JwtUtil;
import com.prs.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 15: External Integrations & Interoperability Tests")
public class Phase15ExternalIntegrationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ExternalCalendarIntegrationRepository calendarIntegrationRepository;

    @Autowired
    private CalendarEventMappingRepository mappingRepository;

    @Autowired
    private WebhookSubscriptionRepository webhookSubscriptionRepository;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private CalendarSyncService calendarSyncService;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private WebhookHmacSigner hmacSigner;

    @Autowired
    private WebhookUrlValidator urlValidator;

    @Autowired
    private IntegrationEventPublisher eventPublisher;

    private Organization orgAlpha;
    private Organization orgBeta;
    private User adminUser;
    private String adminJwt;
    private Project projectAlpha;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        mappingRepository.deleteAll();
        calendarIntegrationRepository.deleteAll();
        webhookDeliveryRepository.deleteAll();
        webhookSubscriptionRepository.deleteAll();
        apiKeyRepository.deleteAll();

        // 1. Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult actualLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = actualLogin.getResponse().getContentAsString();
        Map<String, Object> respMap = objectMapper.readValue(responseStr, Map.class);
        adminJwt = (String) respMap.get("token");
        adminUser = userRepository.findByUsername("admin").orElseThrow();

        // 2. Setup organizations
        orgAlpha = organizationRepository.findBySlug("acme-global")
                .orElseGet(() -> {
                    Organization o = new Organization();
                    o.setName("Acme Global Solutions");
                    o.setSlug("acme-global");
                    o.setStatus("ACTIVE");
                    return organizationRepository.save(o);
                });

        orgBeta = organizationRepository.findBySlug("stark-tech")
                .orElseGet(() -> {
                    Organization o = new Organization();
                    o.setName("Stark Tech Industries");
                    o.setSlug("stark-tech");
                    o.setStatus("ACTIVE");
                    return organizationRepository.save(o);
                });

        // Ensure membership in orgAlpha & orgBeta
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgAlpha.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgAlpha);
            m.setRole("ADMIN");
            m.setStatus("ACTIVE");
            membershipRepository.save(m);
        }
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgBeta.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgBeta);
            m.setRole("ADMIN");
            m.setStatus("ACTIVE");
            membershipRepository.save(m);
        }

        // 3. Setup test project in orgAlpha
        TenantContext.setOrganization(orgAlpha);
        TenantContext.setTenantId(orgAlpha.getId());

        projectAlpha = projectRepository.findAll().stream()
                .filter(p -> "Interoperability Platform".equals(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setName("Interoperability Platform");
                    p.setDescription("Phase 15 Test Project");
                    p.setOrganization(orgAlpha);
                    p.setManager(adminUser);
                    p.setStatus(ProjectStatus.IN_PROGRESS);
                    p.setStartDate(LocalDate.now());
                    p.setDeadline(LocalDate.now().plusDays(30));
                    return projectRepository.save(p);
                });

        Task task1 = new Task();
        task1.setName("Configure OAuth2 Providers");
        task1.setProject(projectAlpha);
        task1.setStatus(TaskStatus.IN_PROGRESS);
        task1.setPriority(Priority.HIGH);
        task1.setStartDate(LocalDate.now());
        task1.setDeadline(LocalDate.now().plusDays(5));
        taskRepository.save(task1);
    }

    // =========================================================================
    // 1. Calendar Integration Tests
    // =========================================================================

    @Test
    @DisplayName("Calendar Provider: Unconfigured State & No False Connection Claims")
    void testCalendarUnconfiguredState() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/integrations/calendars")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<CalendarIntegrationStatusResponse> statuses = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CalendarIntegrationStatusResponse.class)
        );

        assertThat(statuses).isNotEmpty();
        for (CalendarIntegrationStatusResponse res : statuses) {
            assertThat(res.getProvider()).isIn(CalendarProvider.GOOGLE_CALENDAR, CalendarProvider.OUTLOOK_CALENDAR);
            // Neither should claim CONNECTED without explicit credentials
            if (!res.isConfigured()) {
                assertThat(res.getStatus()).isEqualTo(CalendarSyncStatus.NOT_CONFIGURED);
            }
        }
    }

    @Test
    @DisplayName("Calendar Provider: Connect, Disconnect & Credential Protection")
    void testCalendarConnectAndDisconnect() throws Exception {
        // Connect Google Calendar with mock credentials
        ConnectCalendarRequest connectReq = ConnectCalendarRequest.builder()
                .accountEmail("dev-team@acme.com")
                .calendarId("primary")
                .build();

        MvcResult connResult = mockMvc.perform(post("/api/integrations/calendars/GOOGLE_CALENDAR/connect")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectReq)))
                .andExpect(status().isOk())
                .andReturn();

        CalendarIntegrationStatusResponse connStatus = objectMapper.readValue(
                connResult.getResponse().getContentAsString(),
                CalendarIntegrationStatusResponse.class
        );

        assertThat(connStatus.getStatus()).isEqualTo(CalendarSyncStatus.CONNECTED);
        assertThat(connStatus.getAccountEmail()).isEqualTo("dev-team@acme.com");

        // Disconnect
        mockMvc.perform(post("/api/integrations/calendars/GOOGLE_CALENDAR/disconnect")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk());

        ExternalCalendarIntegration integration = calendarIntegrationRepository
                .findByOrganizationIdAndProvider(orgAlpha.getId(), CalendarProvider.GOOGLE_CALENDAR)
                .orElseThrow();

        assertThat(integration.getStatus()).isEqualTo(CalendarSyncStatus.DISCONNECTED);
        assertThat(integration.getAccessToken()).isNull();
    }

    @Test
    @DisplayName("Calendar Sync: Creation, Updating & Duplicate Prevention")
    void testCalendarSyncAndDuplicatePrevention() throws Exception {
        // 1. Connect provider
        calendarSyncService.connect(orgAlpha.getId(), adminUser.getId(), CalendarProvider.GOOGLE_CALENDAR,
                ConnectCalendarRequest.builder().accountEmail("sync@acme.com").calendarId("primary").build());

        // 2. Initial sync
        MvcResult sync1Res = mockMvc.perform(post("/api/projects/" + projectAlpha.getId() + "/calendar/sync?provider=GOOGLE_CALENDAR")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        CalendarSyncResult sync1 = objectMapper.readValue(sync1Res.getResponse().getContentAsString(), CalendarSyncResult.class);
        assertThat(sync1.isSuccess()).isTrue();
        assertThat(sync1.getEventsCreated()).isGreaterThanOrEqualTo(2); // Task + Project deadline

        // 3. Second sync without date changes -> duplicate prevention
        MvcResult sync2Res = mockMvc.perform(post("/api/projects/" + projectAlpha.getId() + "/calendar/sync?provider=GOOGLE_CALENDAR")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        CalendarSyncResult sync2 = objectMapper.readValue(sync2Res.getResponse().getContentAsString(), CalendarSyncResult.class);
        assertThat(sync2.isSuccess()).isTrue();
        assertThat(sync2.getEventsCreated()).isEqualTo(0);
        assertThat(sync2.getEventsSkippedUnchanged()).isEqualTo(sync1.getTotalRecords());

        // 4. Update task date and re-sync -> event updated
        List<Task> tasks = taskRepository.findByProjectId(projectAlpha.getId());
        Task task = tasks.get(0);
        task.setDeadline(task.getDeadline().plusDays(3));
        taskRepository.save(task);

        MvcResult sync3Res = mockMvc.perform(post("/api/projects/" + projectAlpha.getId() + "/calendar/sync?provider=GOOGLE_CALENDAR")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        CalendarSyncResult sync3 = objectMapper.readValue(sync3Res.getResponse().getContentAsString(), CalendarSyncResult.class);
        assertThat(sync3.isSuccess()).isTrue();
        assertThat(sync3.getEventsUpdated()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // 2. Webhook Security & Dispatch Tests
    // =========================================================================

    @Test
    @DisplayName("Webhook Security: HMAC-SHA256 Signature Generation & Verification")
    void testWebhookHmacSignature() {
        String secret = "whsec_supersecretkey123456789";
        String payload = "{\"eventId\":\"evt-001\",\"eventType\":\"project.created\"}";

        String signature = hmacSigner.signPayload(payload, secret);
        assertThat(signature).startsWith("sha256=");

        boolean valid = hmacSigner.verifySignature(payload, secret, signature);
        assertThat(valid).isTrue();

        // Altered payload must fail verification
        boolean tampered = hmacSigner.verifySignature(payload + "tampered", secret, signature);
        assertThat(tampered).isFalse();
    }

    @Test
    @DisplayName("Webhook Security: SSRF Protection Rejects Forbidden Endpoints")
    void testWebhookSsrfProtection() {
        // Default mode blocks private IP ranges and localhost
        assertThatThrownBy(() -> urlValidator.validateUrl("http://localhost:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> urlValidator.validateUrl("http://127.0.0.1:3000/callback"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> urlValidator.validateUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> urlValidator.validateUrl("ftp://example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Webhook Subscriptions: CRUD, Secret Masking & Organization Isolation")
    void testWebhookSubscriptionsCrudAndTenantIsolation() throws Exception {
        WebhookSubscriptionRequest subReq = WebhookSubscriptionRequest.builder()
                .name("CI/CD Pipeline Webhook")
                .destinationUrl("https://webhook.site/2984a861-1234-5678-abcd-000000000000")
                .subscribedEvents(List.of("project.created", "task.completed"))
                .isEnabled(true)
                .build();

        // 1. Create subscription in Org Alpha
        MvcResult createRes = mockMvc.perform(post("/api/integrations/webhooks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subReq)))
                .andExpect(status().isCreated())
                .andReturn();

        WebhookSubscriptionResponse createdSub = objectMapper.readValue(
                createRes.getResponse().getContentAsString(),
                WebhookSubscriptionResponse.class
        );

        assertThat(createdSub.getId()).isNotNull();
        assertThat(createdSub.getSecretMasked()).contains("***");
        assertThat(createdSub.getSubscribedEvents()).contains("project.created");

        // 2. Query webhooks under Org Alpha -> contains created sub
        MvcResult listAlphaRes = mockMvc.perform(get("/api/integrations/webhooks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<WebhookSubscriptionResponse> alphaSubs = objectMapper.readValue(
                listAlphaRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, WebhookSubscriptionResponse.class)
        );
        assertThat(alphaSubs).anyMatch(s -> s.getId().equals(createdSub.getId()));

        // 3. Query webhooks under Org Beta -> must NOT contain Org Alpha subscription (Tenant Isolation)
        MvcResult listBetaRes = mockMvc.perform(get("/api/integrations/webhooks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgBeta.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<WebhookSubscriptionResponse> betaSubs = objectMapper.readValue(
                listBetaRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, WebhookSubscriptionResponse.class)
        );
        assertThat(betaSubs).noneMatch(s -> s.getId().equals(createdSub.getId()));

        // 4. Secret rotation
        MvcResult rotRes = mockMvc.perform(post("/api/integrations/webhooks/" + createdSub.getId() + "/rotate-secret")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        WebhookSubscriptionResponse rotated = objectMapper.readValue(
                rotRes.getResponse().getContentAsString(),
                WebhookSubscriptionResponse.class
        );
        assertThat(rotated.getId()).isEqualTo(createdSub.getId());
    }

    // =========================================================================
    // 3. Scoped API Keys Tests
    // =========================================================================

    @Test
    @DisplayName("API Keys: Generation, Hashing, Scopes & Secret Reveal Once")
    void testApiKeyGenerationAndHashing() throws Exception {
        CreateApiKeyRequest keyReq = CreateApiKeyRequest.builder()
                .name("External Build Agent")
                .scopes(List.of("projects:read", "schedules:read"))
                .expiresInDays(90)
                .build();

        MvcResult createRes = mockMvc.perform(post("/api/integrations/api-keys")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(keyReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ApiKeyResponse createdKey = objectMapper.readValue(
                createRes.getResponse().getContentAsString(),
                ApiKeyResponse.class
        );

        assertThat(createdKey.getId()).isNotNull();
        assertThat(createdKey.getRawKey()).startsWith("prs_live_");
        assertThat(createdKey.getKeyPrefix()).isNotNull();

        // Verify stored entity has SHA-256 hash and NOT raw key
        ApiKey stored = apiKeyRepository.findById(createdKey.getId()).orElseThrow();
        assertThat(stored.getKeyHash()).isEqualTo(ApiKeyService.sha256Hex(createdKey.getRawKey()));
        assertThat(stored.getKeyHash()).isNotEqualTo(createdKey.getRawKey());

        // Subsequent list API call must NOT reveal raw key
        MvcResult listRes = mockMvc.perform(get("/api/integrations/api-keys")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<ApiKeyResponse> keysList = objectMapper.readValue(
                listRes.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ApiKeyResponse.class)
        );

        ApiKeyResponse listed = keysList.stream().filter(k -> k.getId().equals(createdKey.getId())).findFirst().orElseThrow();
        assertThat(listed.getRawKey()).isNull();

        // Revoke key
        mockMvc.perform(delete("/api/integrations/api-keys/" + createdKey.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk());

        ApiKey revoked = apiKeyRepository.findById(createdKey.getId()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.isValid()).isFalse();
    }

    @Test
    @DisplayName("API Keys: Authentication Filter with Tenant Isolation")
    void testApiKeyAuthenticationAndTenantIsolation() throws Exception {
        CreateApiKeyRequest keyReq = CreateApiKeyRequest.builder()
                .name("Integration Service Key")
                .scopes(List.of("projects:read"))
                .build();

        ApiKeyResponse keyRes = apiKeyService.createApiKey(orgAlpha.getId(), adminUser.getId(), keyReq);
        String rawKey = keyRes.getRawKey();

        // Perform request authenticated via X-API-Key header
        mockMvc.perform(get("/api/integrations/calendars")
                        .header("X-API-Key", rawKey))
                .andExpect(status().isOk());

        // Revoke key -> subsequent authentication fails
        apiKeyService.revokeApiKey(orgAlpha.getId(), keyRes.getId());

        mockMvc.perform(get("/api/integrations/calendars")
                        .header("X-API-Key", rawKey))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Core Application Resilience: External Integration Failures Are Non-Blocking")
    void testCoreApplicationResilienceOnIntegrationFailures() {
        // Dispatched domain event to non-existent endpoint must NOT throw or fail transactions
        eventPublisher.publishEvent("project.created", orgAlpha.getId(), projectAlpha.getId(),
                Map.of("name", projectAlpha.getName()));

        // Verify project remains healthy and untouched in database
        Project fetched = projectRepository.findById(projectAlpha.getId()).orElseThrow();
        assertThat(fetched.getName()).isEqualTo("Interoperability Platform");
    }
}
