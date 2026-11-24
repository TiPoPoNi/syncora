package com.prs.service;

import com.prs.dto.UserPreferencesDto;
import com.prs.entity.*;
import com.prs.repository.UserPreferenceRepository;
import com.prs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional(readOnly = true)
    public UserPreferencesDto getPreferences(String username) {
        User user = getUserByUsername(username);
        UserPreference pref = userPreferenceRepository.findByUser(user)
                .orElse(null);

        return UserPreferencesDto.fromEntity(pref);
    }

    @Transactional
    public UserPreferencesDto updatePreferences(String username, UserPreferencesDto dto) {
        User user = getUserByUsername(username);
        UserPreference pref = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    UserPreference p = new UserPreference();
                    p.setUser(user);
                    return p;
                });

        if (dto.getTheme() != null && !dto.getTheme().isBlank()) {
            pref.setTheme(dto.getTheme().trim().toUpperCase());
        }

        if (dto.getLanguage() != null && !dto.getLanguage().isBlank()) {
            pref.setLanguage(dto.getLanguage().trim().toLowerCase());
        }

        if (dto.getTimezone() != null && !dto.getTimezone().isBlank()) {
            pref.setTimezone(dto.getTimezone().trim());
        }

        if (dto.getDateFormat() != null && !dto.getDateFormat().isBlank()) {
            pref.setDateFormat(dto.getDateFormat().trim());
        }

        if (dto.getFirstDayOfWeek() != null && !dto.getFirstDayOfWeek().isBlank()) {
            pref.setFirstDayOfWeek(dto.getFirstDayOfWeek().trim().toUpperCase());
        }

        if (dto.getTextSize() != null && !dto.getTextSize().isBlank()) {
            pref.setTextSize(dto.getTextSize().trim().toUpperCase());
        }

        if (dto.getDisplayDensity() != null && !dto.getDisplayDensity().isBlank()) {
            pref.setDisplayDensity(dto.getDisplayDensity().trim().toUpperCase());
        }

        if (dto.getReducedMotion() != null) {
            pref.setReducedMotion(dto.getReducedMotion());
        }

        if (dto.getHighContrastFocus() != null) {
            pref.setHighContrastFocus(dto.getHighContrastFocus());
        }

        if (dto.getInAppNotifications() != null) {
            pref.setInAppNotifications(dto.getInAppNotifications());
        }

        if (dto.getEmailNotifications() != null) {
            pref.setEmailNotifications(dto.getEmailNotifications());
        }

        if (dto.getApprovalNotifications() != null) {
            pref.setApprovalNotifications(dto.getApprovalNotifications());
        }

        if (dto.getTaskUpdatesNotifications() != null) {
            pref.setTaskUpdatesNotifications(dto.getTaskUpdatesNotifications());
        }

        if (dto.getDeadlineRemindersNotifications() != null) {
            pref.setDeadlineRemindersNotifications(dto.getDeadlineRemindersNotifications());
        }

        UserPreference saved = userPreferenceRepository.save(pref);
        log.info("Updated preferences for user '{}'", username);
        return UserPreferencesDto.fromEntity(saved);
    }

    @Transactional
    public UserPreferencesDto restoreDefaults(String username) {
        User user = getUserByUsername(username);
        UserPreference pref = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    UserPreference p = new UserPreference();
                    p.setUser(user);
                    return p;
                });

        pref.setTheme("SYSTEM");
        pref.setLanguage("en");
        pref.setTimezone("Asia/Kolkata");
        pref.setDateFormat("YYYY-MM-DD");
        pref.setFirstDayOfWeek("MONDAY");
        pref.setTextSize("DEFAULT");
        pref.setDisplayDensity("COMFORTABLE");
        pref.setReducedMotion(false);
        pref.setHighContrastFocus(false);
        pref.setInAppNotifications(true);
        pref.setEmailNotifications(false);
        pref.setApprovalNotifications(true);
        pref.setTaskUpdatesNotifications(true);
        pref.setDeadlineRemindersNotifications(true);

        UserPreference saved = userPreferenceRepository.save(pref);
        log.info("Restored default preferences for user '{}'", username);
        return UserPreferencesDto.fromEntity(saved);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
