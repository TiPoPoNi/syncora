package com.prs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClientDto {
    private Long id;
    private String name;
    private String company;
    private String email;
    private String phone;
    private String address;
    private String status;
    private Long projectCount;
    private Long activeProjectCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
