package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;
    
    private String name;
    private String description;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    private LocalDate deadline;
    
    @Column(name = "estimated_duration")
    private Integer estimatedDuration;
    
    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;
    
    @Column(name = "planned_finish_date")
    private LocalDate plannedFinishDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_finish_date")
    private LocalDate actualFinishDate;
    
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;
    
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.TODO;
    
    private Integer progress = 0;

    @Column(name = "required_skills")
    private String requiredSkills;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
