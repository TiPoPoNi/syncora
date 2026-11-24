package com.prs.service;

import com.prs.dto.ClientDetailDto;
import com.prs.dto.ClientDetailDto.ClientDeliverableSummaryDto;
import com.prs.dto.ClientDetailDto.ClientProjectSummaryDto;
import com.prs.dto.ClientDto;
import com.prs.entity.Client;
import com.prs.entity.Deliverable;
import com.prs.entity.Organization;
import com.prs.entity.Project;
import com.prs.entity.ProjectStatus;
import com.prs.repository.ClientRepository;
import com.prs.repository.DeliverableRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.ProjectRepository;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final DeliverableRepository deliverableRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");

    public List<ClientDto> getAllClients() {
        Long tenantId = TenantContext.getTenantId();
        List<Client> clients;
        if (tenantId != null) {
            clients = clientRepository.findByOrganizationId(tenantId);
        } else {
            clients = clientRepository.findAll();
        }
        return clients.stream().map(c -> mapToDtoWithCounts(c, tenantId)).collect(Collectors.toList());
    }

    public ClientDto getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with id: " + id));
        validateTenantAccess(client);
        return mapToDtoWithCounts(client, TenantContext.getTenantId());
    }

    @Transactional(readOnly = true)
    public ClientDetailDto getClientDetails(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with id: " + id));
        validateTenantAccess(client);

        Long tenantId = TenantContext.getTenantId();
        List<Project> projects = (tenantId != null)
                ? projectRepository.findByOrganizationIdAndClientId(tenantId, id)
                : projectRepository.findByClientId(id);

        long activeCount = projects.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.CANCELLED)
                .count();

        List<ClientProjectSummaryDto> projectSummaries = projects.stream().map(p -> {
            String managerName = null;
            Long managerId = null;
            if (p.getManager() != null) {
                managerId = p.getManager().getId();
                managerName = (p.getManager().getFullName() != null && !p.getManager().getFullName().isBlank())
                        ? p.getManager().getFullName()
                        : p.getManager().getUsername();
            }

            return ClientProjectSummaryDto.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .description(p.getDescription())
                    .status(p.getStatus() != null ? p.getStatus().name() : null)
                    .priority(p.getPriority() != null ? p.getPriority().name() : null)
                    .progress(p.getProgress())
                    .startDate(p.getStartDate())
                    .deadline(p.getDeadline())
                    .expectedCompletionDate(p.getExpectedCompletionDate())
                    .managerId(managerId)
                    .managerName(managerName)
                    .build();
        }).collect(Collectors.toList());

        List<ClientDeliverableSummaryDto> deliverableSummaries = new ArrayList<>();
        for (Project project : projects) {
            try {
                List<Deliverable> deliverables = deliverableRepository.findByProjectIdAndIsClientVisibleTrueAndIsConfidentialFalse(project.getId());
                for (Deliverable d : deliverables) {
                    deliverableSummaries.add(ClientDeliverableSummaryDto.builder()
                            .id(d.getId())
                            .projectId(project.getId())
                            .projectName(project.getName())
                            .name(d.getName())
                            .description(d.getDescription())
                            .deliverableType(d.getDeliverableType() != null ? d.getDeliverableType().name() : null)
                            .status(d.getStatus() != null ? d.getStatus().name() : null)
                            .requiredSubmissionDate(d.getRequiredSubmissionDate())
                            .actualSubmissionDate(d.getActualSubmissionDate())
                            .currentVersionNumber(d.getCurrentVersionNumber())
                            .build());
                }
            } catch (Exception ex) {
                log.warn("Could not load deliverables for project {}: {}", project.getId(), ex.getMessage());
            }
        }

        return ClientDetailDto.builder()
                .id(client.getId())
                .name(client.getName())
                .company(client.getCompany())
                .email(client.getEmail())
                .phone(client.getPhone())
                .address(client.getAddress())
                .status(client.getStatus() != null ? client.getStatus() : "ACTIVE")
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .totalProjects((long) projects.size())
                .activeProjects(activeCount)
                .projects(projectSummaries)
                .deliverables(deliverableSummaries)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClientProjectSummaryDto> getClientProjects(Long id) {
        ClientDetailDto details = getClientDetails(id);
        return details.getProjects();
    }

    @Transactional
    public ClientDto createClient(ClientDto clientDto) {
        validateClientPayload(clientDto);

        Client client = new Client();
        client.setName(clientDto.getName().trim());
        client.setCompany(clientDto.getCompany() != null ? clientDto.getCompany().trim() : null);
        client.setEmail(clientDto.getEmail() != null && !clientDto.getEmail().trim().isEmpty() ? clientDto.getEmail().trim() : null);
        client.setPhone(clientDto.getPhone() != null && !clientDto.getPhone().trim().isEmpty() ? clientDto.getPhone().trim() : null);
        client.setAddress(clientDto.getAddress() != null && !clientDto.getAddress().trim().isEmpty() ? clientDto.getAddress().trim() : null);
        client.setStatus(clientDto.getStatus() != null && !clientDto.getStatus().isBlank() ? clientDto.getStatus().trim().toUpperCase() : "ACTIVE");

        Organization org = TenantContext.getOrganization();
        if (org == null && TenantContext.getTenantId() != null) {
            org = organizationRepository.findById(TenantContext.getTenantId()).orElse(null);
        }
        if (org == null) {
            org = organizationRepository.findAll().stream().findFirst().orElse(null);
        }
        client.setOrganization(org);

        Client saved = clientRepository.save(client);
        ClientDto result = mapToDto(saved);
        result.setProjectCount(0L);
        result.setActiveProjectCount(0L);
        return result;
    }

    @Transactional
    public ClientDto updateClient(Long id, ClientDto clientDto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with id: " + id));
        validateTenantAccess(client);
        validateClientPayload(clientDto);

        client.setName(clientDto.getName().trim());
        client.setCompany(clientDto.getCompany() != null ? clientDto.getCompany().trim() : null);
        client.setEmail(clientDto.getEmail() != null && !clientDto.getEmail().trim().isEmpty() ? clientDto.getEmail().trim() : null);
        client.setPhone(clientDto.getPhone() != null && !clientDto.getPhone().trim().isEmpty() ? clientDto.getPhone().trim() : null);
        client.setAddress(clientDto.getAddress() != null && !clientDto.getAddress().trim().isEmpty() ? clientDto.getAddress().trim() : null);
        if (clientDto.getStatus() != null && !clientDto.getStatus().isBlank()) {
            client.setStatus(clientDto.getStatus().trim().toUpperCase());
        }

        Client saved = clientRepository.save(client);
        return mapToDtoWithCounts(saved, TenantContext.getTenantId());
    }

    @Transactional
    public ClientDto archiveClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with id: " + id));
        validateTenantAccess(client);

        client.setStatus("INACTIVE");
        Client saved = clientRepository.save(client);
        return mapToDtoWithCounts(saved, TenantContext.getTenantId());
    }

    @Transactional
    public void deleteClient(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found with id: " + id));
        validateTenantAccess(client);

        Long tenantId = TenantContext.getTenantId();
        long projectCount = (tenantId != null)
                ? projectRepository.countByOrganizationIdAndClientId(tenantId, id)
                : projectRepository.countByClientId(id);

        if (projectCount > 0) {
            throw new IllegalStateException("Cannot delete client '" + client.getName() + "'. There are "
                    + projectCount + " project(s) associated with this client. Please archive this client instead to preserve project history.");
        }

        clientRepository.delete(client);
    }

    private void validateClientPayload(ClientDto dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Client contact name is required.");
        }
        if (dto.getName().trim().length() > 100) {
            throw new IllegalArgumentException("Client contact name cannot exceed 100 characters.");
        }
        if (dto.getCompany() != null && dto.getCompany().trim().length() > 100) {
            throw new IllegalArgumentException("Company name cannot exceed 100 characters.");
        }
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
            String email = dto.getEmail().trim();
            if (email.length() > 100) {
                throw new IllegalArgumentException("Email address cannot exceed 100 characters.");
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("Please provide a valid email address format (e.g., user@example.com).");
            }
        }
        if (dto.getPhone() != null && dto.getPhone().trim().length() > 30) {
            throw new IllegalArgumentException("Phone number cannot exceed 30 characters.");
        }
        if (dto.getAddress() != null && dto.getAddress().trim().length() > 255) {
            throw new IllegalArgumentException("Address cannot exceed 255 characters.");
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            String s = dto.getStatus().trim().toUpperCase();
            if (!s.equals("ACTIVE") && !s.equals("INACTIVE")) {
                throw new IllegalArgumentException("Client status must be either ACTIVE or INACTIVE.");
            }
        }
    }

    private void validateTenantAccess(Client client) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && client.getOrganization() != null && !client.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Client does not belong to active organization.");
        }
    }

    private ClientDto mapToDto(Client client) {
        ClientDto dto = new ClientDto();
        BeanUtils.copyProperties(client, dto);
        return dto;
    }

    private ClientDto mapToDtoWithCounts(Client client, Long tenantId) {
        ClientDto dto = mapToDto(client);
        try {
            List<Project> projects = (tenantId != null)
                    ? projectRepository.findByOrganizationIdAndClientId(tenantId, client.getId())
                    : projectRepository.findByClientId(client.getId());
            dto.setProjectCount((long) projects.size());
            long active = projects.stream()
                    .filter(p -> p.getStatus() != null && p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.CANCELLED)
                    .count();
            dto.setActiveProjectCount(active);
        } catch (Exception ex) {
            log.warn("Could not calculate project count for client {}: {}", client.getId(), ex.getMessage());
            dto.setProjectCount(0L);
            dto.setActiveProjectCount(0L);
        }
        return dto;
    }
}
