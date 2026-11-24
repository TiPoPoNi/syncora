package com.prs.controller;

import com.prs.dto.*;
import com.prs.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor

public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> getUserOrganizations(Authentication authentication) {
        return ResponseEntity.ok(organizationService.getUserOrganizations(authentication.getName()));
    }

    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationDto> getOrganization(@PathVariable Long orgId, Authentication authentication) {
        return ResponseEntity.ok(organizationService.getOrganization(orgId, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<OrganizationDto> createOrganization(
            @RequestBody CreateOrganizationRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request, authentication.getName()));
    }

    @PutMapping("/{orgId}")
    public ResponseEntity<OrganizationDto> updateOrganization(
            @PathVariable Long orgId,
            @RequestBody CreateOrganizationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.updateOrganization(orgId, request, authentication.getName()));
    }

    @PostMapping("/{orgId}/status")
    public ResponseEntity<OrganizationDto> setOrganizationStatus(
            @PathVariable Long orgId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String status = body.getOrDefault("status", "ACTIVE");
        return ResponseEntity.ok(organizationService.setOrganizationStatus(orgId, status, authentication.getName()));
    }

    @GetMapping("/{orgId}/members")
    public ResponseEntity<List<OrganizationMemberDto>> getMembers(
            @PathVariable Long orgId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invitationStatus,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.getMembers(orgId, authentication.getName(), search, role, status, invitationStatus));
    }

    @PostMapping("/{orgId}/members/invite")
    public ResponseEntity<OrganizationMemberDto> inviteMember(
            @PathVariable Long orgId,
            @RequestBody InviteMemberRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.inviteMember(orgId, request, authentication.getName()));
    }

    @PostMapping("/{orgId}/members/{membershipId}/resend-invite")
    public ResponseEntity<OrganizationMemberDto> resendInvitation(
            @PathVariable Long orgId,
            @PathVariable Long membershipId,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.resendInvitation(orgId, membershipId, authentication.getName()));
    }

    @PostMapping("/{orgId}/members/{membershipId}/revoke-invite")
    public ResponseEntity<OrganizationMemberDto> revokeInvitation(
            @PathVariable Long orgId,
            @PathVariable Long membershipId,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.revokeInvitation(orgId, membershipId, authentication.getName()));
    }

    @PostMapping("/{orgId}/members")
    public ResponseEntity<OrganizationMemberDto> addMember(
            @PathVariable Long orgId,
            @RequestBody AddMemberRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.addMember(orgId, request, authentication.getName()));
    }

    @PutMapping("/{orgId}/members/{membershipId}")
    public ResponseEntity<OrganizationMemberDto> updateMember(
            @PathVariable Long orgId,
            @PathVariable Long membershipId,
            @RequestBody UpdateMemberRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.updateMember(orgId, membershipId, request, authentication.getName()));
    }

    @DeleteMapping("/{orgId}/members/{membershipId}")
    public ResponseEntity<Void> deleteMember(
            @PathVariable Long orgId,
            @PathVariable Long membershipId,
            Authentication authentication) {
        organizationService.deleteMember(orgId, membershipId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
