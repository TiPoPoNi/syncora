package com.prs.baseline;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.ProjectBaselineDto;
import com.prs.dto.TaskBaselineDto;
import com.prs.entity.Project;
import com.prs.entity.ProjectBaseline;
import com.prs.entity.Task;
import com.prs.entity.TaskBaseline;
import com.prs.entity.TaskDependency;
import com.prs.entity.User;
import com.prs.repository.ProjectBaselineRepository;
import com.prs.repository.ProjectRepository;
import com.prs.repository.TaskBaselineRepository;
import com.prs.repository.TaskDependencyRepository;
import com.prs.repository.TaskRepository;
import com.prs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectBaselineService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final ProjectBaselineRepository baselineRepository;
    private final TaskBaselineRepository taskBaselineRepository;
    private final UserRepository userRepository;
    private final CriticalPathService criticalPathService;

    @Transactional
    public ProjectBaselineDto createBaseline(Long projectId, String versionName, String username) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = dependencyRepository.findBySuccessorTaskProjectId(projectId);

        CriticalPathResult cpm = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);

        User creator = null;
        if (username != null) {
            creator = userRepository.findByUsername(username).orElse(null);
        }

        LocalDate completionDate = project.getExpectedCompletionDate() != null
                ? project.getExpectedCompletionDate()
                : cpm.getEarliestProjectedCompletion();

        int minDuration = cpm.getMinimumDurationWorkingDays();

        String version = (versionName != null && !versionName.trim().isEmpty())
                ? versionName.trim()
                : "Baseline v" + (baselineRepository.findByProjectIdOrderByCreatedAtDesc(projectId).size() + 1);

        ProjectBaseline baseline = new ProjectBaseline();
        baseline.setProject(project);
        baseline.setVersionName(version);
        baseline.setExpectedCompletionDate(completionDate);
        baseline.setMinimumDurationDays(minDuration);
        baseline.setCreatedBy(creator);

        ProjectBaseline savedBaseline = baselineRepository.save(baseline);

        List<TaskBaseline> taskBaselines = tasks.stream().map(task -> {
            TaskBaseline tb = new TaskBaseline();
            tb.setBaseline(savedBaseline);
            tb.setTask(task);
            tb.setTaskName(task.getName());
            tb.setPlannedStartDate(task.getPlannedStartDate() != null ? task.getPlannedStartDate() : project.getStartDate());
            tb.setPlannedFinishDate(task.getPlannedFinishDate() != null ? task.getPlannedFinishDate() : completionDate);
            tb.setDurationDays(task.getEstimatedDuration() != null ? task.getEstimatedDuration() : 1);
            return tb;
        }).collect(Collectors.toList());

        taskBaselineRepository.saveAll(taskBaselines);

        return mapToDto(savedBaseline, taskBaselines);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectBaselineDto> getLatestBaseline(Long projectId) {
        return baselineRepository.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .map(b -> {
                    List<TaskBaseline> tbList = taskBaselineRepository.findByBaselineId(b.getId());
                    return mapToDto(b, tbList);
                });
    }

    @Transactional(readOnly = true)
    public List<ProjectBaselineDto> getAllBaselines(Long projectId) {
        return baselineRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(b -> {
                    List<TaskBaseline> tbList = taskBaselineRepository.findByBaselineId(b.getId());
                    return mapToDto(b, tbList);
                })
                .collect(Collectors.toList());
    }

    private ProjectBaselineDto mapToDto(ProjectBaseline b, List<TaskBaseline> taskBaselines) {
        List<TaskBaselineDto> tbDtos = taskBaselines.stream().map(tb -> TaskBaselineDto.builder()
                .id(tb.getId())
                .taskId(tb.getTask().getId())
                .taskName(tb.getTaskName())
                .plannedStartDate(tb.getPlannedStartDate())
                .plannedFinishDate(tb.getPlannedFinishDate())
                .durationDays(tb.getDurationDays())
                .build()
        ).collect(Collectors.toList());

        return ProjectBaselineDto.builder()
                .id(b.getId())
                .projectId(b.getProject().getId())
                .projectName(b.getProject().getName())
                .versionName(b.getVersionName())
                .expectedCompletionDate(b.getExpectedCompletionDate())
                .minimumDurationDays(b.getMinimumDurationDays())
                .createdBy(b.getCreatedBy() != null ? b.getCreatedBy().getUsername() : null)
                .createdAt(b.getCreatedAt())
                .taskBaselines(tbDtos)
                .build();
    }
}
