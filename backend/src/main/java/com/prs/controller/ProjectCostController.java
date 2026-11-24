package com.prs.controller;

import com.prs.dto.*;
import com.prs.entity.PoApprovalStatus;
import com.prs.entity.QuotationStatus;
import com.prs.service.ProjectCostManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor

public class ProjectCostController {

    private final ProjectCostManagementService costService;

    private String getUsername(Authentication authentication) {
        return (authentication != null && authentication.getName() != null) ? authentication.getName() : "admin";
    }

    // ==========================================
    // 1. Budget & Cost Variance Analysis
    // ==========================================

    @GetMapping("/api/projects/{projectId}/budget")
    public ResponseEntity<ProjectBudgetDto> getBudget(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getProjectBudget(projectId));
    }

    @PostMapping("/api/projects/{projectId}/budget")
    public ResponseEntity<ProjectBudgetDto> setBudget(
            @PathVariable Long projectId,
            @RequestBody SetBudgetRequest req,
            Authentication authentication) {
        return ResponseEntity.ok(costService.setProjectBudget(projectId, req, getUsername(authentication)));
    }

    @PostMapping("/api/projects/{projectId}/budget/approve")
    public ResponseEntity<ProjectBudgetDto> approveBudget(
            @PathVariable Long projectId,
            Authentication authentication) {
        return ResponseEntity.ok(costService.approveProjectBudget(projectId, getUsername(authentication)));
    }

    @PostMapping("/api/projects/{projectId}/budget/revisions")
    public ResponseEntity<BudgetRevisionDto> reviseBudget(
            @PathVariable Long projectId,
            @RequestBody ReviseBudgetRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(costService.reviseProjectBudget(projectId, req, getUsername(authentication)));
    }

    @GetMapping("/api/projects/{projectId}/budget/revisions")
    public ResponseEntity<List<BudgetRevisionDto>> getBudgetRevisions(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getBudgetRevisions(projectId));
    }

    @GetMapping("/api/projects/{projectId}/cost-summary")
    public ResponseEntity<CostSummaryDto> getCostSummary(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getCostSummary(projectId));
    }

    // ==========================================
    // 2. Vendor Quotations
    // ==========================================

    @GetMapping("/api/projects/{projectId}/quotations")
    public ResponseEntity<List<VendorQuotationDto>> getQuotations(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getQuotations(projectId));
    }

    @GetMapping("/api/quotations/{quotationId}")
    public ResponseEntity<VendorQuotationDto> getQuotation(@PathVariable Long quotationId) {
        return ResponseEntity.ok(costService.getQuotationById(quotationId));
    }

    @PostMapping("/api/projects/{projectId}/quotations")
    public ResponseEntity<VendorQuotationDto> createQuotation(
            @PathVariable Long projectId,
            @RequestBody CreateQuotationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(costService.createQuotation(projectId, req));
    }

    @PutMapping("/api/quotations/{quotationId}/status")
    public ResponseEntity<VendorQuotationDto> updateQuotationStatus(
            @PathVariable Long quotationId,
            @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }
        QuotationStatus status = QuotationStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(costService.updateQuotationStatus(quotationId, status));
    }

    // ==========================================
    // 3. Purchase Orders
    // ==========================================

    @GetMapping("/api/projects/{projectId}/purchase-orders")
    public ResponseEntity<List<PurchaseOrderDto>> getPurchaseOrders(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getPurchaseOrders(projectId));
    }

    @GetMapping("/api/purchase-orders/{poId}")
    public ResponseEntity<PurchaseOrderDto> getPurchaseOrder(@PathVariable Long poId) {
        return ResponseEntity.ok(costService.getPurchaseOrderById(poId));
    }

    @PostMapping("/api/projects/{projectId}/purchase-orders")
    public ResponseEntity<PurchaseOrderDto> createPurchaseOrder(
            @PathVariable Long projectId,
            @RequestBody CreatePurchaseOrderRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(costService.createPurchaseOrder(projectId, req, getUsername(authentication)));
    }

    @PutMapping("/api/purchase-orders/{poId}/status")
    public ResponseEntity<PurchaseOrderDto> updatePurchaseOrderStatus(
            @PathVariable Long poId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }
        PoApprovalStatus status = PoApprovalStatus.valueOf(statusStr.toUpperCase());
        return ResponseEntity.ok(costService.updatePurchaseOrderStatus(poId, status, getUsername(authentication)));
    }

    // ==========================================
    // 4. Delivery Tracking
    // ==========================================

    @PostMapping("/api/purchase-orders/{poId}/deliveries")
    public ResponseEntity<PurchaseOrderDeliveryDto> recordDelivery(
            @PathVariable Long poId,
            @RequestBody RecordDeliveryRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(costService.recordDelivery(poId, req, getUsername(authentication)));
    }

    @GetMapping("/api/purchase-orders/{poId}/deliveries")
    public ResponseEntity<List<PurchaseOrderDeliveryDto>> getDeliveries(@PathVariable Long poId) {
        return ResponseEntity.ok(costService.getDeliveries(poId));
    }

    // ==========================================
    // 5. Recorded Expenses
    // ==========================================

    @GetMapping("/api/projects/{projectId}/expenses")
    public ResponseEntity<List<ProjectExpenseDto>> getExpenses(@PathVariable Long projectId) {
        return ResponseEntity.ok(costService.getExpenses(projectId));
    }

    @PostMapping("/api/projects/{projectId}/expenses")
    public ResponseEntity<ProjectExpenseDto> recordExpense(
            @PathVariable Long projectId,
            @RequestBody RecordExpenseRequest req,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(costService.recordExpense(projectId, req, getUsername(authentication)));
    }
}
