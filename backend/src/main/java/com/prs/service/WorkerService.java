package com.prs.service;

import com.prs.dto.WorkerDto;
import com.prs.entity.Organization;
import com.prs.entity.Worker;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.UserRepository;
import com.prs.repository.WorkerRepository;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final com.prs.repository.TaskAssignmentRepository taskAssignmentRepository;

    public List<WorkerDto> getAllWorkers() {
        Long tenantId = TenantContext.getTenantId();
        List<Worker> workers;
        if (tenantId != null) {
            workers = workerRepository.findByOrganizationId(tenantId);
        } else {
            workers = workerRepository.findAll();
        }
        return workers.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public WorkerDto getWorkerById(Long id) {
        Worker worker = workerRepository.findById(id).orElseThrow();
        validateTenantAccess(worker);
        return mapToDto(worker);
    }

    public WorkerDto createWorker(WorkerDto workerDto) {
        Worker worker = new Worker();
        BeanUtils.copyProperties(workerDto, worker, "id", "createdAt", "updatedAt");
        
        Organization org = TenantContext.getOrganization();
        if (org == null && TenantContext.getTenantId() != null) {
            org = organizationRepository.findById(TenantContext.getTenantId()).orElse(null);
        }
        if (org == null) {
            org = organizationRepository.findAll().stream().findFirst().orElse(null);
        }
        worker.setOrganization(org);

        if (workerDto.getUserId() != null) {
            worker.setUser(userRepository.findById(workerDto.getUserId()).orElse(null));
        }
        return mapToDto(workerRepository.save(worker));
    }

    public WorkerDto updateWorker(Long id, WorkerDto workerDto) {
        Worker worker = workerRepository.findById(id).orElseThrow();
        validateTenantAccess(worker);
        BeanUtils.copyProperties(workerDto, worker, "id", "createdAt", "updatedAt", "user", "organization");
        if (workerDto.getUserId() != null) {
            worker.setUser(userRepository.findById(workerDto.getUserId()).orElse(null));
        }
        return mapToDto(workerRepository.save(worker));
    }

    public void deleteWorker(Long id) {
        Worker worker = workerRepository.findById(id).orElseThrow();
        validateTenantAccess(worker);
        workerRepository.deleteById(id);
    }

    private void validateTenantAccess(Worker worker) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && worker.getOrganization() != null && !worker.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Worker does not belong to active organization.");
        }
    }

    private WorkerDto mapToDto(Worker worker) {
        WorkerDto dto = new WorkerDto();
        BeanUtils.copyProperties(worker, dto);
        if (worker.getUser() != null) {
            dto.setUserId(worker.getUser().getId());
        }
        if (worker.getId() != null) {
            long activeTasks = taskAssignmentRepository.findByWorkerId(worker.getId()).stream()
                    .filter(a -> a.getTask() != null && a.getTask().getStatus() != com.prs.entity.TaskStatus.COMPLETED)
                    .count();
            dto.setActiveTaskCount((int) activeTasks);
        }
        return dto;
    }
}
