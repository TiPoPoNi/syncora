package com.prs.service;

import com.prs.cpm.CpmTaskResult;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.delay.DelayPropagationService;
import com.prs.delay.DelayReportingService;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.ProjectRepository;
import com.prs.repository.TaskAssignmentRepository;
import com.prs.repository.TaskDependencyRepository;
import com.prs.repository.TaskRepository;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectScheduleService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final ConflictDetector conflictDetector;
    private final CriticalPathService criticalPathService;
    private final DelayPropagationService delayPropagationService;
    private final DelayReportingService delayReportingService;
    private final WorkingDayCalculator workingDayCalculator;

    @Transactional
    public ProjectScheduleDto recalculateSchedule(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);
        
        // Propagate delays and recalculate schedule
        delayPropagationService.propagateAndPersistSchedule(project, tasks, dependencies);
        
        // Save updated project and tasks to DB
        projectRepository.save(project);
        taskRepository.saveAll(tasks);
        
        return getSchedule(projectId);
    }

    public ProjectScheduleDto getSchedule(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);
        
        List<TaskAssignment> allAssignments = assignmentRepository.findAll();
        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(tasks, allAssignments);
        
        // Map to DTO
        ProjectScheduleDto dto = new ProjectScheduleDto();
        dto.setProjectId(project.getId());
        dto.setProjectName(project.getName());
        dto.setStartDate(project.getStartDate());
        dto.setDeadline(project.getDeadline());
        dto.setExpectedCompletionDate(project.getExpectedCompletionDate());
        dto.setStatus(project.getStatus());
        
        // Calculate deadline status
        calculateDeadlineStatus(dto, project);
        
        List<TaskScheduleDto> taskDtos = tasks.stream().map(task -> {
            TaskScheduleDto taskDto = new TaskScheduleDto();
            taskDto.setId(task.getId());
            taskDto.setName(task.getName());
            taskDto.setEstimatedDuration(task.getEstimatedDuration());
            taskDto.setPlannedStartDate(task.getPlannedStartDate());
            taskDto.setPlannedFinishDate(task.getPlannedFinishDate());
            taskDto.setStatus(task.getStatus());
            
            // Map assignments
            List<TaskAssignmentDto> taskAssignments = assignmentRepository.findByTaskId(task.getId()).stream()
                    .map(a -> {
                        TaskAssignmentDto ta = new TaskAssignmentDto();
                        ta.setId(a.getId());
                        ta.setTaskId(a.getTask().getId());
                        ta.setAssignmentType(a.getAssignmentType());
                        if (a.getWorker() != null) {
                            ta.setWorkerId(a.getWorker().getId());
                            ta.setWorkerName(a.getWorker().getName());
                        }
                        if (a.getVendor() != null) {
                            ta.setVendorId(a.getVendor().getId());
                            ta.setVendorName(a.getVendor().getName());
                        }
                        return ta;
                    }).collect(Collectors.toList());
            taskDto.setAssignments(taskAssignments);
            
            // Map dependencies
            List<Long> predIds = dependencies.stream()
                    .filter(d -> d.getSuccessorTask().getId().equals(task.getId()))
                    .map(d -> d.getPredecessorTask().getId())
                    .collect(Collectors.toList());
            taskDto.setPredecessorTaskIds(predIds);
            
            return taskDto;
        }).collect(Collectors.toList());
        
        dto.setTasks(taskDtos);
        
        // Filter conflicts to only include those relevant to this project
        List<ResourceConflict> projectConflicts = conflicts.stream()
                .filter(c -> tasks.stream().anyMatch(t -> t.getName().equals(c.getConflictingTask1()) || t.getName().equals(c.getConflictingTask2())))
                .collect(Collectors.toList());
                
        dto.setConflicts(projectConflicts);
        
        return dto;
    }

    public CriticalPathDto getCriticalPath(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        CriticalPathResult result = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);

        List<CpmTaskDto> cpmTasks = result.getTaskResults().stream().map(this::mapToCpmTaskDto).collect(Collectors.toList());

        return CriticalPathDto.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .projectStartDate(result.getProjectStartDate())
                .earliestProjectedCompletion(result.getEarliestProjectedCompletion())
                .minimumDurationWorkingDays(result.getMinimumDurationWorkingDays())
                .tasks(cpmTasks)
                .criticalTaskIds(result.getCriticalTaskIds())
                .representativeCriticalPath(result.getRepresentativeCriticalPath())
                .allCriticalPaths(result.getAllCriticalPaths())
                .criticalTaskNames(result.getCriticalTaskNames())
                .build();
    }

    public ScheduleAnalysisDto getScheduleAnalysis(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        CriticalPathResult cpm = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);
        List<TaskAssignment> allAssignments = assignmentRepository.findAll();
        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(tasks, allAssignments);

        // Filter conflicts
        List<ResourceConflict> projectConflicts = conflicts.stream()
                .filter(c -> tasks.stream().anyMatch(t -> t.getName().equals(c.getConflictingTask1()) || t.getName().equals(c.getConflictingTask2())))
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>();
        if (!projectConflicts.isEmpty()) {
            warnings.add("Resource conflicts detected: " + projectConflicts.size() + " worker allocation overlap(s).");
        }

        LocalDate completion = project.getExpectedCompletionDate() != null
                ? project.getExpectedCompletionDate()
                : cpm.getEarliestProjectedCompletion();

        int delayDays = 0;
        String deadlineStatus = "ON_TRACK";
        if (project.getDeadline() != null && completion != null) {
            int diff = workingDayCalculator.calculateWorkingDaysBetween(project.getDeadline(), completion);
            if (diff > 0) {
                deadlineStatus = "DELAYED";
                delayDays = diff;
                warnings.add("Project completion exceeds client deadline by " + diff + " working day(s).");
            } else if (diff >= -2) {
                deadlineStatus = "AT_RISK";
                warnings.add("Project completion is within 2 working days of deadline.");
            }
        }

        List<CpmTaskDto> cpmTasks = cpm.getTaskResults().stream().map(this::mapToCpmTaskDto).collect(Collectors.toList());

        return ScheduleAnalysisDto.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .startDate(project.getStartDate())
                .deadline(project.getDeadline())
                .expectedCompletionDate(completion)
                .minimumDurationWorkingDays(cpm.getMinimumDurationWorkingDays())
                .cpmAnalysisStatus("SUCCESS")
                .deadlineStatus(deadlineStatus)
                .delayDays(delayDays)
                .taskAnalysis(cpmTasks)
                .criticalPath(cpm.getCriticalTaskNames())
                .resourceConflicts(projectConflicts)
                .schedulingWarnings(warnings)
                .build();
    }

    public GanttChartDto getGanttData(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        CriticalPathResult cpm = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);
        Map<Long, CpmTaskResult> cpmMap = cpm.getTaskResults().stream()
                .collect(Collectors.toMap(CpmTaskResult::getTaskId, r -> r));

        List<TaskDelayDto> delays = delayReportingService.getDelaysForProject(projectId);
        Map<Long, Integer> delayMap = new HashMap<>();
        for (TaskDelayDto d : delays) {
            if (d.getStatus() != DelayStatus.RESOLVED) {
                delayMap.merge(d.getTaskId(), d.getDelayDurationDays(), Integer::sum);
            }
        }

        List<GanttTaskDto> ganttTasks = tasks.stream().map(task -> {
            CpmTaskResult cpmRes = cpmMap.get(task.getId());
            boolean isCritical = cpmRes != null && cpmRes.isCritical();
            int slack = cpmRes != null ? cpmRes.getTotalSlack() : 0;
            int delay = delayMap.getOrDefault(task.getId(), 0);

            List<Long> predIds = dependencies.stream()
                    .filter(d -> d.getSuccessorTask().getId().equals(task.getId()))
                    .map(d -> d.getPredecessorTask().getId())
                    .collect(Collectors.toList());

            List<String> assignees = assignmentRepository.findByTaskId(task.getId()).stream()
                    .map(a -> a.getWorker() != null ? a.getWorker().getName() : (a.getVendor() != null ? a.getVendor().getName() : "Unassigned"))
                    .collect(Collectors.toList());

            LocalDate start = task.getPlannedStartDate() != null ? task.getPlannedStartDate()
                    : (cpmRes != null ? cpmRes.getEarlyStartDate() : project.getStartDate());
            LocalDate finish = task.getPlannedFinishDate() != null ? task.getPlannedFinishDate()
                    : (cpmRes != null ? cpmRes.getEarlyFinishDate() : project.getStartDate());

            return GanttTaskDto.builder()
                    .id(task.getId())
                    .name(task.getName())
                    .startDate(start)
                    .finishDate(finish)
                    .duration(task.getEstimatedDuration() != null ? task.getEstimatedDuration() : 1)
                    .progress(task.getProgress() != null ? task.getProgress() : 0)
                    .status(task.getStatus())
                    .critical(isCritical)
                    .delayed(delay > 0)
                    .delayDays(delay)
                    .totalSlack(slack)
                    .dependencies(predIds)
                    .assignees(assignees)
                    .build();
        }).collect(Collectors.toList());

        LocalDate completion = project.getExpectedCompletionDate() != null ? project.getExpectedCompletionDate() : cpm.getEarliestProjectedCompletion();
        String deadlineStatus = "ON_TRACK";
        if (project.getDeadline() != null && completion != null) {
            int diff = workingDayCalculator.calculateWorkingDaysBetween(project.getDeadline(), completion);
            if (diff > 0) deadlineStatus = "DELAYED";
            else if (diff >= -2) deadlineStatus = "AT_RISK";
        }

        return GanttChartDto.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .projectStartDate(project.getStartDate())
                .projectDeadline(project.getDeadline())
                .expectedCompletionDate(completion)
                .deadlineStatus(deadlineStatus)
                .currentDate(LocalDate.now())
                .tasks(ganttTasks)
                .build();
    }

    public DelayImpactDto getDelayImpact(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        return delayPropagationService.analyzeDelayImpact(project, tasks, dependencies);
    }

    private void calculateDeadlineStatus(ProjectScheduleDto dto, Project project) {
        if (project.getExpectedCompletionDate() != null && project.getDeadline() != null) {
            int daysDiff = workingDayCalculator.calculateWorkingDaysBetween(project.getDeadline(), project.getExpectedCompletionDate());
            if (daysDiff > 0) {
                dto.setDeadlineStatus("DELAYED");
                dto.setDelayDays(daysDiff);
            } else if (daysDiff >= -2) {
                dto.setDeadlineStatus("AT_RISK");
                dto.setDelayDays(0);
            } else {
                dto.setDeadlineStatus("ON_TRACK");
                dto.setDelayDays(0);
            }
        } else {
            dto.setDeadlineStatus("ON_TRACK");
            dto.setDelayDays(0);
        }
    }

    private CpmTaskDto mapToCpmTaskDto(CpmTaskResult res) {
        return CpmTaskDto.builder()
                .taskId(res.getTaskId())
                .taskName(res.getTaskName())
                .duration(res.getDuration())
                .earlyStart(res.getEarlyStart())
                .earlyFinish(res.getEarlyFinish())
                .lateStart(res.getLateStart())
                .lateFinish(res.getLateFinish())
                .totalSlack(res.getTotalSlack())
                .critical(res.isCritical())
                .earlyStartDate(res.getEarlyStartDate())
                .earlyFinishDate(res.getEarlyFinishDate())
                .lateStartDate(res.getLateStartDate())
                .lateFinishDate(res.getLateFinishDate())
                .predecessorTaskIds(res.getPredecessorTaskIds())
                .successorTaskIds(res.getSuccessorTaskIds())
                .build();
    }
}
