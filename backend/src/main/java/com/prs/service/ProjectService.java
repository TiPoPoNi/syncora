package com.prs.service;

import com.prs.dto.ProjectDto;
import com.prs.entity.Client;
import com.prs.entity.Organization;
import com.prs.entity.Project;
import com.prs.entity.Task;
import com.prs.entity.User;
import com.prs.repository.ClientRepository;
import com.prs.repository.OrganizationMembershipRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.ProjectRepository;
import com.prs.repository.TaskRepository;
import com.prs.repository.UserRepository;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TaskRepository taskRepository;

    public List<ProjectDto> getAllProjects() {
        Long tenantId = TenantContext.getTenantId();
        List<Project> projects;
        if (tenantId != null) {
            projects = projectRepository.findByOrganizationId(tenantId);
        } else {
            projects = projectRepository.findAll();
        }
        return projects.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public ProjectDto getProjectById(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        validateTenantAccess(project);
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto createProject(ProjectDto projectDto) {
        Organization org = TenantContext.getOrganization();
        if (org == null && TenantContext.getTenantId() != null) {
            org = organizationRepository.findById(TenantContext.getTenantId()).orElse(null);
        }
        if (org == null) {
            org = organizationRepository.findAll().stream().findFirst().orElse(null);
        }

        validateProjectData(projectDto, org != null ? org.getId() : null, null);

        Project project = new Project();
        BeanUtils.copyProperties(projectDto, project, "id", "createdAt", "updatedAt");
        project.setName(projectDto.getName().trim());
        if (projectDto.getDescription() != null) {
            project.setDescription(projectDto.getDescription().trim());
        }
        project.setOrganization(org);

        if (projectDto.getClientId() != null) {
            Client client = clientRepository.findById(projectDto.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client not found: " + projectDto.getClientId()));
            if (org != null && client.getOrganization() != null 
                    && !client.getOrganization().getId().equals(org.getId())) {
                throw new IllegalArgumentException("Cannot assign client belonging to another organization.");
            }
            project.setClient(client);
        } else {
            project.setClient(null);
        }

        if (projectDto.getManagerId() != null) {
            User manager = userRepository.findById(projectDto.getManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager not found: " + projectDto.getManagerId()));
            if (org != null && !membershipRepository.existsByUserIdAndOrganizationId(manager.getId(), org.getId())) {
                throw new IllegalArgumentException("Selected project manager is not a member of this workspace.");
            }
            project.setManager(manager);
        } else {
            project.setManager(null);
        }
        
        Project saved = projectRepository.save(project);
        log.info("Created project '{}' (ID: {}) in org '{}'", saved.getName(), saved.getId(), org != null ? org.getName() : "None");
        return mapToDto(saved);
    }

    @Transactional
    public ProjectDto updateProject(Long id, ProjectDto projectDto) {
        Project project = projectRepository.findById(id).orElseThrow();
        validateTenantAccess(project);

        Long orgId = project.getOrganization() != null ? project.getOrganization().getId() : TenantContext.getTenantId();
        validateProjectData(projectDto, orgId, id);

        BeanUtils.copyProperties(projectDto, project, "id", "createdAt", "updatedAt", "client", "manager", "organization");
        project.setName(projectDto.getName().trim());
        if (projectDto.getDescription() != null) {
            project.setDescription(projectDto.getDescription().trim());
        } else {
            project.setDescription(null);
        }
        
        if (projectDto.getClientId() != null) {
            Client client = clientRepository.findById(projectDto.getClientId())
                    .orElseThrow(() -> new IllegalArgumentException("Client not found: " + projectDto.getClientId()));
            if (project.getOrganization() != null && client.getOrganization() != null
                    && !client.getOrganization().getId().equals(project.getOrganization().getId())) {
                throw new IllegalArgumentException("Cannot assign client belonging to another organization.");
            }
            project.setClient(client);
        } else {
            project.setClient(null);
        }

        if (projectDto.getManagerId() != null) {
            User manager = userRepository.findById(projectDto.getManagerId())
                    .orElseThrow(() -> new IllegalArgumentException("Project manager not found: " + projectDto.getManagerId()));
            if (project.getOrganization() != null && !membershipRepository.existsByUserIdAndOrganizationId(manager.getId(), project.getOrganization().getId())) {
                throw new IllegalArgumentException("Selected project manager is not a member of this workspace.");
            }
            project.setManager(manager);
        } else {
            project.setManager(null);
        }
        
        Project saved = projectRepository.save(project);
        log.info("Updated project '{}' (ID: {})", saved.getName(), saved.getId());
        return mapToDto(saved);
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = projectRepository.findById(id).orElseThrow();
        validateTenantAccess(project);

        // Safe cleanup: delete associated tasks to prevent foreign key constraint violations
        List<Task> tasks = taskRepository.findByProjectId(id);
        if (!tasks.isEmpty()) {
            log.info("Deleting {} associated task(s) for project ID {}", tasks.size(), id);
            taskRepository.deleteAll(tasks);
        }

        projectRepository.delete(project);
        log.info("Deleted project '{}' (ID: {})", project.getName(), id);
    }

    private void validateProjectData(ProjectDto dto, Long orgId, Long excludeProjectId) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Project name is required.");
        }
        String trimmedName = dto.getName().trim();
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("Project name cannot exceed 100 characters.");
        }
        if (dto.getDescription() != null && dto.getDescription().length() > 2000) {
            throw new IllegalArgumentException("Description cannot exceed 2000 characters.");
        }

        // Duplicate name validation within same organization
        if (orgId != null) {
            Optional<Project> dup = projectRepository.findByOrganizationIdAndNameIgnoreCase(orgId, trimmedName);
            if (dup.isPresent() && (excludeProjectId == null || !dup.get().getId().equals(excludeProjectId))) {
                throw new IllegalArgumentException("A project named '" + trimmedName + "' already exists in this workspace.");
            }
        }

        // Date validation
        if (dto.getStartDate() != null && dto.getDeadline() != null) {
            if (dto.getDeadline().isBefore(dto.getStartDate())) {
                throw new IllegalArgumentException("Contract deadline cannot be earlier than start date.");
            }
        }

        // Progress validation
        if (dto.getProgress() == null) {
            dto.setProgress(0);
        }
        if (dto.getProgress() < 0 || dto.getProgress() > 100) {
            throw new IllegalArgumentException("Progress must be between 0% and 100%.");
        }

        // Status & progress consistency
        if (dto.getStatus() == com.prs.entity.ProjectStatus.COMPLETED) {
            dto.setProgress(100);
        } else if (dto.getProgress() == 100 && dto.getStatus() == com.prs.entity.ProjectStatus.PLANNED) {
            dto.setStatus(com.prs.entity.ProjectStatus.COMPLETED);
        }
    }

    private void validateTenantAccess(Project project) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && project.getOrganization() != null && !project.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }
    }

    private ProjectDto mapToDto(Project project) {
        ProjectDto dto = new ProjectDto();
        BeanUtils.copyProperties(project, dto);
        if (project.getClient() != null) {
            dto.setClientId(project.getClient().getId());
            dto.setClientName(project.getClient().getName());
        }
        if (project.getManager() != null) {
            dto.setManagerId(project.getManager().getId());
            dto.setManagerName(project.getManager().getFullName() != null && !project.getManager().getFullName().isBlank()
                    ? project.getManager().getFullName()
                    : project.getManager().getUsername());
        }
        return dto;
    }
}
