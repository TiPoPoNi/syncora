package com.prs.optimization;

import com.prs.entity.*;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class CandidateValidator {

    private final WorkingDayCalculator workingDayCalculator;

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }

        public static ValidationResult valid() {
            return new ValidationResult(true, "VALID");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }

    public ValidationResult validate(
            OptimizationCandidate candidate,
            List<Task> tasks,
            List<TaskDependency> dependencies,
            Map<Long, Worker> workersById,
            Map<Long, Vendor> vendorsById) {

        Map<Long, Task> taskMap = new HashMap<>();
        for (Task t : tasks) {
            taskMap.put(t.getId(), t);
        }

        // 1. Hard constraint: Completed tasks cannot be modified
        for (Long taskId : candidate.getProposedWorkerReassignments().keySet()) {
            Task t = taskMap.get(taskId);
            if (t != null && t.getStatus() == TaskStatus.COMPLETED) {
                return ValidationResult.invalid("Completed task '" + t.getName() + "' cannot be reassigned.");
            }
        }
        for (Long taskId : candidate.getProposedTaskStartDates().keySet()) {
            Task t = taskMap.get(taskId);
            if (t != null && t.getStatus() == TaskStatus.COMPLETED) {
                return ValidationResult.invalid("Completed task '" + t.getName() + "' cannot be rescheduled.");
            }
        }

        // 2. Hard constraint: Worker availability & mandatory skills
        for (Map.Entry<Long, Long> entry : candidate.getProposedWorkerReassignments().entrySet()) {
            Long taskId = entry.getKey();
            Long workerId = entry.getValue();
            Task task = taskMap.get(taskId);
            Worker worker = workersById.get(workerId);

            if (worker == null) {
                return ValidationResult.invalid("Assigned worker ID " + workerId + " does not exist.");
            }

            if (worker.getStatus() != WorkerStatus.AVAILABLE) {
                return ValidationResult.invalid("Worker '" + worker.getName() + "' is not available (Status: " + worker.getStatus() + ").");
            }

            if (task != null && task.getRequiredSkills() != null && !task.getRequiredSkills().isBlank()) {
                if (!workerHasRequiredSkills(worker, task.getRequiredSkills())) {
                    return ValidationResult.invalid("Worker '" + worker.getName() + "' lacks mandatory skills for task '" + task.getName() + "' (Requires: " + task.getRequiredSkills() + ").");
                }
            }
        }

        // 3. Hard constraint: Vendor status and services
        for (Map.Entry<Long, Long> entry : candidate.getProposedVendorSubstitutions().entrySet()) {
            Long vendorId = entry.getValue();
            Vendor vendor = vendorsById.get(vendorId);
            if (vendor == null) {
                return ValidationResult.invalid("Assigned vendor ID " + vendorId + " does not exist.");
            }
            if (!"ACTIVE".equalsIgnoreCase(vendor.getStatus())) {
                return ValidationResult.invalid("Vendor '" + vendor.getName() + "' is not active.");
            }
        }

        // 4. Hard constraint: Precedence dependencies
        for (TaskDependency dep : dependencies) {
            Task pred = taskMap.get(dep.getPredecessorTask().getId());
            Task succ = taskMap.get(dep.getSuccessorTask().getId());
            if (pred != null && succ != null && pred.getPlannedFinishDate() != null && succ.getPlannedStartDate() != null) {
                // Check if candidate modifies start dates
                var succStart = candidate.getProposedTaskStartDates().getOrDefault(succ.getId(), succ.getPlannedStartDate());
                var predFinish = pred.getPlannedFinishDate();

                if (!succStart.isAfter(predFinish)) {
                    // Successor must start after predecessor finish
                    return ValidationResult.invalid("Precedence violation: Task '" + succ.getName() + "' start date cannot precede finish of '" + pred.getName() + "'.");
                }
            }
        }

        return ValidationResult.valid();
    }

    public boolean workerHasRequiredSkills(Worker worker, String requiredSkillsStr) {
        if (requiredSkillsStr == null || requiredSkillsStr.isBlank()) {
            return true;
        }
        if (worker.getSkills() == null || worker.getSkills().isBlank()) {
            return false;
        }

        String[] required = requiredSkillsStr.split("[,;]");
        String workerSkillsLower = worker.getSkills().toLowerCase();

        for (String req : required) {
            String trimmed = req.trim().toLowerCase();
            if (!trimmed.isEmpty() && !workerSkillsLower.contains(trimmed)) {
                return false;
            }
        }
        return true;
    }
}
