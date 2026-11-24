package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "task_dependencies")
public class TaskDependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "predecessor_task_id", nullable = false)
    private Task predecessorTask;
    
    @ManyToOne
    @JoinColumn(name = "successor_task_id", nullable = false)
    private Task successorTask;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type")
    private DependencyType dependencyType = DependencyType.FINISH_TO_START;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
