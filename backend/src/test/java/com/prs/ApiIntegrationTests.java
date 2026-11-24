package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.ClientDto;
import com.prs.dto.ProjectDto;
import com.prs.dto.TaskDto;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String jwtToken;

    @Test
    @Order(1)
    void testLoginSuccess() throws Exception {
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        jwtToken = objectMapper.readTree(responseStr).get("token").asText();
    }

    @Test
    @Order(2)
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void testClientCreation() throws Exception {
        ClientDto client = new ClientDto();
        client.setName("Test Client");
        client.setCompany("Acme Corp");
        client.setEmail("test@acme.com");

        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Client"));
    }

    @Test
    @Order(4)
    void testProjectCreation() throws Exception {
        ProjectDto project = new ProjectDto();
        project.setName("New Project");
        project.setDescription("Test Description");

        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("New Project"));
    }

    @Test
    @Order(5)
    void testTaskCreation() throws Exception {
        TaskDto task = new TaskDto();
        task.setName("Initial Task");
        task.setDescription("Test task description");
        task.setEstimatedDuration(5);

        mockMvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Initial Task"));
    }
}
