package com.prs.scheduler;

import com.prs.entity.Project;
import com.prs.entity.Task;
import com.prs.entity.TaskDependency;
import com.prs.entity.Worker;
import com.prs.entity.TaskAssignment;
import com.prs.entity.AssignmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class SchedulingEngineTest {

    private SchedulingEngine schedulingEngine;
    private ConflictDetector conflictDetector;
    private WorkingDayCalculator workingDayCalculator;

    @BeforeEach
    void setUp() {
        workingDayCalculator = new WorkingDayCalculator();
        schedulingEngine = new SchedulingEngine(workingDayCalculator);
        conflictDetector = new ConflictDetector();
    }

    @Test
    void test1_SimpleSequentialTasks() {
        Project p = new Project();
        p.setStartDate(LocalDate.of(2023, 10, 9)); // Monday

        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(3);
        
        TaskDependency dep = new TaskDependency();
        dep.setPredecessorTask(t1);
        dep.setSuccessorTask(t2);

        schedulingEngine.calculateSchedule(p, Arrays.asList(t1, t2), Collections.singletonList(dep));

        assertEquals(LocalDate.of(2023, 10, 9), t1.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 10), t1.getPlannedFinishDate()); // Mon-Tue
        assertEquals(LocalDate.of(2023, 10, 11), t2.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 13), t2.getPlannedFinishDate()); // Wed-Fri
        assertEquals(LocalDate.of(2023, 10, 13), p.getExpectedCompletionDate());
    }

    @Test
    void test2_ParallelTasks() {
        Project p = new Project();
        p.setStartDate(LocalDate.of(2023, 10, 9));

        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(5);
        Task t3 = new Task(); t3.setId(3L); t3.setEstimatedDuration(3);
        
        // No dependencies, should run parallel
        schedulingEngine.calculateSchedule(p, Arrays.asList(t1, t2, t3), Collections.emptyList());

        assertEquals(LocalDate.of(2023, 10, 9), t1.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 9), t2.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 9), t3.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 13), t2.getPlannedFinishDate()); // longest task
        assertEquals(LocalDate.of(2023, 10, 13), p.getExpectedCompletionDate());
    }

    @Test
    void test3_MultiplePredecessors() {
        Project p = new Project();
        p.setStartDate(LocalDate.of(2023, 10, 9));

        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(2);
        Task t2 = new Task(); t2.setId(2L); t2.setEstimatedDuration(5);
        Task t3 = new Task(); t3.setId(3L); t3.setEstimatedDuration(1);
        
        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t3);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t3);

        schedulingEngine.calculateSchedule(p, Arrays.asList(t1, t2, t3), Arrays.asList(d1, d2));

        // t3 must start after t2 finishes, because t2 takes 5 days and t1 takes 2 days.
        assertEquals(LocalDate.of(2023, 10, 13), t2.getPlannedFinishDate()); // Friday
        assertEquals(LocalDate.of(2023, 10, 16), t3.getPlannedStartDate()); // Next Monday!
    }

    @Test
    void test4_CircularDependencyRejection() {
        Project p = new Project();
        p.setStartDate(LocalDate.now());

        Task t1 = new Task(); t1.setId(1L);
        Task t2 = new Task(); t2.setId(2L);
        
        TaskDependency d1 = new TaskDependency(); d1.setPredecessorTask(t1); d1.setSuccessorTask(t2);
        TaskDependency d2 = new TaskDependency(); d2.setPredecessorTask(t2); d2.setSuccessorTask(t1);

        assertThrows(IllegalStateException.class, () -> 
            schedulingEngine.calculateSchedule(p, Arrays.asList(t1, t2), Arrays.asList(d1, d2))
        );
    }

    @Test
    void test5_WorkerOverlappingAssignments() {
        Worker w1 = new Worker(); w1.setId(1L); w1.setName("Rahul");

        Task t1 = new Task(); t1.setId(1L); t1.setName("Task1");
        t1.setPlannedStartDate(LocalDate.of(2023, 10, 10));
        t1.setPlannedFinishDate(LocalDate.of(2023, 10, 15));

        Task t2 = new Task(); t2.setId(2L); t2.setName("Task2");
        t2.setPlannedStartDate(LocalDate.of(2023, 10, 12));
        t2.setPlannedFinishDate(LocalDate.of(2023, 10, 16));

        TaskAssignment a1 = new TaskAssignment(); a1.setTask(t1); a1.setWorker(w1); a1.setAssignmentType(AssignmentType.WORKER);
        TaskAssignment a2 = new TaskAssignment(); a2.setTask(t2); a2.setWorker(w1); a2.setAssignmentType(AssignmentType.WORKER);

        List<ResourceConflict> conflicts = conflictDetector.detectConflicts(Arrays.asList(t1, t2), Arrays.asList(a1, a2));

        assertEquals(1, conflicts.size());
        assertEquals("Rahul", conflicts.get(0).getResource());
        assertEquals(LocalDate.of(2023, 10, 12), conflicts.get(0).getOverlapStart());
        assertEquals(LocalDate.of(2023, 10, 15), conflicts.get(0).getOverlapEnd());
    }

    @Test
    void test9_WeekendWorkingDayCalculation() {
        Project p = new Project();
        p.setStartDate(LocalDate.of(2023, 10, 12)); // Thursday

        Task t1 = new Task(); t1.setId(1L); t1.setEstimatedDuration(3);
        
        schedulingEngine.calculateSchedule(p, Collections.singletonList(t1), Collections.emptyList());

        // Thu(12), Fri(13), Mon(16)
        assertEquals(LocalDate.of(2023, 10, 12), t1.getPlannedStartDate());
        assertEquals(LocalDate.of(2023, 10, 16), t1.getPlannedFinishDate());
    }
}
