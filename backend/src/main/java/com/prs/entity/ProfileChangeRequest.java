package com.prs.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "profile_change_requests")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileChangeRequestStatus status = ProfileChangeRequestStatus.PENDING;

    // Proposed changes
    @Column(name = "proposed_full_name", length = 100)
    private String proposedFullName;

    @Column(name = "proposed_phone", length = 30)
    private String proposedPhone;

    @Column(name = "proposed_job_title", length = 100)
    private String proposedJobTitle;

    @Column(name = "proposed_department", length = 100)
    private String proposedDepartment;

    @Column(name = "proposed_work_email", length = 100)
    private String proposedWorkEmail;

    @Column(name = "proposed_avatar_url", length = 255)
    private String proposedAvatarUrl;

    // Existing values at submission for comparative diff and concurrency/stale checking
    @Column(name = "existing_full_name", length = 100)
    private String existingFullName;

    @Column(name = "existing_phone", length = 30)
    private String existingPhone;

    @Column(name = "existing_job_title", length = 100)
    private String existingJobTitle;

    @Column(name = "existing_department", length = 100)
    private String existingDepartment;

    @Column(name = "existing_work_email", length = 100)
    private String existingWorkEmail;

    @Column(name = "existing_avatar_url", length = 255)
    private String existingAvatarUrl;

    @Column(name = "submission_comment", columnDefinition = "TEXT")
    private String submissionComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewedBy;

    @Column(name = "reviewer_name", length = 100)
    private String reviewerName;

    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewComment;

    public User getReviewer() {
        return reviewedBy;
    }

    public void setReviewer(User reviewer) {
        this.reviewedBy = reviewer;
    }

    public String getReviewerComment() {
        return reviewComment;
    }

    public void setReviewerComment(String reviewerComment) {
        this.reviewComment = reviewerComment;
    }

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // Work email verification token lifecycle
    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "email_verification_expires_at")
    private LocalDateTime emailVerificationExpiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ProfileChangeRequestStatus.PENDING;
        if (emailVerified == null) emailVerified = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
