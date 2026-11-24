package com.prs.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_event_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private ExternalCalendarIntegration integration;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // e.g. "TASK", "MILESTONE", "DELIVERABLE"

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    @Column(name = "sync_hash", length = 64)
    private String syncHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 50)
    @Builder.Default
    private CalendarEventStatus syncStatus = CalendarEventStatus.SYNCED;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
