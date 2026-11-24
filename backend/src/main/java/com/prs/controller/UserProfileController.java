package com.prs.controller;

import com.prs.dto.ProfileChangeRequestDto;
import com.prs.dto.ReviewProfileChangeRequest;
import com.prs.dto.SubmitProfileChangeRequest;
import com.prs.dto.UserProfileDto;
import com.prs.entity.ProfileChangeRequestStatus;
import com.prs.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor

public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/api/users/me/profile")
    public ResponseEntity<UserProfileDto> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(userProfileService.getUserProfile(authentication.getName()));
    }

    @PostMapping("/api/users/me/profile/change-requests")
    public ResponseEntity<ProfileChangeRequestDto> submitProfileChangeRequest(
            @Valid @RequestBody SubmitProfileChangeRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userProfileService.submitProfileChangeRequest(authentication.getName(), request));
    }

    @GetMapping("/api/users/me/profile/change-requests")
    public ResponseEntity<List<ProfileChangeRequestDto>> getMyChangeRequests(Authentication authentication) {
        return ResponseEntity.ok(userProfileService.getUserChangeRequests(authentication.getName()));
    }

    @GetMapping("/api/organizations/{orgId}/profile-change-requests")
    public ResponseEntity<List<ProfileChangeRequestDto>> getOrganizationProfileChangeRequests(
            @PathVariable Long orgId,
            @RequestParam(required = false) ProfileChangeRequestStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(userProfileService.getOrganizationChangeRequests(orgId, authentication.getName(), status));
    }

    @PostMapping("/api/profile-change-requests/{requestId}/approve")
    public ResponseEntity<ProfileChangeRequestDto> approveRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReviewProfileChangeRequest reviewReq,
            Authentication authentication) {
        return ResponseEntity.ok(userProfileService.approveChangeRequest(requestId, authentication.getName(), reviewReq));
    }

    @PostMapping("/api/profile-change-requests/{requestId}/reject")
    public ResponseEntity<ProfileChangeRequestDto> rejectRequest(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReviewProfileChangeRequest reviewReq,
            Authentication authentication) {
        return ResponseEntity.ok(userProfileService.rejectChangeRequest(requestId, authentication.getName(), reviewReq));
    }

    @PostMapping("/api/profile-change-requests/{requestId}/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {
        String token = body.get("token");
        boolean ok = userProfileService.verifyEmailToken(requestId, token);
        return ResponseEntity.ok(Map.of("success", ok, "message", "Email verification successful."));
    }
}
