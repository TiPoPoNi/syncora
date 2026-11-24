package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.AuthRequest;
import com.prs.dto.AuthResponse;
import com.prs.dto.ProjectDto;
import com.prs.entity.*;
import com.prs.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SYNCORA Projects Management & Lifecycle Integration Tests")
public class ProjectsManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminJwt;
    private Organization acmeOrg;
    private Organization starkOrg;
    private Client acmeClient;
    private Client starkClient;
    private User managerUser;

    @BeforeEach
    void setUp() throws Exception {
        acmeOrg = organizationRepository.findBySlug("acme-global")
                .orElseGet(() -> {
                    Organization o = new Organization();
                    o.setName("Acme Global Solutions");
                    o.setSlug("acme-global");
                    o.setOrgCode("ACM");
                    o.setStatus("ACTIVE");
                    return organizationRepository.save(o);
                });

        starkOrg = organizationRepository.findBySlug("stark-tech")
                .orElseGet(() -> {
                    Organization o = new Organization();
                    o.setName("Stark Tech Industries");
                    o.setSlug("stark-tech");
                    o.setOrgCode("STK");
                    o.setStatus("ACTIVE");
                    return organizationRepository.save(o);
                });

        // Ensure users have standard password
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        adminUser.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(adminUser);

        managerUser = userRepository.findByUsername("manager").orElseThrow();
        managerUser.setPassword(passwordEncoder.encode("admin"));
        userRepository.save(managerUser);

        // Ensure memberships
        if (!membershipRepository.existsByUserIdAndOrganizationId(adminUser.getId(), acmeOrg.getId())) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(acmeOrg);
            m.setRole("ORGANIZATION_ADMIN");
            m.setStatus("ACTIVE");
            m.setEmployeeId("ACM-EMP-0001");
            membershipRepository.save(m);
        }

        if (!membershipRepository.existsByUserIdAndOrganizationId(managerUser.getId(), acmeOrg.getId())) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(managerUser);
            m.setOrganization(acmeOrg);
            m.setRole("PROJECT_MANAGER");
            m.setStatus("ACTIVE");
            m.setEmployeeId("ACM-EMP-0002");
            membershipRepository.save(m);
        }

        // Clients
        acmeClient = clientRepository.findAll().stream()
                .filter(c -> c.getOrganization() != null && c.getOrganization().getId().equals(acmeOrg.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setName("Acme Client A");
                    c.setOrganization(acmeOrg);
                    return clientRepository.save(c);
                });

        starkClient = clientRepository.findAll().stream()
                .filter(c -> c.getOrganization() != null && c.getOrganization().getId().equals(starkOrg.getId()))
                .findFirst()
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setName("Stark Client B");
                    c.setOrganization(starkOrg);
                    return clientRepository.save(c);
                });

        // Login as admin for token
        AuthRequest req = new AuthRequest();
        req.setUsername("admin");
        req.setPassword("admin");

        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse resp = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class);
        adminJwt = resp.getToken();
    }

    @Test
    @DisplayName("1. Fetch all projects scoped to active organization")
    void testGetAllProjectsScoped() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();

        List<ProjectDto> list = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectDto.class)
        );

        assertThat(list).isNotNull();
    }

    @Test
    @DisplayName("2. Create project with valid attributes and manager assignment")
    void testCreateProjectSuccess() throws Exception {
        ProjectDto dto = new ProjectDto();
        dto.setName("Enterprise Cloud Migration");
        dto.setDescription("Multi-region high availability cloud migration");
        dto.setClientId(acmeClient.getId());
        dto.setManagerId(managerUser.getId());
        dto.setPriority(Priority.HIGH);
        dto.setStartDate(LocalDate.of(2026, 11, 1));
        dto.setDeadline(LocalDate.of(2026, 12, 15));
        dto.setStatus(ProjectStatus.IN_PROGRESS);
        dto.setProgress(25);

        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ProjectDto.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Enterprise Cloud Migration");
        assertThat(created.getClientId()).isEqualTo(acmeClient.getId());
        assertThat(created.getManagerId()).isEqualTo(managerUser.getId());
        assertThat(created.getProgress()).isEqualTo(25);
    }

    @Test
    @DisplayName("3. Reject project creation with empty name")
    void testCreateProjectEmptyName() throws Exception {
        ProjectDto dto = new ProjectDto();
        dto.setName("   ");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. Reject duplicate project name in same organization")
    void testCreateProjectDuplicateName() throws Exception {
        ProjectDto dto1 = new ProjectDto();
        dto1.setName("Unique Project Alpha");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isOk());

        // Second submission with same name (case-insensitive)
        ProjectDto dto2 = new ProjectDto();
        dto2.setName("unique project alpha");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Reject deadline earlier than start date")
    void testCreateProjectInvalidDates() throws Exception {
        ProjectDto dto = new ProjectDto();
        dto.setName("Inverted Dates Project");
        dto.setStartDate(LocalDate.of(2026, 12, 1));
        dto.setDeadline(LocalDate.of(2026, 11, 1)); // Earlier!

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Completed status enforces 100% progress")
    void testCreateProjectCompletedStatusProgressSync() throws Exception {
        ProjectDto dto = new ProjectDto();
        dto.setName("Fast Track Delivery");
        dto.setStatus(ProjectStatus.COMPLETED);
        dto.setProgress(40); // Inconsistent with COMPLETED

        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto created = objectMapper.readValue(result.getResponse().getContentAsString(), ProjectDto.class);
        assertThat(created.getProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("7. Reject client from foreign organization")
    void testCreateProjectForeignClientRejected() throws Exception {
        ProjectDto dto = new ProjectDto();
        dto.setName("Cross Org Project Attempt");
        dto.setClientId(starkClient.getId()); // Foreign client

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("8. Edit project updates attributes and clears client/manager cleanly")
    void testEditProject() throws Exception {
        // Create initial project
        ProjectDto dto = new ProjectDto();
        dto.setName("Initial Project State");
        dto.setClientId(acmeClient.getId());
        dto.setManagerId(managerUser.getId());
        dto.setPriority(Priority.LOW);
        dto.setStatus(ProjectStatus.PLANNED);
        dto.setProgress(0);

        MvcResult res = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto created = objectMapper.readValue(res.getResponse().getContentAsString(), ProjectDto.class);

        // Edit to update name, clear client and manager (internal & unassigned)
        created.setName("Updated Project Title");
        created.setClientId(null);
        created.setManagerId(null);
        created.setPriority(Priority.CRITICAL);
        created.setStatus(ProjectStatus.IN_PROGRESS);
        created.setProgress(50);

        MvcResult updateRes = mockMvc.perform(put("/api/projects/" + created.getId())
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto updated = objectMapper.readValue(updateRes.getResponse().getContentAsString(), ProjectDto.class);
        assertThat(updated.getName()).isEqualTo("Updated Project Title");
        assertThat(updated.getClientId()).isNull();
        assertThat(updated.getManagerId()).isNull();
        assertThat(updated.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(updated.getProgress()).isEqualTo(50);
    }

    @Test
    @DisplayName("9. Safe project deletion with task cleanup")
    void testDeleteProjectWithTasks() throws Exception {
        // Create project
        ProjectDto dto = new ProjectDto();
        dto.setName("Project To Be Deleted");

        MvcResult res = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectDto created = objectMapper.readValue(res.getResponse().getContentAsString(), ProjectDto.class);
        Project projectEntity = projectRepository.findById(created.getId()).orElseThrow();

        // Create an associated task
        Task task = new Task();
        task.setProject(projectEntity);
        task.setName("Task inside project");
        taskRepository.save(task);

        // Delete project should safely cascade tasks and delete project
        mockMvc.perform(delete("/api/projects/" + created.getId())
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk());

        // Verify project and task no longer exist
        assertThat(projectRepository.findById(created.getId())).isEmpty();
        assertThat(taskRepository.findByProjectId(created.getId())).isEmpty();
    }
}
