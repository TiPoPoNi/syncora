package com.prs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitProfileChangeRequest {
    @Size(max = 120, message = "Full name cannot exceed 120 characters")
    private String proposedFullName;

    @Size(max = 30, message = "Phone cannot exceed 30 characters")
    private String proposedPhone;

    @Size(max = 100, message = "Job title cannot exceed 100 characters")
    private String proposedJobTitle;

    @Size(max = 100, message = "Department cannot exceed 100 characters")
    private String proposedDepartment;

    @Email(message = "Work email must be a valid email address")
    @Size(max = 120, message = "Email cannot exceed 120 characters")
    private String proposedWorkEmail;

    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    private String proposedAvatarUrl;

    @Size(max = 500, message = "Submission comment cannot exceed 500 characters")
    private String submissionComment;
}
