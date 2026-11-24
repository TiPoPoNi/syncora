package com.prs.graph;

import com.prs.entity.Task;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TaskNode {
    private Task task;
    private List<TaskNode> predecessors = new ArrayList<>();
    private List<TaskNode> successors = new ArrayList<>();
    
    public TaskNode(Task task) {
        this.task = task;
    }
    
    public void addSuccessor(TaskNode successor) {
        this.successors.add(successor);
        successor.getPredecessors().add(this);
    }
}
