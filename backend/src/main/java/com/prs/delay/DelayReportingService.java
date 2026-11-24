package com.prs.delay;

import com.prs.dto.ReportDelayRequest;
import com.prs.dto.TaskDelayDto;
import com.prs.entity.DelayStatus;
import com.prs.entity.Task;
import com.prs.entity.TaskDelay;
import com.prs.repository.TaskDelayRepository;
import com.prs.repository.TaskRepository;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DelayReportingService {

    private final TaskRepository taskRepository;
    private final TaskDelayRepository delayRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final com.prs.service.NotificationService notificationService;

    @Transactional
    public TaskDelayDto reportDelay(Long taskId, ReportDelayRequest request, String username) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + taskId));

        LocalDate baseFinish = task.getPlannedFinishDate() != null ? task.getPlannedFinishDate() : LocalDate.now();
        LocalDate updatedFinish = workingDayCalculator.addWorkingDays(baseFinish, request.getDelayDurationDays());

        TaskDelay delay = new TaskDelay();
        delay.setTask(task);
        delay.setDelayDurationDays(request.getDelayDurationDays());
        delay.setDelayReason(request.getDelayReason());
        delay.setReportedDate(LocalDate.now());
        delay.setUpdatedExpectedFinish(updatedFinish);
        delay.setNotes(request.getNotes());
        delay.setReportedBy(username != null ? username : "system");
        delay.setStatus(DelayStatus.REPORTED);

        TaskDelay saved = delayRepository.save(delay);

        if (task.getProject() != null && task.getProject().getManager() != null) {
            notificationService.sendNotification(
                    task.getProject().getManager(),
                    com.prs.entity.NotificationEventType.VENDOR_DELAY,
                    "Schedule Delay Reported: " + task.getName(),
                    String.format("A delay of %d working days was reported on task '%s' in project '%s'. Reason: %s",
                            request.getDelayDurationDays(), task.getName(), task.getProject().getName(), request.getDelayReason()),
                    task.getProject().getId(),
                    task.getId()
            );
        }

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskDelayDto> getDelaysForProject(Long projectId) {
        return delayRepository.findByProjectId(projectId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TaskDelayDto> getDelaysForTask(Long taskId) {
        return delayRepository.findByTaskId(taskId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private TaskDelayDto mapToDto(TaskDelay entity) {
        return TaskDelayDto.builder()
                .id(entity.getId())
                .taskId(entity.getTask().getId())
                .taskName(entity.getTask().getName())
                .delayDurationDays(entity.getDelayDurationDays())
                .delayReason(entity.getDelayReason())
                .reportedDate(entity.getReportedDate())
                .updatedExpectedFinish(entity.getUpdatedExpectedFinish())
                .notes(entity.getNotes())
                .reportedBy(entity.getReportedBy())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
