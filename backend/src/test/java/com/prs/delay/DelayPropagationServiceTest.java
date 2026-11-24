package com.prs.delay;

import com.prs.cpm.CriticalPathService;
import com.prs.dto.DelayImpactDto;
import com.prs.dto.TaskDelayDto;
import com.prs.entity.*;
import com.prs.repository.ProjectBaselineRepository;
import com.prs.repository.TaskBaselineRepository;
import com.prs.repository.TaskDelayRepository;
import com.prs.scheduler.WorkingDayCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class DelayPropagationServiceTest {

    private DelayPropagationService delayPropagationService;
    private DelayReportingService delayReportingService;
    private WorkingDayCalculator workingDayCalculator;
    private CriticalPathService criticalPathService;
    private TaskDelayRepository taskDelayRepository;
    private ProjectBaselineRepository baselineRepository;
    private TaskBaselineRepository taskBaselineRepository;

    @BeforeEach
    void setUp() {
        workingDayCalculator = new WorkingDayCalculator();
        criticalPathService = new CriticalPathService(workingDayCalculator);
        taskDelayRepository = Mockito.mock(TaskDelayRepository.class);
        baselineRepository = Mockito.mock(ProjectBaselineRepository.class);
        taskBaselineRepository = Mockito.mock(TaskBaselineRepository.class);
        delayReportingService = Mockito.mock(DelayReportingService.class);

        delayPropagationService = new DelayPropagationService(
                workingDayCalculator,
                criticalPathService,
                taskDelayRepository,
                baselineRepository,
                taskBaselineRepository,
                delayReportingService
        );
    }

    @Test
    void test1_DelayOnCriticalTaskPushesProjectCompletion() {
        LocalDate start = LocalDate.of(2023, 10, 9); // Monday
        Project p = new Project();
        p.setId(1L);
        p.setName("Test Project");
        p.setStartDate(start);
        p.setDeadline(LocalDate.of(2023, 10, 20));

        Task t1 = new Task(); t1.setId(1L); t1.setName("Critical 1"); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setName("Critical 2"); t2.setEstimatedDuration(3);

        TaskDependency dep = new TaskDependency();
        dep.setPredecessorTask(t1);
        dep.setSuccessorTask(t2);

        // Report a 2-day delay on t1
        TaskDelayDto delay = TaskDelayDto.builder()
                .taskId(1L)
                .delayDurationDays(2)
                .delayReason("Scope adjustment")
                .status(DelayStatus.REPORTED)
                .build();

        when(delayReportingService.getDelaysForProject(1L)).thenReturn(Collections.singletonList(delay));
        when(baselineRepository.findFirstByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        DelayImpactDto impact = delayPropagationService.analyzeDelayImpact(p, Arrays.asList(t1, t2), Collections.singletonList(dep));

        // Original duration was 2 + 3 = 5 working days (finish: Oct 13)
        // With 2-day delay on T1 (now 4d + 3d = 7 working days) -> finish: Oct 17
        assertEquals(LocalDate.of(2023, 10, 17), impact.getForecastCompletionDate());
        assertTrue(impact.getAffectedTasks().contains("Critical 1"));
        assertTrue(impact.getAffectedTasks().contains("Critical 2"));
        assertEquals("AT_RISK", impact.getDeadlineStatus()); // Critical task delayed, so project is at risk!
    }

    @Test
    void test2_DelayOnNonCriticalTaskAbsorbedBySlack() {
        LocalDate start = LocalDate.of(2023, 10, 9); // Monday
        Project p = new Project();
        p.setId(1L);
        p.setName("Parallel Project");
        p.setStartDate(start);
        p.setDeadline(LocalDate.of(2023, 10, 25));

        // Critical branch: T1 (2d) -> T2 (5d) = 7d
        // Non-critical branch: T3 (2d) = 2d (slack = 5 days)
        // Merge at T4 (1d)
        Task t1 = new Task(); t1.setId(1L); t1.setName("T1"); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setName("T2"); t2.setEstimatedDuration(5);
        Task t3 = new Task(); t3.setId(3L); t3.setName("T3 Non-critical"); t3.setEstimatedDuration(2);
        Task t4 = new Task(); t4.setId(4L); t4.setName("T4"); t4.setEstimatedDuration(1);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t2);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t4);
        TaskDependency d3 = new TaskDependency(); d3.setPredecessorTask(t3); d3.setSuccessorTask(t4);

        // Report 2-day delay on T3 (slack is 5 days, so 2 days is easily absorbed!)
        TaskDelayDto delay = TaskDelayDto.builder()
                .taskId(3L)
                .delayDurationDays(2)
                .delayReason("Minor vendor hold")
                .status(DelayStatus.REPORTED)
                .build();

        when(delayReportingService.getDelaysForProject(1L)).thenReturn(Collections.singletonList(delay));
        when(baselineRepository.findFirstByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        DelayImpactDto impact = delayPropagationService.analyzeDelayImpact(p, Arrays.asList(t1, t2, t3, t4), Arrays.asList(d1, d2, d3));

        // T4's start should NOT move, because T2 still finishes after T3!
        // Total project duration remains: 2 + 5 + 1 = 8 working days!
        // Mon 9 -> 8 working days = Wed Oct 18
        assertEquals(LocalDate.of(2023, 10, 18), impact.getForecastCompletionDate());

        // T4 is unaffected!
        assertTrue(impact.getUnaffectedTasks().contains("T4"));
        assertFalse(impact.getAffectedTasks().contains("T4"));
    }

    @Test
    void test3_DelayExceedingDeadlineReturnsDelayedStatus() {
        LocalDate start = LocalDate.of(2023, 10, 9);
        Project p = new Project();
        p.setId(1L);
        p.setStartDate(start);
        p.setDeadline(LocalDate.of(2023, 10, 13)); // Friday

        Task t1 = new Task(); t1.setId(1L); t1.setName("Task 1"); t1.setEstimatedDuration(5);

        // Add 3-day delay pushes completion past Friday to next Wednesday
        TaskDelayDto delay = TaskDelayDto.builder()
                .taskId(1L)
                .delayDurationDays(3)
                .delayReason("Resource unavailability")
                .status(DelayStatus.REPORTED)
                .build();

        when(delayReportingService.getDelaysForProject(1L)).thenReturn(Collections.singletonList(delay));
        when(baselineRepository.findFirstByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        DelayImpactDto impact = delayPropagationService.analyzeDelayImpact(p, Collections.singletonList(t1), Collections.emptyList());

        assertEquals("DELAYED", impact.getDeadlineStatus());
        assertTrue(impact.getVarianceFromDeadlineWorkingDays() > 0);
    }
}
