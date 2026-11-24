package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.CompleteTaskRequest;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 9: Historical Analytics and Delay Risk Integration Tests")
public class Phase9HistoricalAnalyticsIntegrationTest {

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
    @DisplayName("1. Record Task Completion: Calculates actual duration, variance, and archives outcome")
    void testRecordTaskCompletion() throws Exception {
        // Complete Task 1 (Requirements)
        CompleteTaskRequest req = CompleteTaskRequest.builder()
                .actualStartDate(LocalDate.of(2026, 10, 19))
                .actualFinishDate(LocalDate.of(2026, 10, 20))
                .primaryDelayReason(null)
                .build();

        mockMvc.perform(post("/api/tasks/1/complete")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName", is("Requirements")))
                .andExpect(jsonPath("$.actualDuration", notNullValue()))
                .andExpect(jsonPath("$.isDelayed", notNullValue()))
                .andExpect(jsonPath("$.recordedAt", notNullValue()));
    }

    @Test
    @DisplayName("2. Historical Analytics Aggregation: Returns on-time rate, average variance, and delay reasons")
    void testHistoricalOutcomesAggregation() throws Exception {
        mockMvc.perform(get("/api/analytics/historical-outcomes")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedTasks", notNullValue()))
                .andExpect(jsonPath("$.onTimeRatePercent", notNullValue()))
                .andExpect(jsonPath("$.averageVarianceDays", notNullValue()))
                .andExpect(jsonPath("$.delayReasonsDistribution", notNullValue()))
                .andExpect(jsonPath("$.workerReliability", notNullValue()));
    }

    @Test
    @DisplayName("3. Delay Risk Assessment: Strict governance returns deterministic status and unmanipulated ML state")
    void testProjectDelayRiskAssessment_Governed() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/delay-risk")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].taskId", notNullValue()))
                .andExpect(jsonPath("$[0].deterministicScheduleStatus", notNullValue()))
                // Governance Assertion: No fabricated probabilities when training threshold is unmet
                .andExpect(jsonPath("$[0].mlPredictionStatus", is("NOT_READY")))
                .andExpect(jsonPath("$[0].delayRiskScore").doesNotExist())
                .andExpect(jsonPath("$[0].riskLevel", is("UNASSESSED")))
                .andExpect(jsonPath("$[0].limitations", containsString("completed task")));
    }

    @Test
    @DisplayName("4. Security: Unauthenticated request to complete task is rejected")
    void testCompleteTask_UnauthenticatedRejected() throws Exception {
        CompleteTaskRequest req = CompleteTaskRequest.builder()
                .actualFinishDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/tasks/2/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Input Validation: Missing actual finish date returns 400 Bad Request")
    void testCompleteTask_MissingFinishDateRejected() throws Exception {
        CompleteTaskRequest req = CompleteTaskRequest.builder()
                .actualFinishDate(null)
                .build();

        mockMvc.perform(post("/api/tasks/2/complete")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
