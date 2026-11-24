package com.prs.graph;

import com.prs.entity.Task;
import com.prs.entity.TaskDependency;

import java.util.*;

public class DependencyGraph {
    
    private final Map<Long, TaskNode> nodes = new HashMap<>();
    
    public void buildGraph(List<Task> tasks, List<TaskDependency> dependencies) {
        nodes.clear();
        for (Task task : tasks) {
            nodes.put(task.getId(), new TaskNode(task));
        }
        
        for (TaskDependency dep : dependencies) {
            TaskNode predecessor = nodes.get(dep.getPredecessorTask().getId());
            TaskNode successor = nodes.get(dep.getSuccessorTask().getId());
            if (predecessor != null && successor != null) {
                predecessor.addSuccessor(successor);
            }
        }
    }
    
    public Map<Long, TaskNode> getNodes() {
        return nodes;
    }
    
    /**
     * Checks if adding a specific dependency would create a cycle.
     */
    public boolean wouldCreateCycle(Long predId, Long succId) {
        if (predId.equals(succId)) return true;
        
        TaskNode pred = nodes.get(predId);
        TaskNode succ = nodes.get(succId);
        
        if (pred == null || succ == null) return false;
        
        // Use BFS/DFS to check if pred is reachable from succ
        // If succ -> ... -> pred, then adding pred -> succ creates a cycle
        Set<Long> visited = new HashSet<>();
        Queue<TaskNode> queue = new LinkedList<>();
        queue.add(succ);
        visited.add(succ.getTask().getId());
        
        while (!queue.isEmpty()) {
            TaskNode current = queue.poll();
            if (current.getTask().getId().equals(predId)) {
                return true;
            }
            for (TaskNode child : current.getSuccessors()) {
                if (!visited.contains(child.getTask().getId())) {
                    visited.add(child.getTask().getId());
                    queue.add(child);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Returns true if the entire graph has a cycle.
     */
    public boolean hasCycle() {
        Map<Long, Integer> inDegree = new HashMap<>();
        for (Long id : nodes.keySet()) {
            inDegree.put(id, nodes.get(id).getPredecessors().size());
        }
        
        Queue<TaskNode> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(nodes.get(entry.getKey()));
            }
        }
        
        int count = 0;
        while (!queue.isEmpty()) {
            TaskNode current = queue.poll();
            count++;
            for (TaskNode successor : current.getSuccessors()) {
                long succId = successor.getTask().getId();
                inDegree.put(succId, inDegree.get(succId) - 1);
                if (inDegree.get(succId) == 0) {
                    queue.add(successor);
                }
            }
        }
        
        return count != nodes.size();
    }
}
