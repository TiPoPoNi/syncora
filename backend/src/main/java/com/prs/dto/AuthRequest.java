package com.prs.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String email;
    private String employeeId;
    private String organizationCode;
    private String password;
    private Boolean rememberMe;
}
