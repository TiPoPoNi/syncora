package com.prs.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceConflict {
    private String resource;
    private String conflictingTask1;
    private String conflictingTask2;
    private LocalDate overlapStart;
    private LocalDate overlapEnd;
}
