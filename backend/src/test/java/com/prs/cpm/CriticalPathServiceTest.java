package com.prs.cpm;

import com.prs.entity.Task;
import com.prs.entity.TaskDependency;
import com.prs.scheduler.WorkingDayCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CriticalPathServiceTest {

    private CriticalPathService cpmService;
    private WorkingDayCalculator workingDayCalculator;

    @BeforeEach
    void setUp() {
        workingDayCalculator = new WorkingDayCalculator();
        cpmService = new CriticalPathService(workingDayCalculator);
    }

    @Test
    void test1_SequentialTaskChainProducesExpectedCriticalPath() {
        LocalDate start = LocalDate.of(2023, 10, 9); // Monday
        Task t1 = new Task(); t1.setId(1L); t1.setName("Task 1"); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setName("Task 2"); t2.setEstimatedDuration(3);
        Task t3 = new Task(); t3.setId(3L); t3.setName("Task 3"); t3.setEstimatedDuration(1);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t2);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t3);

        CriticalPathResult res = cpmService.calculate(start, Arrays.asList(t1, t2, t3), Arrays.asList(d1, d2));

        assertEquals(6, res.getMinimumDurationWorkingDays());
        assertEquals(3, res.getCriticalTaskIds().size());
        assertTrue(res.getCriticalTaskIds().containsAll(Arrays.asList(1L, 2L, 3L)));
        assertEquals(Arrays.asList(1L, 2L, 3L), res.getRepresentativeCriticalPath());

        // Check task 1 (ES=0, EF=2, LS=0, LF=2, Slack=0)
        CpmTaskResult r1 = res.getTaskResults().stream().filter(t -> t.getTaskId() == 1L).findFirst().get();
        assertEquals(0, r1.getEarlyStart());
        assertEquals(2, r1.getEarlyFinish());
        assertEquals(0, r1.getTotalSlack());
        assertTrue(r1.isCritical());

        // Check task 2 (ES=2, EF=5, LS=2, LF=5, Slack=0)
        CpmTaskResult r2 = res.getTaskResults().stream().filter(t -> t.getTaskId() == 2L).findFirst().get();
        assertEquals(2, r2.getEarlyStart());
        assertEquals(5, r2.getEarlyFinish());
        assertEquals(0, r2.getTotalSlack());
    }

    @Test
    void test2_ParallelBranchesProduceCorrectDurationAndSlack() {
        LocalDate start = LocalDate.of(2023, 10, 9); // Monday
        // Branch 1: T1 (2d) -> T2 (4d) = 6d
        // Branch 2: T3 (3d)           = 3d
        // Both merge at T4 (2d)
        Task t1 = new Task(); t1.setId(1L); t1.setName("T1"); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setName("T2"); t2.setEstimatedDuration(4);
        Task t3 = new Task(); t3.setId(3L); t3.setName("T3"); t3.setEstimatedDuration(3);
        Task t4 = new Task(); t4.setId(4L); t4.setName("T4"); t4.setEstimatedDuration(2);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t2);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t4);
        TaskDependency d3 = new TaskDependency(); d3.setPredecessorTask(t3); d3.setSuccessorTask(t4);

        CriticalPathResult res = cpmService.calculate(start, Arrays.asList(t1, t2, t3, t4), Arrays.asList(d1, d2, d3));

        // Total duration: 2 + 4 + 2 = 8 days
        assertEquals(8, res.getMinimumDurationWorkingDays());

        // Critical path is T1 -> T2 -> T4
        assertEquals(Arrays.asList(1L, 2L, 4L), res.getRepresentativeCriticalPath());

        // T3 has duration 3 and must finish before day 6, so slack = 6 - 3 = 3 days!
        CpmTaskResult r3 = res.getTaskResults().stream().filter(t -> t.getTaskId() == 3L).findFirst().get();
        assertFalse(r3.isCritical());
        assertEquals(0, r3.getEarlyStart());
        assertEquals(3, r3.getEarlyFinish());
        assertEquals(3, r3.getLateStart());
        assertEquals(6, r3.getLateFinish());
        assertEquals(3, r3.getTotalSlack());
    }

    @Test
    void test3_MultiplePredecessorsStartsAfterLatestFinish() {
        LocalDate start = LocalDate.of(2023, 10, 9);
        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(5);
        Task t3 = new Task(); t3.setId(3L); t3.setEstimatedDuration(2);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t3);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t3);

        CriticalPathResult res = cpmService.calculate(start, Arrays.asList(t1, t2, t3), Arrays.asList(d1, d2));

        CpmTaskResult r3 = res.getTaskResults().stream().filter(t -> t.getTaskId() == 3L).findFirst().get();
        assertEquals(5, r3.getEarlyStart());
        assertEquals(7, r3.getEarlyFinish());
    }

    @Test
    void test4_MultipleCriticalPathsHandled() {
        LocalDate start = LocalDate.of(2023, 10, 9);
        // Branch 1: T1 (3d) -> T3 (2d) = 5d
        // Branch 2: T2 (3d) -> T3 (2d) = 5d
        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(3);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(3);
        Task t3 = new Task(); t3.setId(3L); t3.setEstimatedDuration(2);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t3);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t3);

        CriticalPathResult res = cpmService.calculate(start, Arrays.asList(t1, t2, t3), Arrays.asList(d1, d2));

        assertEquals(5, res.getMinimumDurationWorkingDays());
        assertEquals(3, res.getCriticalTaskIds().size());
        assertEquals(2, res.getAllCriticalPaths().size());
    }

    @Test
    void test5_CircularDependencyRejection() {
        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(2);

        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t2);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t1);

        assertThrows(IllegalStateException.class, () ->
                cpmService.calculate(LocalDate.now(), Arrays.asList(t1, t2), Arrays.asList(d1, d2))
        );
    }

    @Test
    void test6_EmptyProjectReturnsZeroDuration() {
        CriticalPathResult res = cpmService.calculate(LocalDate.of(2023, 10, 9), Collections.emptyList(), Collections.emptyList());
        assertEquals(0, res.getMinimumDurationWorkingDays());
        assertTrue(res.getTaskResults().isEmpty());
    }
}
