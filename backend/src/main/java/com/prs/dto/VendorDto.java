package com.prs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VendorDto {
    private Long id;
    private String name;
    private String company;
    private String email;
    private String phone;
    private String services;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
