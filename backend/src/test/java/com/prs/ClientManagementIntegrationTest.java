package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.ClientDto;
import com.prs.dto.ProjectDto;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class ClientManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        if (adminToken == null) {
            AuthRequest req = new AuthRequest();
            req.setUsername("admin");
            req.setPassword("admin");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            adminToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        }
    }

    @Test
    @Order(1)
    void testCreateClient_ValidationFailure_BlankName() throws Exception {
        ClientDto client = new ClientDto();
        client.setName("   ");
        client.setCompany("Invalid Corp");

        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Client contact name is required")));
    }

    @Test
    @Order(2)
    void testCreateClient_ValidationFailure_InvalidEmail() throws Exception {
        ClientDto client = new ClientDto();
        client.setName("John Doe");
        client.setEmail("invalid-email-format");

        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("valid email address format")));
    }

    @Test
    @Order(3)
    void testCreateClient_ValidationFailure_InvalidStatus() throws Exception {
        ClientDto client = new ClientDto();
        client.setName("Jane Doe");
        client.setStatus("PENDING_UNKNOWN");

        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("status must be either ACTIVE or INACTIVE")));
    }

    @Test
    @Order(4)
    void testCreateClient_Success() throws Exception {
        ClientDto client = new ClientDto();
        client.setName("Safe Delete Candidate");
        client.setCompany("Deletable Solutions");
        client.setEmail("candidate@deletable.com");
        client.setPhone("+1-555-0199");
        client.setAddress("123 Test Street");
        client.setStatus("ACTIVE");

        MvcResult result = mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Safe Delete Candidate"))
                .andExpect(jsonPath("$.company").value("Deletable Solutions"))
                .andExpect(jsonPath("$.projectCount").value(0))
                .andReturn();

        Long createdId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Should be able to delete immediately since it has 0 projects
        mockMvc.perform(delete("/api/clients/" + createdId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void testClientWithProjects_BlockedFromDeletion_CanArchive() throws Exception {
        // 1. Create client
        ClientDto client = new ClientDto();
        client.setName("Enterprise Client With Projects");
        client.setCompany("Enterprise Systems Ltd");
        client.setEmail("contact@enterprisesystems.com");
        client.setStatus("ACTIVE");

        MvcResult clientResult = mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isOk())
                .andReturn();

        Long clientId = objectMapper.readTree(clientResult.getResponse().getContentAsString()).get("id").asLong();

        // 2. Create project linked to this client
        ProjectDto project = new ProjectDto();
        project.setName("Client Linked Mission");
        project.setDescription("Important mission for enterprise client");
        project.setClientId(clientId);

        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isOk());

        // 3. Attempt destructive deletion -> Must fail with conflict/bad request
        mockMvc.perform(delete("/api/clients/" + clientId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("associated with this client")));

        // 4. Inspect details endpoint
        mockMvc.perform(get("/api/clients/" + clientId + "/details")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId))
                .andExpect(jsonPath("$.totalProjects").value(1))
                .andExpect(jsonPath("$.projects", hasSize(1)))
                .andExpect(jsonPath("$.projects[0].name").value("Client Linked Mission"));

        // 5. Test archive client
        mockMvc.perform(put("/api/clients/" + clientId + "/archive")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}
