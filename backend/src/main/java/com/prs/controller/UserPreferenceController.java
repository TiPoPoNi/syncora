package com.prs.controller;

import com.prs.dto.UserPreferencesDto;
import com.prs.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/users/me/preferences")
@RequiredArgsConstructor

public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public ResponseEntity<UserPreferencesDto> getPreferences(Authentication authentication) {
        return ResponseEntity.ok(userPreferenceService.getPreferences(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UserPreferencesDto> updatePreferences(
            @RequestBody UserPreferencesDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(userPreferenceService.updatePreferences(authentication.getName(), dto));
    }

    @PostMapping("/reset")
    public ResponseEntity<UserPreferencesDto> resetPreferences(Authentication authentication) {
        return ResponseEntity.ok(userPreferenceService.restoreDefaults(authentication.getName()));
    }
}
