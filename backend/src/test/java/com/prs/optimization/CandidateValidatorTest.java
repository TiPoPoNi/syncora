package com.prs.optimization;

import com.prs.entity.*;
import com.prs.scheduler.WorkingDayCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CandidateValidatorTest {

    private CandidateValidator validator;
    private WorkingDayCalculator workingDayCalculator;

    @BeforeEach
    void setUp() {
        workingDayCalculator = new WorkingDayCalculator();
        validator = new CandidateValidator(workingDayCalculator);
    }

    @Test
    void testWorkerWithRequiredSkills_isValid() {
        Worker worker = new Worker();
        worker.setId(1L);
        worker.setName("Vikram Mehta");
        worker.setSkills("Backend, Java, Spring, Microservices");
        worker.setStatus(WorkerStatus.AVAILABLE);

        assertTrue(validator.workerHasRequiredSkills(worker, "Backend, Java, Spring"));
    }

    @Test
    void testWorkerLackingMandatorySkill_isRejected() {
        Worker worker = new Worker();
        worker.setId(2L);
        worker.setName("Priya Singh");
        worker.setSkills("Frontend, React, UI/UX");
        worker.setStatus(WorkerStatus.AVAILABLE);

        assertFalse(validator.workerHasRequiredSkills(worker, "Backend, Java, Spring"));

        Task task = new Task();
        task.setId(10L);
        task.setName("Backend API");
        task.setRequiredSkills("Backend, Java, Spring");

        OptimizationCandidate candidate = new OptimizationCandidate();
        candidate.getProposedWorkerReassignments().put(10L, 2L);

        Map<Long, Worker> workerMap = Map.of(2L, worker);
        var result = validator.validate(candidate, List.of(task), Collections.emptyList(), workerMap, Collections.emptyMap());

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("lacks mandatory skills"));
    }

    @Test
    void testUnavailableWorker_isRejected() {
        Worker worker = new Worker();
        worker.setId(3L);
        worker.setName("Offline Worker");
        worker.setSkills("Backend, Java, Spring");
        worker.setStatus(WorkerStatus.UNAVAILABLE);

        Task task = new Task();
        task.setId(11L);
        task.setName("Backend Task");
        task.setRequiredSkills("Backend, Java");

        OptimizationCandidate candidate = new OptimizationCandidate();
        candidate.getProposedWorkerReassignments().put(11L, 3L);

        Map<Long, Worker> workerMap = Map.of(3L, worker);
        var result = validator.validate(candidate, List.of(task), Collections.emptyList(), workerMap, Collections.emptyMap());

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("is not available"));
    }

    @Test
    void testCompletedTask_cannotBeModified() {
        Worker worker = new Worker();
        worker.setId(4L);
        worker.setName("Qualified Worker");
        worker.setSkills("Backend, Java, Spring");
        worker.setStatus(WorkerStatus.AVAILABLE);

        Task completedTask = new Task();
        completedTask.setId(12L);
        completedTask.setName("Architecture Review");
        completedTask.setStatus(TaskStatus.COMPLETED);

        OptimizationCandidate candidate = new OptimizationCandidate();
        candidate.getProposedWorkerReassignments().put(12L, 4L);

        Map<Long, Worker> workerMap = Map.of(4L, worker);
        var result = validator.validate(candidate, List.of(completedTask), Collections.emptyList(), workerMap, Collections.emptyMap());

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Completed task"));
    }
}
