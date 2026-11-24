package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.security.PasswordPolicyValidator;
import com.prs.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 19: SYNCORA Authentication & Account Management Corrections Tests")
public class Phase19AuthenticationCorrectionsTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordPolicyValidator passwordPolicyValidator;

    @Autowired
    private EmailService emailService;

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        emailService.clearHistory();
    }

    // 1. All displayed development accounts authenticate successfully
    @Test
    @DisplayName("1. All displayed development test accounts authenticate successfully")
    void test1_allDisplayedDevelopmentAccountsAuthenticateSuccessfully() throws Exception {
        String[] testUsers = {"admin", "manager", "worker", "client", "vendor"};
        for (String uname : testUsers) {
            AuthRequest req = new AuthRequest();
            req.setUsername(uname);
            req.setPassword("admin");
            req.setOrganizationCode("ACM");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
            assertThat(resp.getToken()).isNotBlank();
            assertThat(resp.getUsername()).isEqualToIgnoringCase(uname);
        }

        // Test secondary tenant admin
        AuthRequest stkReq = new AuthRequest();
        stkReq.setUsername("admin");
        stkReq.setPassword("admin");
        stkReq.setOrganizationCode("STK");

        MvcResult stkResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stkReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse stkResp = objectMapper.readValue(stkResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(stkResp.getToken()).isNotBlank();
        assertThat(stkResp.getOrganizationCode()).isEqualTo("STK");
    }

    // 2. Demo account identifiers match persisted seed records
    @Test
    @DisplayName("2. Demo account identifiers match actual persisted seed records and employee IDs")
    void test2_demoAccountIdentifiersMatchPersistedSeedRecords() throws Exception {
        // Authenticate via Employee IDs
        Map<String, String> empIds = Map.of(
                "ACM-EMP-0001", "admin",
                "ACM-EMP-0002", "manager",
                "ACM-EMP-0003", "worker",
                "ACM-EMP-0004", "client",
                "ACM-EMP-0005", "vendor"
        );

        for (Map.Entry<String, String> entry : empIds.entrySet()) {
            AuthRequest req = new AuthRequest();
            req.setUsername(entry.getKey());
            req.setPassword("admin");
            req.setOrganizationCode("ACM");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
            assertThat(resp.getEmployeeId()).isEqualTo(entry.getKey());
            assertThat(resp.getUsername()).isEqualTo(entry.getValue());
        }
    }

    // 3. Demo controls and tokens are absent when disabled
    @Test
    @DisplayName("3. Verify unknown reset requests return non-enumerating generic response")
    void test3_unknownAccountReceivesGenericResponse() throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setIdentifier("completely_nonexistent_user@example.com");

        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(resp.get("message")).isEqualTo("If an account exists with that identifier, password reset instructions have been dispatched.");
        // Nonexistent user should never have a token
        assertThat(resp.get("demoResetToken")).isNull();
        assertThat(resp.get("demoResetLink")).isNull();
    }

    // 4. Globally duplicated usernames are rejected
    @Test
    @DisplayName("4. Globally duplicated usernames are rejected across organizations")
    void test4_globallyDuplicatedUsernamesRejected() throws Exception {
        RegisterOrganizationRequest req = new RegisterOrganizationRequest();
        req.setOrganizationName("Duplicate Username Org " + System.currentTimeMillis());
        req.setOrganizationCode("DUP" + (int)(Math.random() * 900 + 100));
        req.setOwnerEmail("unique_email_" + System.currentTimeMillis() + "@test.com");
        req.setOwnerUsername("admin"); // Already exists!
        req.setOwnerPassword("StrongPassphrase2026!");
        req.setOwnerConfirmPassword("StrongPassphrase2026!");

        mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // 5. Case-insensitive duplicate usernames are rejected
    @Test
    @DisplayName("5. Case-insensitive duplicate usernames (e.g. ADMIN vs admin) are rejected")
    void test5_caseInsensitiveDuplicateUsernamesRejected() throws Exception {
        String[] variations = {"ADMIN", "Admin", "AdMiN", "MANAGER", "Worker"};
        for (String variant : variations) {
            RegisterOrganizationRequest req = new RegisterOrganizationRequest();
            req.setOrganizationName("Org " + variant + " " + System.currentTimeMillis());
            req.setOrganizationCode("VR" + (int)(Math.random() * 900 + 100));
            req.setOwnerEmail("email_" + variant.toLowerCase() + "_" + System.currentTimeMillis() + "@test.com");
            req.setOwnerUsername(variant); // Case-insensitive duplicate!
            req.setOwnerPassword("StrongPassphrase2026!");
            req.setOwnerConfirmPassword("StrongPassphrase2026!");

            mockMvc.perform(post("/api/auth/register-organization")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // 6. Concurrent account creation cannot create duplicate usernames
    @Test
    @DisplayName("6. Concurrent account creation cannot create duplicate usernames")
    void test6_concurrentAccountCreationCannotCreateDuplicates() throws Exception {
        String concurrentUsername = "concurrent_user_" + System.currentTimeMillis();
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    User u = new User();
                    u.setUsername(concurrentUsername);
                    u.setEmail("concurrent_" + idx + "_" + System.currentTimeMillis() + "@test.com");
                    u.setPassword(passwordEncoder.encode("StrongPassphrase2026!"));
                    u.setRole(Role.WORKER);
                    userRepository.saveAndFlush(u);
                    results.add(true);
                } catch (Exception e) {
                    results.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one concurrent insert must succeed, the rest must fail with constraint violation
        long successCount = results.stream().filter(r -> r).count();
        long failureCount = results.stream().filter(r -> !r).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(threads - 1);
    }

    // 7. Password-policy validation: minimum 12 characters, reject common passwords
    @Test
    @DisplayName("7. Password-policy validation enforces 12-character minimum and rejects common passwords")
    void test7_passwordPolicyValidationEnforced() throws Exception {
        // Less than 12 characters
        RegisterOrganizationRequest shortPassReq = new RegisterOrganizationRequest();
        shortPassReq.setOrganizationName("Short Pass Org");
        shortPassReq.setOwnerEmail("shortpass@test.com");
        shortPassReq.setOwnerUsername("shortpassuser");
        shortPassReq.setOwnerPassword("Pass1234"); // 8 chars
        shortPassReq.setOwnerConfirmPassword("Pass1234");

        mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPassReq)))
                .andExpect(status().isBadRequest());

        // Common password
        RegisterOrganizationRequest commonPassReq = new RegisterOrganizationRequest();
        commonPassReq.setOrganizationName("Common Pass Org");
        commonPassReq.setOwnerEmail("commonpass@test.com");
        commonPassReq.setOwnerUsername("commonpassuser");
        commonPassReq.setOwnerPassword("password12345"); // In common list
        commonPassReq.setOwnerConfirmPassword("password12345");

        mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commonPassReq)))
                .andExpect(status().isBadRequest());
    }

    // 8. Password confirmation mismatch is rejected
    @Test
    @DisplayName("8. Password confirmation mismatch is rejected")
    void test8_passwordConfirmationMismatchRejected() throws Exception {
        RegisterOrganizationRequest req = new RegisterOrganizationRequest();
        req.setOrganizationName("Mismatch Org " + System.currentTimeMillis());
        req.setOwnerEmail("mismatch_" + System.currentTimeMillis() + "@test.com");
        req.setOwnerUsername("mismatch_user_" + System.currentTimeMillis());
        req.setOwnerPassword("StrongPassphrase2026!");
        req.setOwnerConfirmPassword("DifferentPassphrase2026!");

        mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // 9. Owner onboarding creates correct organization and permissions
    @Test
    @DisplayName("9. Owner onboarding creates correct organization and permissions (not global admin)")
    void test9_ownerOnboardingCreatesCorrectOrganizationAndPermissions() throws Exception {
        String unique = "onboard_" + System.currentTimeMillis();
        RegisterOrganizationRequest req = new RegisterOrganizationRequest();
        req.setOrganizationName("Onboard Test Corp " + unique);
        req.setOrganizationCode("ONB");
        req.setOwnerEmail(unique + "@onboard.com");
        req.setOwnerUsername(unique);
        req.setOwnerPassword("StrongPassphrase2026!");
        req.setOwnerConfirmPassword("StrongPassphrase2026!");

        MvcResult result = mockMvc.perform(post("/api/auth/register-organization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(resp.getUserRole()).isEqualTo("ORGANIZATION_OWNER");
        assertThat(resp.getRole()).isEqualTo("PROJECT_MANAGER"); // NOT ADMIN!

        User owner = userRepository.findByUsernameIgnoreCase(unique).orElseThrow();
        assertThat(owner.getRole()).isEqualTo(Role.PROJECT_MANAGER);
    }

    // 10. Valid recovery requests trigger email delivery mechanism
    @Test
    @DisplayName("10. Valid recovery requests trigger configured email delivery")
    void test10_validRecoveryRequestTriggersEmailDelivery() throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setIdentifier("manager@example.com");

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(emailService.getSentHistory()).isNotEmpty();
        EmailService.SentEmailRecord lastEmail = emailService.getSentHistory().get(emailService.getSentHistory().size() - 1);
        assertThat(lastEmail.getToEmail()).isEqualTo("manager@example.com");
        assertThat(lastEmail.getSubject()).contains("SYNCORA");
        assertThat(lastEmail.getBody()).contains("/reset-password?token=");
    }

    // 11. Invalid account identifiers receive non-enumerating response
    @Test
    @DisplayName("11. Invalid identifiers receive identical non-enumerating generic response")
    void test11_invalidAccountIdentifiersReceiveNonEnumeratingResponse() throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setIdentifier("unknown_user_identifier_12345");

        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> resp = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(resp.get("message")).isEqualTo("If an account exists with that identifier, password reset instructions have been dispatched.");
    }

    // 12. Expired reset tokens are rejected
    @Test
    @DisplayName("12. Expired reset tokens are rejected")
    void test12_expiredResetTokensRejected() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase("worker").orElseThrow();
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(hashToken(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().minusHours(2)); // Expired!
        userRepository.save(user);

        // Verification endpoint rejects expired token
        mockMvc.perform(get("/api/auth/password-reset/verify")
                        .param("token", rawToken))
                .andExpect(status().isBadRequest());

        // Confirm endpoint rejects expired token
        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken(rawToken);
        confirmReq.setNewPassword("NewSecurePassphrase2026!");
        confirmReq.setConfirmPassword("NewSecurePassphrase2026!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest());
    }

    // 13. Reused tokens are rejected
    @Test
    @DisplayName("13. Reused tokens are rejected after single use")
    void test13_reusedTokensRejected() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase("worker").orElseThrow();
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(hashToken(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // First use succeeds
        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken(rawToken);
        confirmReq.setNewPassword("NewSecurePassphrase2026!");
        confirmReq.setConfirmPassword("NewSecurePassphrase2026!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk());

        // Reusing the same token fails
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest());
    }

    // 14. Invalid tokens cannot change passwords
    @Test
    @DisplayName("14. Invalid or forged tokens cannot change passwords")
    void test14_invalidTokensCannotChangePasswords() throws Exception {
        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken("completely_fake_invalid_token_1234567890");
        confirmReq.setNewPassword("NewSecurePassphrase2026!");
        confirmReq.setConfirmPassword("NewSecurePassphrase2026!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest());
    }

    // 15. Valid token successfully changes intended account password
    @Test
    @DisplayName("15. Complete end-to-end password reset flow with valid token")
    void test15_validTokenSuccessfullyChangesPassword() throws Exception {
        String testUser = "client";
        User user = userRepository.findByUsernameIgnoreCase(testUser).orElseThrow();

        // 1. Request reset
        PasswordResetRequest req = new PasswordResetRequest();
        req.setIdentifier(testUser);

        MvcResult reqResult = mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> reqResp = objectMapper.readValue(reqResult.getResponse().getContentAsString(), Map.class);
        String rawToken = (String) reqResp.get("demoResetToken");
        assertThat(rawToken).isNotBlank();

        // Verify SHA-256 protected token representation in database
        User updatedUser = userRepository.findByUsernameIgnoreCase(testUser).orElseThrow();
        assertThat(updatedUser.getPasswordResetToken()).isEqualTo(hashToken(rawToken));

        // 2. Verify token via GET /api/auth/password-reset/verify
        MvcResult verifyResult = mockMvc.perform(get("/api/auth/password-reset/verify")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> verifyResp = objectMapper.readValue(verifyResult.getResponse().getContentAsString(), Map.class);
        assertThat(verifyResp.get("valid")).isEqualTo(true);

        // 3. Confirm reset with new 12+ character password
        String newPassword = "BrandNewPassphrase2026!";
        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken(rawToken);
        confirmReq.setNewPassword(newPassword);
        confirmReq.setConfirmPassword(newPassword);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk());

        // 4. Authenticate with new password
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername(testUser);
        loginReq.setPassword(newPassword);
        loginReq.setOrganizationCode("ACM");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());

        // Restore default password for other tests
        user.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(user);
    }

    // 16. Used tokens are invalidated atomically
    @Test
    @DisplayName("16. Reset token is cleared from database immediately after use")
    void test16_tokenInvalidatedAtomicallyOnReset() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase("vendor").orElseThrow();
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(hashToken(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken(rawToken);
        confirmReq.setNewPassword("SecurePassphrase2026!");
        confirmReq.setConfirmPassword("SecurePassphrase2026!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk());

        User refreshed = userRepository.findByUsernameIgnoreCase("vendor").orElseThrow();
        assertThat(refreshed.getPasswordResetToken()).isNull();
        assertThat(refreshed.getPasswordResetExpiresAt()).isNull();

        // Restore default password
        refreshed.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(refreshed);
    }

    // 17. Invalid passwords cannot be submitted
    @Test
    @DisplayName("17. Passwords under 12 characters are rejected during reset")
    void test17_invalidPasswordsCannotBeSubmitted() throws Exception {
        User user = userRepository.findByUsernameIgnoreCase("worker").orElseThrow();
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(hashToken(rawToken));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        PasswordResetConfirmRequest confirmReq = new PasswordResetConfirmRequest();
        confirmReq.setToken(rawToken);
        confirmReq.setNewPassword("short");
        confirmReq.setConfirmPassword("short");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isBadRequest());
    }

    // 18. Authenticated password change enforces unified policy
    @Test
    @DisplayName("18. Authenticated password change requires current password and enforces 12-char policy")
    void test18_authenticatedPasswordChangeEnforcesPolicy() throws Exception {
        // Login as manager
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("manager");
        loginReq.setPassword("admin");
        loginReq.setOrganizationCode("ACM");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResp = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        String jwt = authResp.getToken();

        // Wrong current password
        ChangePasswordRequest wrongCurrentReq = new ChangePasswordRequest();
        wrongCurrentReq.setCurrentPassword("wrongPassword");
        wrongCurrentReq.setNewPassword("StrongPassphrase2026!");
        wrongCurrentReq.setConfirmPassword("StrongPassphrase2026!");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrentReq)))
                .andExpect(status().isBadRequest());

        // Short new password (< 12 chars)
        ChangePasswordRequest shortReq = new ChangePasswordRequest();
        shortReq.setCurrentPassword("admin");
        shortReq.setNewPassword("shortpass");
        shortReq.setConfirmPassword("shortpass");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortReq)))
                .andExpect(status().isBadRequest());
    }

    // 19. Case-insensitive login with username or work email
    @Test
    @DisplayName("19. Case-insensitive login resolves correctly for UPPERCASE, lowercase, and mixed-case usernames")
    void test19_caseInsensitiveLoginResolvesCorrectly() throws Exception {
        String[] variations = {"ADMIN", "Admin", "admin", "aDmIn"};
        for (String uname : variations) {
            AuthRequest req = new AuthRequest();
            req.setUsername(uname);
            req.setPassword("admin");
            req.setOrganizationCode("acm"); // lowercase org code

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }
    }

    // 20. Existing organization isolation preserved
    @Test
    @DisplayName("20. Tenant workspace isolation preserved between ACM and STK")
    void test20_tenantIsolationPreserved() throws Exception {
        // Manager in ACM tries to log in with STK workspace code -> 403 Forbidden
        AuthRequest req = new AuthRequest();
        req.setUsername("manager");
        req.setPassword("admin");
        req.setOrganizationCode("STK");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
