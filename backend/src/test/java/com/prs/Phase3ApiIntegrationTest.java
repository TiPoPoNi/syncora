package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.ReportDelayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase3ApiIntegrationTest {

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
    void testCriticalPathEndpoint() throws Exception {
        mockMvc.perform(get("/api/projects/1/critical-path")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.minimumDurationWorkingDays").isNumber())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.criticalTaskIds").isArray());
    }

    @Test
    void testScheduleAnalysisEndpoint() throws Exception {
        mockMvc.perform(get("/api/projects/1/schedule-analysis")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.cpmAnalysisStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.deadlineStatus").exists())
                .andExpect(jsonPath("$.taskAnalysis").isArray());
    }

    @Test
    void testGanttEndpoint() throws Exception {
        mockMvc.perform(get("/api/projects/1/gantt")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.currentDate").exists());
    }

    @Test
    void testBaselineCreationAndRetrieval() throws Exception {
        mockMvc.perform(post("/api/projects/1/baseline")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.singletonMap("versionName", "Approved Plan v1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionName").value("Approved Plan v1"))
                .andExpect(jsonPath("$.taskBaselines").isArray());

        mockMvc.perform(get("/api/projects/1/baseline")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionName").value("Approved Plan v1"));
    }

    @Test
    void testDelayReportingAndImpact() throws Exception {
        ReportDelayRequest req = new ReportDelayRequest();
        req.setDelayDurationDays(3);
        req.setDelayReason("Vendor component supply delay");
        req.setNotes("Critical vendor delayed hardware shipment");

        // Report delay on task 1
        mockMvc.perform(post("/api/tasks/1/delays")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delayDurationDays").value(3))
                .andExpect(jsonPath("$.delayReason").value("Vendor component supply delay"));

        // Check delay impact
        mockMvc.perform(get("/api/projects/1/delay-impact")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.reportedDelays").isArray())
                .andExpect(jsonPath("$.explanation").isString());
    }
}
