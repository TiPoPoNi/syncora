package com.prs.security;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Standardized Password Policy Validator for SYNCORA.
 * Enforces:
 * - Minimum length: 12 characters
 * - Maximum length: 128 characters
 * - Rejection of common, compromised, and guessable passwords
 * - Prevention of username inclusion in passwords
 */
@Component
public class PasswordPolicyValidator {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234",
            "password12345",
            "123456789012",
            "1234567890123",
            "qwerty123456",
            "admin1234567",
            "welcome12345",
            "syncora12345",
            "letmein12345",
            "iloveyou1234",
            "administrator123"
    );

    public void validate(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters in length.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Password cannot exceed " + MAX_LENGTH + " characters.");
        }
        String lower = password.toLowerCase();
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new IllegalArgumentException("Password is too common or easily guessable. Please choose a stronger passphrase.");
        }
        if (username != null && !username.isBlank()) {
            String u = username.trim().toLowerCase();
            if (lower.equals(u) || (u.length() >= 4 && lower.contains(u))) {
                throw new IllegalArgumentException("Password cannot contain your username.");
            }
        }
    }

    public void validatePassword(String password, String confirmPassword, String username) {
        if (confirmPassword != null && !confirmPassword.equals(password)) {
            throw new IllegalArgumentException("Passwords do not match. Please re-enter.");
        }
        validate(password, username);
    }
}
