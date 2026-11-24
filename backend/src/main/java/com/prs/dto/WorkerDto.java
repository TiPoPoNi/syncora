package com.prs.dto;

import com.prs.entity.WorkerStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkerDto {
    private Long id;
    private Long userId;
    private String name;
    private String email;
    private String skills;
    private Integer experience;
    private WorkerStatus status;
    private Integer activeTaskCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
