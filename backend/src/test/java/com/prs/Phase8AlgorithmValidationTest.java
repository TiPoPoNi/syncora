package com.prs;

import com.prs.cpm.CpmTaskResult;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.entity.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.SchedulingEngine;
import com.prs.scheduler.WorkingDayCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 8: Mathematical Scheduling & Optimization Algorithm Validation")
public class Phase8AlgorithmValidationTest {

    private WorkingDayCalculator workingDayCalculator;
    private SchedulingEngine schedulingEngine;
    private CriticalPathService criticalPathService;
    private ConflictDetector conflictDetector;

    // Fixed start on Monday: 2026-10-19
    private final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @BeforeEach
    void setUp() {
        workingDayCalculator = new WorkingDayCalculator();
        schedulingEngine = new SchedulingEngine(workingDayCalculator);
        criticalPathService = new CriticalPathService(workingDayCalculator);
        conflictDetector = new ConflictDetector();
    }

    @Test
    @DisplayName("Case A: Sequential tasks (A: 2d -> B: 3d -> C: 2d) = 7 working days, ordering and CPM verified")
    void testCaseA_SequentialTasks() {
        Project project = new Project();
        project.setId(101L);
        project.setStartDate(MONDAY);

        Task a = createTask(1L, "Task A", 2);
        Task b = createTask(2L, "Task B", 3);
        Task c = createTask(3L, "Task C", 2);

        TaskDependency d1 = createDep(a, b);
        TaskDependency d2 = createDep(b, c);

        List<Task> tasks = new ArrayList<>(Arrays.asList(a, b, c));
        List<TaskDependency> deps = Arrays.asList(d1, d2);

        // Run Scheduling Engine
        schedulingEngine.calculateSchedule(project, tasks, deps);

        // Verify task ordering & dates
        // A: Mon Oct 19 -> Tue Oct 20
        assertEquals(LocalDate.of(2026, 10, 19), a.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 10, 20), a.getPlannedFinishDate());

        // B: Wed Oct 21 -> Fri Oct 23
        assertEquals(LocalDate.of(2026, 10, 21), b.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 10, 23), b.getPlannedFinishDate());

        // C: Mon Oct 26 -> Tue Oct 27 (skips Sat Oct 24 & Sun Oct 25)
        assertEquals(LocalDate.of(2026, 10, 26), c.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 10, 27), c.getPlannedFinishDate());

        // Overall completion date
        assertEquals(LocalDate.of(2026, 10, 27), project.getExpectedCompletionDate());

        // Run CPM
        CriticalPathResult cpm = criticalPathService.calculate(MONDAY, tasks, deps);
        assertEquals(7, cpm.getMinimumDurationWorkingDays());
        assertEquals(Arrays.asList(1L, 2L, 3L), cpm.getRepresentativeCriticalPath());
        assertTrue(cpm.getCriticalTaskIds().containsAll(Arrays.asList(1L, 2L, 3L)));
    }

    @Test
    @DisplayName("Case B: Parallel tasks (A: 2d, B: 5d independent, C: 2d dependent on both) = 7 working days")
    void testCaseB_ParallelTasks_NoUnnecessarySerialization() {
        Project project = new Project();
        project.setId(102L);
        project.setStartDate(MONDAY);

        Task a = createTask(1L, "Task A", 2);
        Task b = createTask(2L, "Task B", 5);
        Task c = createTask(3L, "Task C", 2);

        TaskDependency d1 = createDep(a, c);
        TaskDependency d2 = createDep(b, c);

        List<Task> tasks = new ArrayList<>(Arrays.asList(a, b, c));
        List<TaskDependency> deps = Arrays.asList(d1, d2);

        schedulingEngine.calculateSchedule(project, tasks, deps);

        // Independent tasks start on the same project start date without serialization
        assertEquals(MONDAY, a.getPlannedStartDate());
        assertEquals(MONDAY, b.getPlannedStartDate());

        // A finishes Tue Oct 20
        assertEquals(LocalDate.of(2026, 10, 20), a.getPlannedFinishDate());
        // B finishes Fri Oct 23
        assertEquals(LocalDate.of(2026, 10, 23), b.getPlannedFinishDate());

        // C waits for both, so starts after max predecessor finish (Fri Oct 23 -> Mon Oct 26)
        assertEquals(LocalDate.of(2026, 10, 26), c.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 10, 27), c.getPlannedFinishDate());

        assertEquals(LocalDate.of(2026, 10, 27), project.getExpectedCompletionDate());

        CriticalPathResult cpm = criticalPathService.calculate(MONDAY, tasks, deps);
        assertEquals(7, cpm.getMinimumDurationWorkingDays());
    }

    @Test
    @DisplayName("Case C: Non-critical task has total float equal to slack (A has 3 days total float)")
    void testCaseC_NonCriticalTaskTotalFloat() {
        Task a = createTask(1L, "Task A", 2);
        Task b = createTask(2L, "Task B", 5);
        Task c = createTask(3L, "Task C", 2);

        TaskDependency d1 = createDep(a, c);
        TaskDependency d2 = createDep(b, c);

        CriticalPathResult cpm = criticalPathService.calculate(MONDAY, Arrays.asList(a, b, c), Arrays.asList(d1, d2));

        // Total duration is 7
        assertEquals(7, cpm.getMinimumDurationWorkingDays());

        // Find Task A result
        CpmTaskResult resA = cpm.getTaskResults().stream().filter(r -> r.getTaskId() == 1L).findFirst().orElseThrow();
        assertEquals(0, resA.getEarlyStart());
        assertEquals(2, resA.getEarlyFinish());
        assertEquals(3, resA.getLateStart());
        assertEquals(5, resA.getLateFinish());
        assertEquals(3, resA.getTotalSlack()); // 3 working days of total slack!
        assertFalse(resA.isCritical());

        // Task B is on the critical path (slack == 0)
        CpmTaskResult resB = cpm.getTaskResults().stream().filter(r -> r.getTaskId() == 2L).findFirst().orElseThrow();
        assertEquals(0, resB.getTotalSlack());
        assertTrue(resB.isCritical());

        // Task C is critical (slack == 0)
        CpmTaskResult resC = cpm.getTaskResults().stream().filter(r -> r.getTaskId() == 3L).findFirst().orElseThrow();
        assertEquals(0, resC.getTotalSlack());
        assertTrue(resC.isCritical());
    }

    @Test
    @DisplayName("Case D: Delay propagation recalculates from constraints rather than blind addition")
    void testCaseD_DelayPropagationSlackAbsorption() {
        // In Topology from Case B/C:
        // A has 3 days of slack. A 2-day delay on A must NOT push successor C!
        Task a = createTask(1L, "Task A", 2);
        Task b = createTask(2L, "Task B", 5);
        Task c = createTask(3L, "Task C", 2);

        TaskDependency d1 = createDep(a, c);
        TaskDependency d2 = createDep(b, c);

        // Baseline schedule
        Project project = new Project();
        project.setStartDate(MONDAY);
        schedulingEngine.calculateSchedule(project, Arrays.asList(a, b, c), Arrays.asList(d1, d2));

        LocalDate originalCFinish = c.getPlannedFinishDate();

        // Simulate 2-day delay on non-critical Task A (duration 2 -> 4)
        a.setEstimatedDuration(4);
        schedulingEngine.calculateSchedule(project, Arrays.asList(a, b, c), Arrays.asList(d1, d2));

        // A now finishes Thu Oct 22 instead of Tue Oct 20
        assertEquals(LocalDate.of(2026, 10, 22), a.getPlannedFinishDate());
        // Successor C finish must NOT have changed because A's delay is absorbed by slack!
        assertEquals(originalCFinish, c.getPlannedFinishDate());

        // Now simulate 2-day delay on critical Task B (duration 5 -> 7)
        b.setEstimatedDuration(7);
        schedulingEngine.calculateSchedule(project, Arrays.asList(a, b, c), Arrays.asList(d1, d2));

        // B now finishes Tue Oct 27 (skipping weekend)
        assertEquals(LocalDate.of(2026, 10, 27), b.getPlannedFinishDate());
        // Successor C is now pushed because B is bottleneck!
        // C starts Wed Oct 28, finishes Thu Oct 29
        assertEquals(LocalDate.of(2026, 10, 28), c.getPlannedStartDate());
        assertEquals(LocalDate.of(2026, 10, 29), c.getPlannedFinishDate());
    }

    @Test
    @DisplayName("Case E: Resource conflict detected when capacity-1 worker assigned to overlapping tasks")
    void testCaseE_ResourceConflictDetection() {
        Task t1 = createTask(1L, "Task 1", 3);
        t1.setPlannedStartDate(MONDAY);
        t1.setPlannedFinishDate(LocalDate.of(2026, 10, 21));

        Task t2 = createTask(2L, "Task 2", 3);
        t2.setPlannedStartDate(LocalDate.of(2026, 10, 20));
        t2.setPlannedFinishDate(LocalDate.of(2026, 10, 22));

        Worker worker = new Worker();
        worker.setId(55L);
        worker.setName("Alice Dev");

        TaskAssignment a1 = new TaskAssignment();
        a1.setTask(t1);
        a1.setWorker(worker);

        TaskAssignment a2 = new TaskAssignment();
        a2.setTask(t2);
        a2.setWorker(worker);

        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(
                Arrays.asList(t1, t2),
                Arrays.asList(a1, a2)
        );

        assertEquals(1, conflicts.size());
        ResourceConflict conflict = conflicts.get(0);
        assertEquals("Alice Dev", conflict.getResource());
        assertEquals(LocalDate.of(2026, 10, 20), conflict.getOverlapStart());
        assertEquals(LocalDate.of(2026, 10, 21), conflict.getOverlapEnd());
    }

    @Test
    @DisplayName("Case F: Circular dependency graph is rejected with IllegalStateException")
    void testCaseF_InvalidDependencyGraphCycle() {
        Task a = createTask(1L, "A", 2);
        Task b = createTask(2L, "B", 2);
        Task c = createTask(3L, "C", 2);

        // A -> B -> C -> A (cycle!)
        TaskDependency d1 = createDep(a, b);
        TaskDependency d2 = createDep(b, c);
        TaskDependency d3 = createDep(c, a);

        Project project = new Project();
        project.setStartDate(MONDAY);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            schedulingEngine.calculateSchedule(project, Arrays.asList(a, b, c), Arrays.asList(d1, d2, d3));
        });
        assertTrue(ex.getMessage().contains("Circular dependency detected"));
    }

    @Test
    @DisplayName("Case G: Deadline analysis correctly identifies delayed vs on-schedule status")
    void testCaseG_DeadlineAnalysis() {
        Project project = new Project();
        project.setStartDate(MONDAY);
        project.setDeadline(LocalDate.of(2026, 10, 23)); // Friday

        Task a = createTask(1L, "A", 2);
        Task b = createTask(2L, "B", 5);
        TaskDependency dep = createDep(a, b);

        schedulingEngine.calculateSchedule(project, Arrays.asList(a, b), Arrays.asList(dep));

        // When completion date (Oct 27) > deadline (Oct 23):
        // (A: Mon Oct 19 - Tue Oct 20 -> B: Wed Oct 21 - Tue Oct 27)
        assertTrue(project.getExpectedCompletionDate().isAfter(project.getDeadline()));

        // Now extend deadline to Nov 1
        project.setDeadline(LocalDate.of(2026, 11, 1));
        assertFalse(project.getExpectedCompletionDate().isAfter(project.getDeadline()));
    }

    private Task createTask(Long id, String name, int duration) {
        Task t = new Task();
        t.setId(id);
        t.setName(name);
        t.setEstimatedDuration(duration);
        t.setStatus(TaskStatus.TODO);
        return t;
    }

    private TaskDependency createDep(Task pred, Task succ) {
        TaskDependency d = new TaskDependency();
        d.setPredecessorTask(pred);
        d.setSuccessorTask(succ);
        d.setDependencyType(DependencyType.FINISH_TO_START);
        return d;
    }
}
