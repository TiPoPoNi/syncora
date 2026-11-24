package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.StartOptimizationRequest;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase6ProductionReadinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;
    private final Long projectId = 1L;

    @BeforeEach
    void setUp() throws Exception {
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(responseStr).get("token").asText();
    }

    @Test
    @DisplayName("Security Audit: Unauthenticated request to protected endpoints is rejected with 403")
    void testSecurity_UnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/1/schedule/recalculate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security Audit: Invalid login returns clean 401 without stack trace")
    void testSecurity_InvalidLoginReturnsClean401() throws Exception {
        AuthRequest badLogin = new AuthRequest();
        badLogin.setUsername("admin");
        badLogin.setPassword("wrong_password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Authentication Failed")));
    }

    @Test
    @DisplayName("Error Handling: Bad input returns structured 400 Bad Request without stack trace")
    void testErrorHandling_InvalidInputReturnsSanitizedError() throws Exception {
        // Querying non-existent recommendation
        mockMvc.perform(post("/api/recommendations/999999/approve")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("not found")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("Concurrency & Stale Changes: Non-approved recommendation cannot be applied")
    void testConcurrency_UnapprovedRecommendationCannotBeApplied() throws Exception {
        // 1. Run optimization
        mockMvc.perform(post("/api/projects/" + projectId + "/optimization/runs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartOptimizationRequest())))
                .andExpect(status().isOk());

        // 2. Fetch proposed recommendations
        MvcResult recs = mockMvc.perform(get("/api/projects/" + projectId + "/recommendations")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        long recId = objectMapper.readTree(recs.getResponse().getContentAsString()).get(0).get("id").asLong();

        // 3. Attempting to apply without approval first must be rejected with 400 Bad Request
        mockMvc.perform(post("/api/recommendations/" + recId + "/apply")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must be APPROVED before applying")));
    }

    @Test
    @DisplayName("End-to-End Workflow Verification: Schedule recalculation updates critical path deterministically")
    void testWorkflow_ScheduleRecalculationAndCpm() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/schedule/recalculate")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(projectId.intValue())))
                .andExpect(jsonPath("$.tasks", hasSize(greaterThan(0))));

        mockMvc.perform(get("/api/projects/" + projectId + "/critical-path")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalTaskIds", notNullValue()))
                .andExpect(jsonPath("$.tasks", hasSize(greaterThan(0))));
    }
}
