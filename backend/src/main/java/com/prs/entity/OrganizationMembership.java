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
@Table(name = "organization_memberships", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "organization_id"})
})
public class OrganizationMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 30)
    private String role; // 'ORGANIZATION_OWNER', 'ORGANIZATION_ADMIN', 'PROJECT_MANAGER', 'CLIENT', 'WORKER', 'VENDOR'

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // 'ACTIVE', 'SUSPENDED'

    @Column(name = "employee_id", length = 50)
    private String employeeId;

    @Column(name = "invitation_token", length = 255)
    private String invitationToken;

    @Column(name = "invitation_status", length = 20)
    private String invitationStatus = "ACCEPTED"; // 'PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'

    @Column(name = "invitation_expires_at")
    private LocalDateTime invitationExpiresAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (invitationStatus == null) invitationStatus = "ACCEPTED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
