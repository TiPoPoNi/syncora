package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "workers")
public class Worker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    private String name;
    private String email;
    private String skills;
    private Integer experience;
    
    @Enumerated(EnumType.STRING)
    private WorkerStatus status = WorkerStatus.AVAILABLE;
    
    @Column(name = "daily_capacity")
    private Integer dailyCapacity = 8;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;
    
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
