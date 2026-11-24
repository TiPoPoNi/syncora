package com.prs.optimization;

import com.prs.entity.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.SchedulingEngine;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CandidateEvaluator {

    private final SchedulingEngine schedulingEngine;
    private final ConflictDetector conflictDetector;
    private final WorkingDayCalculator workingDayCalculator;

    public void evaluate(
            OptimizationCandidate candidate,
            Project project,
            List<Task> originalTasks,
            List<TaskDependency> dependencies,
            List<TaskAssignment> allSystemAssignments,
            int initialConflictCount,
            LocalDate currentForecastCompletion) {

        // 1. Clone tasks for simulation
        Map<Long, Task> simTaskMap = new HashMap<>();
        List<Task> simTasks = new ArrayList<>();
        for (Task t : originalTasks) {
            Task clone = new Task();
            clone.setId(t.getId());
            clone.setName(t.getName());
            clone.setDescription(t.getDescription());
            clone.setEstimatedDuration(t.getEstimatedDuration());
            clone.setPlannedStartDate(t.getPlannedStartDate());
            clone.setPlannedFinishDate(t.getPlannedFinishDate());
            clone.setPriority(t.getPriority());
            clone.setStatus(t.getStatus());
            clone.setRequiredSkills(t.getRequiredSkills());
            clone.setProject(project);
            simTaskMap.put(clone.getId(), clone);
            simTasks.add(clone);
        }

        // Apply any candidate-specific planned start date shifts
        for (Map.Entry<Long, LocalDate> shift : candidate.getProposedTaskStartDates().entrySet()) {
            Task t = simTaskMap.get(shift.getKey());
            if (t != null) {
                t.setPlannedStartDate(shift.getValue());
                int dur = t.getEstimatedDuration() != null ? t.getEstimatedDuration() : 1;
                t.setPlannedFinishDate(workingDayCalculator.calculateFinishDate(shift.getValue(), dur));
            }
        }

        // 2. Clone dependencies with simulated tasks
        List<TaskDependency> simDependencies = new ArrayList<>();
        for (TaskDependency d : dependencies) {
            Task pred = simTaskMap.get(d.getPredecessorTask().getId());
            Task succ = simTaskMap.get(d.getSuccessorTask().getId());
            if (pred != null && succ != null) {
                TaskDependency simDep = new TaskDependency();
                simDep.setId(d.getId());
                simDep.setPredecessorTask(pred);
                simDep.setSuccessorTask(succ);
                simDep.setDependencyType(d.getDependencyType());
                simDependencies.add(simDep);
            }
        }

        // 3. Recalculate schedule with authoritative SchedulingEngine
        Project simProject = new Project();
        simProject.setId(project.getId());
        simProject.setName(project.getName());
        simProject.setStartDate(project.getStartDate());
        simProject.setDeadline(project.getDeadline());

        schedulingEngine.calculateSchedule(simProject, simTasks, simDependencies);

        LocalDate newCompletion = simProject.getExpectedCompletionDate() != null
                ? simProject.getExpectedCompletionDate()
                : (currentForecastCompletion != null ? currentForecastCompletion : LocalDate.now());
        candidate.setProjectCompletion(newCompletion);

        // 4. Calculate working days saved vs current forecast
        int daysSaved = 0;
        if (currentForecastCompletion != null) {
            if (newCompletion.isBefore(currentForecastCompletion)) {
                daysSaved = workingDayCalculator.calculateWorkingDaysBetween(newCompletion, currentForecastCompletion);
            } else if (newCompletion.isAfter(currentForecastCompletion)) {
                daysSaved = -workingDayCalculator.calculateWorkingDaysBetween(currentForecastCompletion, newCompletion);
            }
        }
        candidate.setWorkingDaysSaved(daysSaved);

        // 5. Evaluate Deadline Status
        LocalDate deadline = project.getDeadline();
        if (deadline != null) {
            if (newCompletion.isAfter(deadline)) {
                candidate.setDeadlineStatus("DELAYED");
            } else {
                int slackToDeadline = workingDayCalculator.calculateWorkingDaysBetween(newCompletion, deadline);
                if (slackToDeadline <= 1) {
                    candidate.setDeadlineStatus("AT_RISK");
                } else {
                    candidate.setDeadlineStatus("ON_TRACK");
                }
            }
        } else {
            candidate.setDeadlineStatus("ON_TRACK");
        }

        // 6. Simulate global assignments and detect remaining conflicts
        List<TaskAssignment> simGlobalAssignments = new ArrayList<>();
        for (TaskAssignment a : allSystemAssignments) {
            Task taskForAssignment = a.getTask();
            // If assignment belongs to this project's tasks, use simTask
            if (simTaskMap.containsKey(taskForAssignment.getId())) {
                taskForAssignment = simTaskMap.get(taskForAssignment.getId());
            }

            TaskAssignment simA = new TaskAssignment();
            simA.setId(a.getId());
            simA.setTask(taskForAssignment);
            simA.setWorker(a.getWorker());
            simA.setVendor(a.getVendor());
            simA.setAssignmentType(a.getAssignmentType());
            simA.setAllocationPercentage(a.getAllocationPercentage());

            // Check if candidate reassigns this task
            Long taskId = taskForAssignment.getId();
            if (candidate.getProposedWorkerReassignments().containsKey(taskId)) {
                Long newWorkerId = candidate.getProposedWorkerReassignments().get(taskId);
                Worker w = new Worker();
                w.setId(newWorkerId);
                w.setName("Worker #" + newWorkerId);
                simA.setWorker(w);
                simA.setAssignmentType(AssignmentType.WORKER);
            }
            if (candidate.getProposedVendorSubstitutions().containsKey(taskId)) {
                Long newVendorId = candidate.getProposedVendorSubstitutions().get(taskId);
                Vendor v = new Vendor();
                v.setId(newVendorId);
                v.setName("Vendor #" + newVendorId);
                simA.setVendor(v);
                simA.setAssignmentType(AssignmentType.VENDOR);
            }

            simGlobalAssignments.add(simA);
        }

        List<Task> allGlobalTasks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (TaskAssignment a : simGlobalAssignments) {
            if (seen.add(a.getTask().getId())) {
                allGlobalTasks.add(a.getTask());
            }
        }

        List<ResourceConflict> remainingConflictList = conflictDetector.detectConflicts(allGlobalTasks, simGlobalAssignments);
        int remaining = remainingConflictList.size();
        candidate.setRemainingConflicts(remaining);
        candidate.setConflictsResolved(Math.max(0, initialConflictCount - remaining));

        // 7. Calculate Disruption Count
        int disruption = candidate.getProposedWorkerReassignments().size()
                + candidate.getProposedVendorSubstitutions().size()
                + candidate.getProposedTaskStartDates().size();
        candidate.setDisruptionCount(disruption);

        // 8. Compute 5-factor transparent objective score
        // 40% Deadline score
        double sDeadline = 100.0;
        if (deadline != null && newCompletion.isAfter(deadline)) {
            int overDays = workingDayCalculator.calculateWorkingDaysBetween(deadline, newCompletion);
            sDeadline = Math.max(0.0, 100.0 - (overDays * 15.0));
        }

        // 25% Time improvement score
        double sTime = Math.min(100.0, Math.max(0.0, 70.0 + (daysSaved * 10.0)));

        // 15% Conflicts score
        double sConflicts = remaining == 0 ? 100.0 : Math.max(0.0, 100.0 - (remaining * 35.0));

        // 10% Disruption score
        double sDisruption = Math.max(0.0, 100.0 - (disruption * 15.0));

        // 10% Workload balance score
        double sBalance = 85.0; // Base healthy workload balance

        double totalScore = (0.40 * sDeadline) + (0.25 * sTime) + (0.15 * sConflicts) + (0.10 * sDisruption) + (0.10 * sBalance);
        candidate.setObjectiveScore(Math.round(totalScore * 10.0) / 10.0);
    }
}
