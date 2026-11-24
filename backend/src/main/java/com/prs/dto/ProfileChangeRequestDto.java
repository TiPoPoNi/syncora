package com.prs.dto;

import com.prs.entity.ProfileChangeRequest;
import com.prs.entity.ProfileChangeRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChangeRequestDto {
    private Long id;
    private Long userId;
    private String username;
    private String employeeName;
    private String employeeId;
    private Long organizationId;
    private String organizationName;

    private String proposedFullName;
    private String proposedPhone;
    private String proposedJobTitle;
    private String proposedDepartment;
    private String proposedWorkEmail;
    private String proposedAvatarUrl;

    private String existingFullName;
    private String existingPhone;
    private String existingJobTitle;
    private String existingDepartment;
    private String existingWorkEmail;
    private String existingAvatarUrl;

    private ProfileChangeRequestStatus status;
    private Boolean emailVerified;
    private String submissionComment;
    private String reviewComment;

    private Long reviewedByUserId;
    private String reviewedByUsername;

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public static ProfileChangeRequestDto fromEntity(ProfileChangeRequest entity, String employeeId) {
        String empName = entity.getUser() != null ? 
                (entity.getUser().getFullName() != null && !entity.getUser().getFullName().isBlank() 
                        ? entity.getUser().getFullName() 
                        : entity.getUser().getUsername()) 
                : "";

        return ProfileChangeRequestDto.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .username(entity.getUser() != null ? entity.getUser().getUsername() : null)
                .employeeName(empName)
                .employeeId(employeeId)
                .organizationId(entity.getOrganization() != null ? entity.getOrganization().getId() : null)
                .organizationName(entity.getOrganization() != null ? entity.getOrganization().getName() : null)
                .proposedFullName(entity.getProposedFullName())
                .proposedPhone(entity.getProposedPhone())
                .proposedJobTitle(entity.getProposedJobTitle())
                .proposedDepartment(entity.getProposedDepartment())
                .proposedWorkEmail(entity.getProposedWorkEmail())
                .proposedAvatarUrl(entity.getProposedAvatarUrl())
                .existingFullName(entity.getExistingFullName())
                .existingPhone(entity.getExistingPhone())
                .existingJobTitle(entity.getExistingJobTitle())
                .existingDepartment(entity.getExistingDepartment())
                .existingWorkEmail(entity.getExistingWorkEmail())
                .existingAvatarUrl(entity.getExistingAvatarUrl())
                .status(entity.getStatus())
                .emailVerified(entity.getEmailVerified())
                .submissionComment(entity.getSubmissionComment())
                .reviewComment(entity.getReviewComment())
                .reviewedByUserId(entity.getReviewedBy() != null ? entity.getReviewedBy().getId() : null)
                .reviewedByUsername(entity.getReviewedBy() != null ? entity.getReviewedBy().getUsername() : null)
                .createdAt(entity.getCreatedAt())
                .reviewedAt(entity.getReviewedAt())
                .build();
    }
}
