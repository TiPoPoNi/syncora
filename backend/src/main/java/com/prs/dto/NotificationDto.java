package com.prs.dto;

import com.prs.entity.Notification;
import com.prs.entity.NotificationDeliveryChannel;
import com.prs.entity.NotificationDeliveryStatus;
import com.prs.entity.NotificationEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private NotificationEventType eventType;
    private String title;
    private String message;
    private Long relatedProjectId;
    private Long relatedTaskId;
    private Boolean isRead;
    private NotificationDeliveryChannel deliveryChannel;
    private NotificationDeliveryStatus deliveryStatus;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public static NotificationDto fromEntity(Notification entity) {
        return NotificationDto.builder()
                .id(entity.getId())
                .eventType(entity.getEventType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .relatedProjectId(entity.getRelatedProjectId())
                .relatedTaskId(entity.getRelatedTaskId())
                .isRead(entity.getIsRead())
                .deliveryChannel(entity.getDeliveryChannel())
                .deliveryStatus(entity.getDeliveryStatus())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }
}
