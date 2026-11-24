package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AiChatRequestDto;
import com.prs.dto.AuthRequest;
import com.prs.dto.ScenarioSimulationRequestDto;
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
public class Phase5ApiIntegrationTest {

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
    @DisplayName("Verify AI Assistant Status endpoint returns provider info")
    void testAssistantStatus() throws Exception {
        mockMvc.perform(get("/api/projects/" + projectId + "/assistant/status")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", anyOf(is("ONLINE"), is("OFFLINE_FALLBACK"))))
                .andExpect(jsonPath("$.provider", notNullValue()));
    }

    @Test
    @DisplayName("Verify Grounded AI Chat message endpoint returns explanation and source citations")
    void testChatMessage() throws Exception {
        AiChatRequestDto request = new AiChatRequestDto();
        request.setMessage("What is the current critical path and are there any delays?");

        mockMvc.perform(post("/api/projects/" + projectId + "/assistant/messages")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("assistant")))
                .andExpect(jsonPath("$.answer", notNullValue()))
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.citations", notNullValue()));
    }

    @Test
    @DisplayName("Verify Chat History is persisted and retrievable")
    void testChatHistory() throws Exception {
        AiChatRequestDto request = new AiChatRequestDto();
        request.setMessage("Which tasks have the least scheduling slack?");

        mockMvc.perform(post("/api/projects/" + projectId + "/assistant/messages")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/" + projectId + "/assistant/history")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].content", notNullValue()));
    }

    @Test
    @DisplayName("Verify What-If Scenario simulation returns calculated variances without mutating active schedule")
    void testHypotheticalScenarioSimulation() throws Exception {
        ScenarioSimulationRequestDto request = new ScenarioSimulationRequestDto();
        request.setScenarioType("TASK_DELAY");
        request.setAdditionalDelayDays(5);
        request.setDelayReason("Vendor component shipment delay");

        mockMvc.perform(post("/api/projects/" + projectId + "/assistant/scenarios")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioType", is("TASK_DELAY")))
                .andExpect(jsonPath("$.hypothetical", is(true)))
                .andExpect(jsonPath("$.workingDaysDifference", notNullValue()))
                .andExpect(jsonPath("$.aiExplanation", notNullValue()));
    }

    @Test
    @DisplayName("Verify unauthorized user without token is rejected")
    void testUnauthorizedAccessRejection() throws Exception {
        AiChatRequestDto request = new AiChatRequestDto();
        request.setMessage("Show confidential critical path");

        mockMvc.perform(post("/api/projects/" + projectId + "/assistant/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
