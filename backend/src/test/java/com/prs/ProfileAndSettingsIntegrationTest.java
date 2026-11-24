package com.prs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Employee Profile Change Approval Workflow & User Preferences Integration Tests")
public class ProfileAndSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private ProfileChangeRequestRepository profileChangeRequestRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminJwt;
    private String workerJwt;
    private Organization testOrg;
    private User workerUser;
    private User adminUser;

    @BeforeEach
    void setUp() throws Exception {
        profileChangeRequestRepository.deleteAll();

        testOrg = organizationRepository.findBySlug("acme-global")
                .orElseGet(() -> {
                    Organization org = new Organization();
                    org.setName("Acme Global Solutions");
                    org.setSlug("acme-global");
                    org.setOrgCode("ACM");
                    org.setStatus("ACTIVE");
                    return organizationRepository.save(org);
                });

        adminUser = userRepository.findByUsername("admin").orElseThrow();
        adminUser.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(adminUser);

        workerUser = userRepository.findByUsername("worker").orElseThrow();
        workerUser.setPassword(passwordEncoder.encode("admin"));
        // Reset worker official profile
        workerUser.setFullName("Original Worker Name");
        workerUser.setPhone("+1-555-0100");
        workerUser.setJobTitle("Junior Developer");
        workerUser.setDepartment("Engineering");
        workerUser.setEmail("worker.official@syncora.test");
        userRepository.save(workerUser);

        // Ensure Acme memberships
        if (!membershipRepository.existsByUserIdAndOrganizationId(adminUser.getId(), testOrg.getId())) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(testOrg);
            m.setRole("ORGANIZATION_OWNER");
            m.setStatus("ACTIVE");
            m.setEmployeeId("ACM-EMP-0001");
            membershipRepository.save(m);
        }

        if (!membershipRepository.existsByUserIdAndOrganizationId(workerUser.getId(), testOrg.getId())) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(workerUser);
            m.setOrganization(testOrg);
            m.setRole("WORKER");
            m.setStatus("ACTIVE");
            m.setEmployeeId("ACM-EMP-0003");
            membershipRepository.save(m);
        }

        // Login Admin
        adminJwt = loginAndGetToken("admin", "admin");
        // Login Worker
        workerJwt = loginAndGetToken("worker", "admin");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setUsername(username);
        req.setPassword(password);

        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        return resp.getToken();
    }

    @Test
    @DisplayName("1. Fetch Official Profile returns authenticated user details and active membership")
    void testGetOfficialProfile() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/users/me/profile")
                        .header("Authorization", "Bearer " + workerJwt))
                .andExpect(status().isOk())
                .andReturn();

        UserProfileDto profile = objectMapper.readValue(res.getResponse().getContentAsString(), UserProfileDto.class);
        assertThat(profile.getUsername()).isEqualTo("worker");
        assertThat(profile.getFullName()).isEqualTo("Original Worker Name");
        assertThat(profile.getJobTitle()).isEqualTo("Junior Developer");
        assertThat(profile.getDepartment()).isEqualTo("Engineering");
        assertThat(profile.getEmployeeId()).isEqualTo("ACM-EMP-0003");
        assertThat(profile.isHasPendingChangeRequest()).isFalse();
    }

    @Test
    @DisplayName("2. Employee Submits Profile Changes: Pending request created while official profile remains untouched")
    void testSubmitProfileChangeRequestPendingState() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedFullName("Promoted Worker Name")
                .proposedJobTitle("Lead Architect")
                .proposedDepartment("Platform Infrastructure")
                .proposedPhone("+1-555-9999")
                .submissionComment("Promotion to Lead Architect")
                .build();

        MvcResult res = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto reqDto = objectMapper.readValue(res.getResponse().getContentAsString(), ProfileChangeRequestDto.class);
        assertThat(reqDto.getStatus()).isEqualTo(ProfileChangeRequestStatus.PENDING);
        assertThat(reqDto.getProposedFullName()).isEqualTo("Promoted Worker Name");
        assertThat(reqDto.getProposedJobTitle()).isEqualTo("Lead Architect");
        assertThat(reqDto.getExistingFullName()).isEqualTo("Original Worker Name");

        // Verify official database user profile is NOT changed
        User currentDbUser = userRepository.findById(workerUser.getId()).orElseThrow();
        assertThat(currentDbUser.getFullName()).isEqualTo("Original Worker Name");
        assertThat(currentDbUser.getJobTitle()).isEqualTo("Junior Developer");
    }

    @Test
    @DisplayName("3. Prevent Duplicate Unresolved Requests: Submitting another pending request fails")
    void testPreventDuplicatePendingRequests() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedFullName("Promoted Worker Name")
                .build();

        mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated());

        // Attempt second submission
        mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. Authorized Organization Administrator Approves Request: Changes applied transactionally")
    void testAdminApprovalAppliesChanges() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedFullName("Promoted Worker Name")
                .proposedJobTitle("Lead Architect")
                .proposedDepartment("Core Team")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto createdReq = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);

        // Administrator reviews and approves
        ReviewProfileChangeRequest review = ReviewProfileChangeRequest.builder()
                .comment("Congratulations on the promotion!")
                .build();

        MvcResult approveResult = mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk())
                .andReturn();

        ProfileChangeRequestDto approvedDto = objectMapper.readValue(approveResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);
        assertThat(approvedDto.getStatus()).isEqualTo(ProfileChangeRequestStatus.APPROVED);
        assertThat(approvedDto.getReviewedByUsername()).isEqualTo("admin");
        assertThat(approvedDto.getReviewComment()).isEqualTo("Congratulations on the promotion!");

        // Official profile must now be updated in the database
        User updatedUser = userRepository.findById(workerUser.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Promoted Worker Name");
        assertThat(updatedUser.getJobTitle()).isEqualTo("Lead Architect");
        assertThat(updatedUser.getDepartment()).isEqualTo("Core Team");
    }

    @Test
    @DisplayName("5. Self-Approval Rejection: Organization administrator cannot approve their own profile request")
    void testSelfApprovalProhibited() throws Exception {
        // Admin submits a change for their own profile
        SubmitProfileChangeRequest adminSubmit = SubmitProfileChangeRequest.builder()
                .proposedFullName("Self Modifying Admin")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminSubmit)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto req = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);

        // Admin attempts self-approval
        mockMvc.perform(post("/api/profile-change-requests/" + req.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReviewProfileChangeRequest("Self approving"))))
                .andExpect(status().isBadRequest());

        // Verify admin profile was NOT changed
        User currentAdmin = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(currentAdmin.getFullName()).isNotEqualTo("Self Modifying Admin");
    }

    @Test
    @DisplayName("6. Request Rejection: Preserves official profile and stores rejection reason")
    void testAdminRejectionPreservesProfile() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedFullName("Rejected Name Change")
                .proposedJobTitle("Chief Everything Officer")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto createdReq = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);

        // Admin rejects
        ReviewProfileChangeRequest review = ReviewProfileChangeRequest.builder()
                .comment("Requested job title is not authorized in current org chart.")
                .build();

        MvcResult rejectResult = mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(review)))
                .andExpect(status().isOk())
                .andReturn();

        ProfileChangeRequestDto rejectedDto = objectMapper.readValue(rejectResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);
        assertThat(rejectedDto.getStatus()).isEqualTo(ProfileChangeRequestStatus.REJECTED);
        assertThat(rejectedDto.getReviewComment()).contains("not authorized");

        // Official profile remains unchanged
        User dbUser = userRepository.findById(workerUser.getId()).orElseThrow();
        assertThat(dbUser.getFullName()).isEqualTo("Original Worker Name");
        assertThat(dbUser.getJobTitle()).isEqualTo("Junior Developer");
    }

    @Test
    @DisplayName("7. Stale Request Detection: Modified official profile marks pending request STALE")
    void testStaleRequestDetection() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedFullName("New Name")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto createdReq = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);

        // Intervening change: Official profile updated directly
        workerUser.setFullName("Intervening Name Change");
        userRepository.save(workerUser);

        // Admin attempts approval: Should be rejected as STALE
        mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Verify request was marked STALE in database
        ProfileChangeRequest staleReq = profileChangeRequestRepository.findById(createdReq.getId()).orElseThrow();
        assertThat(staleReq.getStatus()).isEqualTo(ProfileChangeRequestStatus.STALE);
    }

    @Test
    @DisplayName("8. Sensitive Work Email Verification: Token verification required before approval")
    void testWorkEmailVerificationWorkflow() throws Exception {
        SubmitProfileChangeRequest submitReq = SubmitProfileChangeRequest.builder()
                .proposedWorkEmail("worker.new@syncora.test")
                .build();

        MvcResult submitResult = mockMvc.perform(post("/api/users/me/profile/change-requests")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ProfileChangeRequestDto createdReq = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ProfileChangeRequestDto.class);
        assertThat(createdReq.getEmailVerified()).isFalse();

        // Attempt approval before token verification -> fails
        mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Retrieve generated token from entity
        ProfileChangeRequest reqEntity = profileChangeRequestRepository.findById(createdReq.getId()).orElseThrow();
        String token = reqEntity.getEmailVerificationToken();
        assertThat(token).isNotBlank();

        // Submit token verification
        mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/verify-email")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk());

        // Now approval succeeds
        mockMvc.perform(post("/api/profile-change-requests/" + createdReq.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Official email is now updated
        User updatedUser = userRepository.findById(workerUser.getId()).orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("worker.new@syncora.test");
    }

    @Test
    @DisplayName("9. User Preferences: Theme, Localization, Density persist independently without admin approval")
    void testUserPreferencesPersistence() throws Exception {
        UserPreferencesDto prefs = UserPreferencesDto.builder()
                .theme("DARK")
                .language("hi")
                .timezone("Asia/Kolkata")
                .dateFormat("DD/MM/YYYY")
                .firstDayOfWeek("MONDAY")
                .textSize("LARGE")
                .displayDensity("COMPACT")
                .reducedMotion(true)
                .highContrastFocus(true)
                .inAppNotifications(true)
                .emailNotifications(true)
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/users/me/preferences")
                        .header("Authorization", "Bearer " + workerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prefs)))
                .andExpect(status().isOk())
                .andReturn();

        UserPreferencesDto updated = objectMapper.readValue(updateResult.getResponse().getContentAsString(), UserPreferencesDto.class);
        assertThat(updated.getTheme()).isEqualTo("DARK");
        assertThat(updated.getLanguage()).isEqualTo("hi");
        assertThat(updated.getTextSize()).isEqualTo("LARGE");
        assertThat(updated.getDisplayDensity()).isEqualTo("COMPACT");
        assertThat(updated.getReducedMotion()).isTrue();

        // Verify retrieval endpoint
        MvcResult getResult = mockMvc.perform(get("/api/users/me/preferences")
                        .header("Authorization", "Bearer " + workerJwt))
                .andExpect(status().isOk())
                .andReturn();

        UserPreferencesDto fetched = objectMapper.readValue(getResult.getResponse().getContentAsString(), UserPreferencesDto.class);
        assertThat(fetched.getTheme()).isEqualTo("DARK");
        assertThat(fetched.getTimezone()).isEqualTo("Asia/Kolkata");

        // Verify Reset to Defaults
        MvcResult resetResult = mockMvc.perform(post("/api/users/me/preferences/reset")
                        .header("Authorization", "Bearer " + workerJwt))
                .andExpect(status().isOk())
                .andReturn();

        UserPreferencesDto reset = objectMapper.readValue(resetResult.getResponse().getContentAsString(), UserPreferencesDto.class);
        assertThat(reset.getTheme()).isEqualTo("SYSTEM");
        assertThat(reset.getLanguage()).isEqualTo("en");
        assertThat(reset.getTextSize()).isEqualTo("DEFAULT");
    }
}
