package com.prs.controller;

import com.prs.dto.NotificationDto;
import com.prs.dto.NotificationPreferencesDto;
import com.prs.dto.UnreadCountDto;
import com.prs.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor

public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/api/notifications")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            Authentication authentication,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(notificationService.getUserNotifications(username, unreadOnly));
    }

    @GetMapping("/api/notifications/unread-count")
    public ResponseEntity<UnreadCountDto> getUnreadCount(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(new UnreadCountDto(notificationService.getUnreadCount(username)));
    }

    @PatchMapping("/api/notifications/{notificationId}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(notificationService.markAsRead(notificationId, username));
    }

    @PatchMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        notificationService.markAllAsRead(username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/users/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesDto> getPreferences(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(notificationService.getPreferences(username));
    }

    @PutMapping("/api/users/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesDto> updatePreferences(
            @RequestBody NotificationPreferencesDto dto,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "admin";
        return ResponseEntity.ok(notificationService.updatePreferences(username, dto));
    }
}
