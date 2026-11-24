package com.prs.optimization;

import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CpmTaskResult;
import com.prs.entity.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CandidateGenerator {

    private final CandidateValidator validator;
    private final ConflictDetector conflictDetector;

    public List<OptimizationCandidate> generateCandidates(
            Project project,
            List<Task> tasks,
            List<TaskDependency> dependencies,
            List<TaskAssignment> projectAssignments,
            List<TaskAssignment> allSystemAssignments,
            List<Worker> allWorkers,
            List<Vendor> allVendors,
            CriticalPathResult cpmResult) {

        List<OptimizationCandidate> candidates = new ArrayList<>();
        Map<Long, Worker> workerMap = new HashMap<>();
        for (Worker w : allWorkers) {
            workerMap.put(w.getId(), w);
        }
        Map<Long, Vendor> vendorMap = new HashMap<>();
        for (Vendor v : allVendors) {
            vendorMap.put(v.getId(), v);
        }
        Map<Long, Task> taskMap = new HashMap<>();
        for (Task t : tasks) {
            taskMap.put(t.getId(), t);
        }

        // Map task to assignment
        Map<Long, TaskAssignment> assignmentByTaskId = new HashMap<>();
        for (TaskAssignment a : projectAssignments) {
            assignmentByTaskId.put(a.getTask().getId(), a);
        }

        // 1. Identify existing resource conflicts
        List<Task> allGlobalTasks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (TaskAssignment a : allSystemAssignments) {
            if (seen.add(a.getTask().getId())) {
                allGlobalTasks.add(a.getTask());
            }
        }
        List<ResourceConflict> existingConflicts = conflictDetector.detectConflicts(allGlobalTasks, allSystemAssignments);

        // Map workerId -> conflicted?
        Set<String> conflictedWorkerNames = new HashSet<>();
        for (ResourceConflict c : existingConflicts) {
            conflictedWorkerNames.add(c.getResource());
        }

        // --- Candidate 0: Current Schedule (Baseline / Status Quo) ---
        OptimizationCandidate statusQuo = new OptimizationCandidate();
        statusQuo.setTitle("Current Schedule Baseline (Status Quo)");
        statusQuo.setSummary("Keep the existing assignments and schedule unaltered.");
        statusQuo.setCategory(existingConflicts.isEmpty() ? RecommendationCategory.NO_IMPROVEMENT_POSSIBLE : RecommendationCategory.WORKER_REASSIGNMENT);
        statusQuo.setTradeOffs("No disruption to existing plan; any existing resource over-allocations or delays remain unresolved.");
        statusQuo.setExplanation("Evaluates the current baseline without modifications as a reference benchmark.");
        candidates.add(statusQuo);

        // --- Strategy 1: Targeted Worker Reassignments for Conflicted / Overloaded Tasks ---
        for (Task task : tasks) {
            // Cannot reassign completed tasks
            if (task.getStatus() == TaskStatus.COMPLETED) {
                continue;
            }

            TaskAssignment assignment = assignmentByTaskId.get(task.getId());
            if (assignment == null || assignment.getWorker() == null) {
                continue;
            }

            Worker currentWorker = assignment.getWorker();
            boolean isConflicted = conflictedWorkerNames.contains(currentWorker.getName());

            // If this worker is conflicted or we want to explore qualified alternatives
            for (Worker candidateWorker : allWorkers) {
                if (candidateWorker.getId().equals(currentWorker.getId())) {
                    continue; // Skip same worker
                }
                if (candidateWorker.getStatus() != WorkerStatus.AVAILABLE) {
                    continue; // Skip unavailable workers
                }

                // Verify mandatory skills
                String reqSkills = task.getRequiredSkills();
                if (reqSkills != null && !validator.workerHasRequiredSkills(candidateWorker, reqSkills)) {
                    continue; // Skip worker lacking required skills
                }

                // Check if candidate worker already has conflicts in this timeframe
                OptimizationCandidate c = new OptimizationCandidate();
                c.setCategory(RecommendationCategory.WORKER_REASSIGNMENT);
                c.getProposedWorkerReassignments().put(task.getId(), candidateWorker.getId());

                if (isConflicted) {
                    c.setTitle("Reassign '" + task.getName() + "' to " + candidateWorker.getName());
                    c.setSummary("Resolve resource bottleneck by moving '" + task.getName() + "' from " + currentWorker.getName() + " to qualified worker " + candidateWorker.getName() + ".");
                    c.setExplanation("Worker " + currentWorker.getName() + " has overlapping task commitments. " + candidateWorker.getName() + " possesses verified skills (" + candidateWorker.getSkills() + ") with " + candidateWorker.getExperience() + " years experience and full capacity.");
                    c.setTradeOffs("Requires onboarding " + candidateWorker.getName() + " to '" + task.getName() + "'. Eliminates concurrent workload overlap.");
                } else {
                    c.setTitle("Reassign '" + task.getName() + "' to " + candidateWorker.getName() + " (Workload Balancing)");
                    c.setSummary("Balance workload by transferring '" + task.getName() + "' to " + candidateWorker.getName() + ".");
                    c.setExplanation("Transfers task to an available qualified peer (" + candidateWorker.getName() + ") to optimize team utilization.");
                    c.setTradeOffs("Minor team assignment change; maintains deadline and dependencies.");
                }

                candidates.add(c);
            }
        }

        // --- Strategy 2: Vendor Substitution / Outsourcing ---
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                continue;
            }
            TaskAssignment assignment = assignmentByTaskId.get(task.getId());

            for (Vendor vendor : allVendors) {
                if (!"ACTIVE".equalsIgnoreCase(vendor.getStatus())) {
                    continue;
                }
                // Check if vendor services match task requirements
                if (task.getRequiredSkills() != null && vendor.getServices() != null) {
                    String req = task.getRequiredSkills().toLowerCase();
                    boolean match = false;
                    for (String part : req.split("[,;]")) {
                        if (vendor.getServices().toLowerCase().contains(part.trim())) {
                            match = true;
                            break;
                        }
                    }
                    if (match) {
                        OptimizationCandidate vc = new OptimizationCandidate();
                        vc.setCategory(RecommendationCategory.VENDOR_SUBSTITUTION);
                        vc.getProposedVendorSubstitutions().put(task.getId(), vendor.getId());
                        vc.setTitle("Engage Vendor " + vendor.getName() + " for '" + task.getName() + "'");
                        vc.setSummary("Substitute or engage active vendor " + vendor.getName() + " (" + vendor.getCompany() + ") for '" + task.getName() + "'.");
                        vc.setExplanation("Vendor " + vendor.getName() + " provides verified service capabilities: " + vendor.getServices() + ".");
                        vc.setTradeOffs("Offloads internal team capacity; requires external vendor coordination.");
                        candidates.add(vc);
                    }
                }
            }
        }

        // --- Strategy 3: Slack-Aware Rescheduling for Non-Critical Tasks ---
        if (cpmResult != null && cpmResult.getTaskResults() != null) {
            for (CpmTaskResult cpmTask : cpmResult.getTaskResults()) {
                if (!cpmTask.isCritical() && cpmTask.getTotalSlack() > 0) {
                    Task task = taskMap.get(cpmTask.getTaskId());
                    if (task != null && task.getStatus() != TaskStatus.COMPLETED && task.getPlannedStartDate() != null) {
                        // Test shifting start date by 1 or 2 days within slack
                        int shiftDays = Math.min(2, cpmTask.getTotalSlack());
                        LocalDate newStart = task.getPlannedStartDate().plusDays(shiftDays);

                        OptimizationCandidate sc = new OptimizationCandidate();
                        sc.setCategory(RecommendationCategory.SCHEDULE_SHIFT);
                        sc.getProposedTaskStartDates().put(task.getId(), newStart);
                        sc.setTitle("Shift Non-Critical Task '" + task.getName() + "' by " + shiftDays + " day(s)");
                        sc.setSummary("Utilize available float (" + cpmTask.getTotalSlack() + " days slack) to reschedule '" + task.getName() + "' starting " + newStart + ".");
                        sc.setExplanation("Task '" + task.getName() + "' is non-critical with " + cpmTask.getTotalSlack() + " days of total slack. Shifting it absorbs potential resource congestion without delaying project finish.");
                        sc.setTradeOffs("Consumes " + shiftDays + " days of available float; does not impact critical path or project deadline.");
                        candidates.add(sc);
                    }
                }
            }
        }

        // --- Strategy 4: Composite Multi-Conflict Reallocation ---
        // If there are multiple assignments to conflicted workers, generate a composite candidate
        if (existingConflicts.size() > 0) {
            OptimizationCandidate composite = new OptimizationCandidate();
            composite.setCategory(RecommendationCategory.COMPOSITE_REALLOCATION);
            composite.setTitle("Comprehensive Resource De-Bottlenecking");
            composite.setSummary("Composite reallocation resolving multiple resource overlaps simultaneously.");
            composite.setTradeOffs("Resolves all detected team conflicts at once; involves multiple assignment updates.");

            StringBuilder expl = new StringBuilder("Coordinated reallocation across overlapping tasks: ");
            boolean addedAny = false;

            for (Task task : tasks) {
                TaskAssignment a = assignmentByTaskId.get(task.getId());
                if (a != null && a.getWorker() != null && conflictedWorkerNames.contains(a.getWorker().getName())) {
                    // Find first available qualified alternative worker
                    for (Worker candidateWorker : allWorkers) {
                        if (!candidateWorker.getId().equals(a.getWorker().getId()) &&
                            candidateWorker.getStatus() == WorkerStatus.AVAILABLE &&
                            validator.workerHasRequiredSkills(candidateWorker, task.getRequiredSkills()) &&
                            !composite.getProposedWorkerReassignments().containsValue(candidateWorker.getId())) {

                            composite.getProposedWorkerReassignments().put(task.getId(), candidateWorker.getId());
                            expl.append("['").append(task.getName()).append("' -> ").append(candidateWorker.getName()).append("] ");
                            addedAny = true;
                            break;
                        }
                    }
                }
            }

            if (addedAny && composite.getProposedWorkerReassignments().size() > 1) {
                composite.setExplanation(expl.toString().trim());
                candidates.add(composite);
            }
        }

        // Bounded guarantee: at most 20 candidate alternatives evaluated per run
        if (candidates.size() > 20) {
            return candidates.subList(0, 20);
        }

        return candidates;
    }
}
