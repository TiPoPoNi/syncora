package com.prs.controller;

import com.prs.dto.*;
import com.prs.service.DeliverableManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor

public class DeliverableController {

    private final DeliverableManagementService deliverableService;

    private String getUsername(Authentication authentication) {
        return (authentication != null && authentication.getName() != null) ? authentication.getName() : "admin";
    }

    // ==========================================
    // 1. Deliverables CRUD
    // ==========================================

    @GetMapping("/api/projects/{projectId}/deliverables")
    public ResponseEntity<List<DeliverableDto>> getDeliverables(
            @PathVariable Long projectId,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.getProjectDeliverables(projectId, getUsername(authentication)));
    }

    @PostMapping("/api/projects/{projectId}/deliverables")
    public ResponseEntity<DeliverableDto> createDeliverable(
            @PathVariable Long projectId,
            @RequestBody CreateDeliverableRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliverableService.createDeliverable(projectId, req, getUsername(authentication)));
    }

    @GetMapping("/api/deliverables/{deliverableId}")
    public ResponseEntity<DeliverableDto> getDeliverable(
            @PathVariable Long deliverableId,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.getDeliverableById(deliverableId, getUsername(authentication)));
    }

    @PutMapping("/api/deliverables/{deliverableId}")
    public ResponseEntity<DeliverableDto> updateDeliverable(
            @PathVariable Long deliverableId,
            @RequestBody UpdateDeliverableRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.updateDeliverable(deliverableId, req, getUsername(authentication)));
    }

    // ==========================================
    // 2. Document Uploads, Versioning & Download
    // ==========================================

    @PostMapping(value = "/api/deliverables/{deliverableId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliverableDocumentDto> uploadDocument(
            @PathVariable Long deliverableId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "versionNotes", required = false) String versionNotes,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliverableService.uploadDocument(deliverableId, file, versionNotes, getUsername(authentication)));
    }

    @GetMapping({"/api/deliverables/{deliverableId}/documents", "/api/deliverables/{deliverableId}/versions"})
    public ResponseEntity<List<DeliverableDocumentDto>> getDocuments(
            @PathVariable Long deliverableId,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.getDeliverableDocuments(deliverableId, getUsername(authentication)));
    }

    @GetMapping("/api/deliverables/{deliverableId}/documents/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long deliverableId,
            @PathVariable Long docId,
            Authentication authentication) {
        Resource fileResource = deliverableService.downloadDocument(deliverableId, docId, getUsername(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResource.getFilename() + "\"")
                .body(fileResource);
    }

    // ==========================================
    // 3. Workflow: Submit, Review Assignment & Decisions
    // ==========================================

    @PostMapping("/api/deliverables/{deliverableId}/submit")
    public ResponseEntity<DeliverableDto> submitDeliverable(
            @PathVariable Long deliverableId,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.submitDeliverable(deliverableId, getUsername(authentication)));
    }

    @PostMapping("/api/deliverables/{deliverableId}/reviews")
    public ResponseEntity<DeliverableDto> assignReviewer(
            @PathVariable Long deliverableId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long reviewerId = Long.valueOf(body.get("reviewerId").toString());
        LocalDate decisionDate = null;
        if (body.containsKey("requestedDecisionDate") && body.get("requestedDecisionDate") != null) {
            decisionDate = LocalDate.parse(body.get("requestedDecisionDate").toString());
        }
        return ResponseEntity.ok(deliverableService.assignReviewer(deliverableId, reviewerId, decisionDate, getUsername(authentication)));
    }

    @PostMapping("/api/deliverables/{deliverableId}/reviews/decision")
    public ResponseEntity<DeliverableApprovalDto> recordDecision(
            @PathVariable Long deliverableId,
            @RequestBody ApprovalDecisionRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(deliverableService.recordApprovalDecision(deliverableId, req, getUsername(authentication)));
    }

    @GetMapping("/api/deliverables/{deliverableId}/history")
    public ResponseEntity<List<DeliverableApprovalDto>> getApprovalHistory(@PathVariable Long deliverableId) {
        return ResponseEntity.ok(deliverableService.getDeliverableApprovals(deliverableId));
    }

    // ==========================================
    // 4. Task Completion Gates
    // ==========================================

    @PostMapping("/api/tasks/{taskId}/approval-gates")
    public ResponseEntity<TaskApprovalGateDto> configureTaskGate(
            @PathVariable Long taskId,
            @RequestBody ConfigureTaskGateRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deliverableService.configureTaskGate(taskId, req, getUsername(authentication)));
    }

    @GetMapping("/api/tasks/{taskId}/approval-gates")
    public ResponseEntity<List<TaskApprovalGateDto>> getTaskGates(@PathVariable Long taskId) {
        return ResponseEntity.ok(deliverableService.getTaskGates(taskId));
    }

    @DeleteMapping("/api/approval-gates/{gateId}")
    public ResponseEntity<Void> deleteTaskGate(
            @PathVariable Long gateId,
            Authentication authentication) {
        deliverableService.deleteTaskGate(gateId, getUsername(authentication));
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // 5. Overdue Approval & Schedule Slip Impact
    // ==========================================

    @GetMapping("/api/deliverables/{deliverableId}/delay-impact")
    public ResponseEntity<ApprovalDelayImpactDto> getDelayImpact(@PathVariable Long deliverableId) {
        return ResponseEntity.ok(deliverableService.getApprovalDelayImpact(deliverableId));
    }
}
