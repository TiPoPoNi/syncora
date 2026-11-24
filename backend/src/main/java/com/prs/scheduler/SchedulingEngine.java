package com.prs.scheduler;

import com.prs.entity.Project;
import com.prs.entity.Task;
import com.prs.entity.TaskDependency;
import com.prs.graph.DependencyGraph;
import com.prs.graph.TaskNode;
import com.prs.graph.TopologicalSorter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchedulingEngine {
    
    private final WorkingDayCalculator workingDayCalculator;
    
    public void calculateSchedule(Project project, List<Task> tasks, List<TaskDependency> dependencies) {
        DependencyGraph graph = new DependencyGraph();
        graph.buildGraph(tasks, dependencies);
        
        if (graph.hasCycle()) {
            throw new IllegalStateException("Cannot schedule tasks: Circular dependency detected in the project.");
        }
        
        TopologicalSorter sorter = new TopologicalSorter();
        List<TaskNode> sortedNodes = sorter.sort(graph);
        
        LocalDate projectStart = project.getStartDate() != null ? project.getStartDate() : LocalDate.now();
        
        for (TaskNode node : sortedNodes) {
            Task task = node.getTask();
            LocalDate earliestStart = projectStart;
            
            // Check all predecessors
            for (TaskNode predecessor : node.getPredecessors()) {
                Task predTask = predecessor.getTask();
                if (predTask.getPlannedFinishDate() != null) {
                    LocalDate predFinish = predTask.getPlannedFinishDate();
                    LocalDate nextAvailableStart = workingDayCalculator.getNextWorkingDay(predFinish);
                    if (nextAvailableStart.isAfter(earliestStart)) {
                        earliestStart = nextAvailableStart;
                    }
                }
            }
            
            // Ensure start date is a working day
            if (!workingDayCalculator.isWorkingDay(earliestStart)) {
                earliestStart = workingDayCalculator.getNextWorkingDay(earliestStart);
            }
            
            task.setPlannedStartDate(earliestStart);
            
            int duration = task.getEstimatedDuration() != null ? task.getEstimatedDuration() : 1;
            LocalDate finishDate = workingDayCalculator.calculateFinishDate(earliestStart, duration);
            task.setPlannedFinishDate(finishDate);
        }
        
        // Calculate project completion date (max finish date of all tasks)
        LocalDate projectCompletion = projectStart;
        for (Task task : tasks) {
            if (task.getPlannedFinishDate() != null && task.getPlannedFinishDate().isAfter(projectCompletion)) {
                projectCompletion = task.getPlannedFinishDate();
            }
        }
        project.setExpectedCompletionDate(projectCompletion);
    }
}
