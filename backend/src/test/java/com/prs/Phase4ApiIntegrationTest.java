package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.StartOptimizationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase4ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;

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
    void testStartOptimizationRunAndFetchAlternatives() throws Exception {
        StartOptimizationRequest req = new StartOptimizationRequest();
        req.setSelectedObjective("BALANCED");

        MvcResult runResult = mockMvc.perform(post("/api/projects/1/optimization/runs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidatesEvaluated").isNumber())
                .andExpect(jsonPath("$.feasibleCandidatesCount").isNumber())
                .andReturn();

        long runId = objectMapper.readTree(runResult.getResponse().getContentAsString()).get("id").asLong();

        // Fetch alternatives for this run
        mockMvc.perform(get("/api/optimization/runs/" + runId + "/alternatives")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].alternativeRank").value(1))
                .andExpect(jsonPath("$[0].objectiveScore").isNumber())
                .andExpect(jsonPath("$[0].deadlineStatus").exists());
    }

    @Test
    void testRecommendationLifecycle_ApproveAndApply() throws Exception {
        // 1. Run optimization
        mockMvc.perform(post("/api/projects/1/optimization/runs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartOptimizationRequest())))
                .andExpect(status().isOk());

        // 2. Fetch recommendations
        MvcResult recsResult = mockMvc.perform(get("/api/projects/1/recommendations")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        long recId = objectMapper.readTree(recsResult.getResponse().getContentAsString()).get(0).get("id").asLong();

        // 3. Approve recommendation
        mockMvc.perform(post("/api/recommendations/" + recId + "/approve")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. Apply approved recommendation
        mockMvc.perform(post("/api/recommendations/" + recId + "/apply")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        // 5. Verify audit log entry
        mockMvc.perform(get("/api/projects/1/optimization/audit-logs")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].action").exists());
    }

    @Test
    void testTaskResourceRecommendations() throws Exception {
        // Task 3 is Backend Development (requires Backend, Java, Spring)
        mockMvc.perform(get("/api/tasks/3/resource-recommendations")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(3))
                .andExpect(jsonPath("$.candidateWorkers").isArray())
                .andExpect(jsonPath("$.candidateWorkers[0].suitabilityScore").isNumber())
                .andExpect(jsonPath("$.candidateWorkers[0].recommendationReason").exists());
    }

    @Test
    void testUnauthorizedAccess_isDenied() throws Exception {
        mockMvc.perform(post("/api/projects/1/optimization/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testStaleRecommendation_cannotBeAppliedTwice() throws Exception {
        // Run optimization
        mockMvc.perform(post("/api/projects/1/optimization/runs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartOptimizationRequest())))
                .andExpect(status().isOk());

        // Fetch recommendations
        MvcResult recsResult = mockMvc.perform(get("/api/projects/1/recommendations")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        long recId = objectMapper.readTree(recsResult.getResponse().getContentAsString()).get(0).get("id").asLong();

        // Approve recommendation
        mockMvc.perform(post("/api/recommendations/" + recId + "/approve")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // Apply recommendation once -> SUCCESS
        mockMvc.perform(post("/api/recommendations/" + recId + "/apply")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());

        // Attempting to apply again must fail (cannot apply already APPLIED / stale recommendation)
        mockMvc.perform(post("/api/recommendations/" + recId + "/apply")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isBadRequest());
    }
}
