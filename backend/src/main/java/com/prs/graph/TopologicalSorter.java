package com.prs.graph;


import java.util.*;

public class TopologicalSorter {
    
    public List<TaskNode> sort(DependencyGraph graph) {
        Map<Long, TaskNode> nodes = graph.getNodes();
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
        
        List<TaskNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            TaskNode current = queue.poll();
            sorted.add(current);
            for (TaskNode successor : current.getSuccessors()) {
                long succId = successor.getTask().getId();
                inDegree.put(succId, inDegree.get(succId) - 1);
                if (inDegree.get(succId) == 0) {
                    queue.add(successor);
                }
            }
        }
        
        if (sorted.size() != nodes.size()) {
            throw new IllegalStateException("Graph contains a cycle");
        }
        
        return sorted;
    }
}
