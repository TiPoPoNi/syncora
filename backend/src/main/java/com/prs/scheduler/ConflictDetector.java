package com.prs.scheduler;

import com.prs.entity.Task;
import com.prs.entity.TaskAssignment;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class ConflictDetector {

    public List<ResourceConflict> detectConflicts(List<Task> allTasks, List<TaskAssignment> allAssignments) {
        List<ResourceConflict> conflicts = new ArrayList<>();
        
        // Group assignments by worker
        Map<Long, List<TaskAssignment>> workerAssignments = new HashMap<>();
        for (TaskAssignment assignment : allAssignments) {
            if (assignment.getWorker() != null) {
                Long workerId = assignment.getWorker().getId();
                workerAssignments.computeIfAbsent(workerId, k -> new ArrayList<>()).add(assignment);
            }
        }
        
        // Detect overlaps for each worker
        for (Map.Entry<Long, List<TaskAssignment>> entry : workerAssignments.entrySet()) {
            List<TaskAssignment> assignments = entry.getValue();
            // Sort by planned start date
            assignments.sort(Comparator.comparing(a -> a.getTask().getPlannedStartDate() != null ? a.getTask().getPlannedStartDate() : LocalDate.MAX));
            
            for (int i = 0; i < assignments.size(); i++) {
                for (int j = i + 1; j < assignments.size(); j++) {
                    Task t1 = assignments.get(i).getTask();
                    Task t2 = assignments.get(j).getTask();
                    
                    if (t1.getPlannedStartDate() == null || t1.getPlannedFinishDate() == null ||
                        t2.getPlannedStartDate() == null || t2.getPlannedFinishDate() == null) {
                        continue; // Cannot determine overlap
                    }
                    
                    // Overlap condition: start1 <= finish2 AND start2 <= finish1
                    if (!t1.getPlannedStartDate().isAfter(t2.getPlannedFinishDate()) &&
                        !t2.getPlannedStartDate().isAfter(t1.getPlannedFinishDate())) {
                        
                        LocalDate overlapStart = t1.getPlannedStartDate().isAfter(t2.getPlannedStartDate()) ? t1.getPlannedStartDate() : t2.getPlannedStartDate();
                        LocalDate overlapEnd = t1.getPlannedFinishDate().isBefore(t2.getPlannedFinishDate()) ? t1.getPlannedFinishDate() : t2.getPlannedFinishDate();
                        
                        conflicts.add(new ResourceConflict(
                                assignments.get(i).getWorker().getName(),
                                t1.getName(),
                                t2.getName(),
                                overlapStart,
                                overlapEnd
                        ));
                    }
                }
            }
        }
        
        return conflicts;
    }
}
