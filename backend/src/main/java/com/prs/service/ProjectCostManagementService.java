package com.prs.service;

import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCostManagementService {

    private final ProjectRepository projectRepository;
    private final ProjectBudgetRepository budgetRepository;
    private final BudgetRevisionRepository revisionRepository;
    private final VendorRepository vendorRepository;
    private final VendorQuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final PurchaseOrderDeliveryRepository deliveryRepository;
    private final ProjectExpenseRepository expenseRepository;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;

    // ==========================================
    // 1. Project Budget Management
    // ==========================================

    @Transactional(readOnly = true)
    public ProjectBudgetDto getProjectBudget(Long projectId) {
        Project project = getValidatedProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElseGet(() -> createDefaultDraftBudget(project));
        return mapToBudgetDto(budget);
    }

    @Transactional
    public ProjectBudgetDto setProjectBudget(Long projectId, SetBudgetRequest req, String username) {
        Project project = getValidatedProject(projectId);
        if (req.getInitialBudget() == null || req.getInitialBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial budget amount must be non-negative.");
        }

        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    ProjectBudget pb = new ProjectBudget();
                    pb.setProject(project);
                    return pb;
                });

        budget.setInitialBudget(req.getInitialBudget().setScale(2, RoundingMode.HALF_UP));
        if (budget.getStatus() == BudgetStatus.DRAFT) {
            budget.setApprovedBudget(budget.getInitialBudget());
        }
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) {
            budget.setCurrency(req.getCurrency().trim().toUpperCase());
        }
        if (req.getNotes() != null) {
            budget.setNotes(req.getNotes());
        }
        budget.setStatus(BudgetStatus.PENDING_APPROVAL);

        return mapToBudgetDto(budgetRepository.save(budget));
    }

    @Transactional
    public ProjectBudgetDto approveProjectBudget(Long projectId, String username) {
        Project project = getValidatedProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No budget found for project: " + projectId));

        budget.setApprovedBudget(budget.getInitialBudget());
        budget.setStatus(BudgetStatus.APPROVED);
        return mapToBudgetDto(budgetRepository.save(budget));
    }

    @Transactional
    public BudgetRevisionDto reviseProjectBudget(Long projectId, ReviseBudgetRequest req, String username) {
        Project project = getValidatedProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("No budget found for project: " + projectId));

        if (req.getRevisedAmount() == null || req.getRevisedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Revised budget amount must be non-negative.");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new IllegalArgumentException("Revision reason is required.");
        }

        BigDecimal prevAmount = budget.getApprovedBudget();
        BigDecimal newAmount = req.getRevisedAmount().setScale(2, RoundingMode.HALF_UP);

        int nextRevisionNum = revisionRepository.countByBudgetId(budget.getId()) + 1;

        BudgetRevision revision = new BudgetRevision();
        revision.setBudget(budget);
        revision.setRevisionNumber(nextRevisionNum);
        revision.setPreviousAmount(prevAmount);
        revision.setRevisedAmount(newAmount);
        revision.setReason(req.getReason().trim());
        revision.setApprovedBy(username);
        revision.setApprovedAt(LocalDateTime.now());
        revision = revisionRepository.save(revision);

        // Update current approved budget
        budget.setApprovedBudget(newAmount);
        budget.setStatus(BudgetStatus.APPROVED);
        budgetRepository.save(budget);

        return mapToRevisionDto(revision);
    }

    @Transactional(readOnly = true)
    public List<BudgetRevisionDto> getBudgetRevisions(Long projectId) {
        getValidatedProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElse(null);
        if (budget == null) return Collections.emptyList();
        return revisionRepository.findByBudgetIdOrderByRevisionNumberDesc(budget.getId())
                .stream().map(this::mapToRevisionDto).collect(Collectors.toList());
    }

    // ==========================================
    // 2. Vendor Quotations
    // ==========================================

    @Transactional(readOnly = true)
    public List<VendorQuotationDto> getQuotations(Long projectId) {
        getValidatedProject(projectId);
        return quotationRepository.findByProjectId(projectId).stream()
                .map(this::mapToQuotationDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VendorQuotationDto getQuotationById(Long quotationId) {
        VendorQuotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found: " + quotationId));
        validateProjectTenant(quotation.getProject());
        return mapToQuotationDto(quotation);
    }

    @Transactional
    public VendorQuotationDto createQuotation(Long projectId, CreateQuotationRequest req) {
        Project project = getValidatedProject(projectId);
        Vendor vendor = vendorRepository.findById(req.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + req.getVendorId()));
        validateCrossTenantVendor(project, vendor);

        if (req.getQuoteReference() == null || req.getQuoteReference().isBlank()) {
            throw new IllegalArgumentException("Quote reference is required.");
        }
        if (quotationRepository.existsByQuoteReference(req.getQuoteReference().trim())) {
            throw new IllegalArgumentException("Quote reference already exists: " + req.getQuoteReference());
        }
        if (req.getIssueDate() == null) {
            req.setIssueDate(LocalDate.now());
        }

        VendorQuotation quotation = new VendorQuotation();
        quotation.setProject(project);
        quotation.setVendor(vendor);
        quotation.setQuoteReference(req.getQuoteReference().trim());
        quotation.setIssueDate(req.getIssueDate());
        quotation.setValidUntil(req.getValidUntil());
        quotation.setCurrency(req.getCurrency() != null ? req.getCurrency().toUpperCase() : "INR");
        quotation.setTaxAmount(req.getTaxAmount() != null ? req.getTaxAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        quotation.setNotes(req.getNotes());
        quotation.setStatus(QuotationStatus.RECEIVED);

        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        List<QuotationItem> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (QuotationItemDto itemDto : req.getItems()) {
                if (itemDto.getDescription() == null || itemDto.getDescription().isBlank()) continue;
                BigDecimal qty = itemDto.getQuantity() != null ? itemDto.getQuantity() : BigDecimal.ONE;
                BigDecimal unitPrice = itemDto.getUnitPrice() != null ? itemDto.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal lineSubtotal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

                QuotationItem item = new QuotationItem();
                item.setQuotation(quotation);
                item.setDescription(itemDto.getDescription().trim());
                item.setQuantity(qty.setScale(2, RoundingMode.HALF_UP));
                item.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
                item.setSubtotal(lineSubtotal);
                items.add(item);
                itemsSubtotal = itemsSubtotal.add(lineSubtotal);
            }
        }

        quotation.setItems(items);
        quotation.setTotalAmount(itemsSubtotal.add(quotation.getTaxAmount()).setScale(2, RoundingMode.HALF_UP));

        return mapToQuotationDto(quotationRepository.save(quotation));
    }

    @Transactional
    public VendorQuotationDto updateQuotationStatus(Long quotationId, QuotationStatus status) {
        VendorQuotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found: " + quotationId));
        validateProjectTenant(quotation.getProject());
        quotation.setStatus(status);
        return mapToQuotationDto(quotationRepository.save(quotation));
    }

    // ==========================================
    // 3. Purchase Orders & Procurement
    // ==========================================

    @Transactional(readOnly = true)
    public List<PurchaseOrderDto> getPurchaseOrders(Long projectId) {
        getValidatedProject(projectId);
        return purchaseOrderRepository.findByProjectId(projectId).stream()
                .map(this::mapToPurchaseOrderDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDto getPurchaseOrderById(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + poId));
        validateProjectTenant(po.getProject());
        return mapToPurchaseOrderDto(po);
    }

    @Transactional
    public PurchaseOrderDto createPurchaseOrder(Long projectId, CreatePurchaseOrderRequest req, String username) {
        Project project = getValidatedProject(projectId);
        Vendor vendor = vendorRepository.findById(req.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + req.getVendorId()));
        validateCrossTenantVendor(project, vendor);

        if (req.getPoReference() == null || req.getPoReference().isBlank()) {
            throw new IllegalArgumentException("Purchase order reference is required.");
        }
        if (purchaseOrderRepository.existsByPoReference(req.getPoReference().trim())) {
            throw new IllegalArgumentException("Purchase order reference already exists: " + req.getPoReference());
        }

        VendorQuotation quotation = null;
        if (req.getQuotationId() != null) {
            quotation = quotationRepository.findById(req.getQuotationId()).orElse(null);
            if (quotation != null) {
                validateProjectTenant(quotation.getProject());
            }
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setPoReference(req.getPoReference().trim());
        po.setProject(project);
        po.setVendor(vendor);
        po.setQuotation(quotation);
        po.setOrderDate(req.getOrderDate() != null ? req.getOrderDate() : LocalDate.now());
        po.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        po.setCurrency(req.getCurrency() != null ? req.getCurrency().toUpperCase() : "INR");
        po.setNotes(req.getNotes());
        po.setApprovalStatus(PoApprovalStatus.DRAFT);
        po.setDeliveryStatus(PoDeliveryStatus.PENDING);

        BigDecimal committedTotal = BigDecimal.ZERO;
        List<PurchaseOrderLine> lines = new ArrayList<>();
        if (req.getLines() != null) {
            for (PurchaseOrderLineDto lineDto : req.getLines()) {
                if (lineDto.getDescription() == null || lineDto.getDescription().isBlank()) continue;
                BigDecimal qty = lineDto.getQuantity() != null ? lineDto.getQuantity() : BigDecimal.ONE;
                BigDecimal unitPrice = lineDto.getUnitPrice() != null ? lineDto.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal lineTotal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

                PurchaseOrderLine line = new PurchaseOrderLine();
                line.setPurchaseOrder(po);
                line.setDescription(lineDto.getDescription().trim());
                line.setQuantity(qty.setScale(2, RoundingMode.HALF_UP));
                line.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
                line.setLineTotal(lineTotal);
                line.setDeliveredQuantity(BigDecimal.ZERO);
                lines.add(line);
                committedTotal = committedTotal.add(lineTotal);
            }
        }

        po.setLines(lines);
        po.setTotalCommittedAmount(committedTotal.setScale(2, RoundingMode.HALF_UP));

        return mapToPurchaseOrderDto(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDto updatePurchaseOrderStatus(Long poId, PoApprovalStatus status, String username) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + poId));
        validateProjectTenant(po.getProject());

        po.setApprovalStatus(status);
        if (status == PoApprovalStatus.APPROVED) {
            po.setApprovedBy(username);
            po.setApprovedAt(LocalDateTime.now());
        }
        return mapToPurchaseOrderDto(purchaseOrderRepository.save(po));
    }

    // ==========================================
    // 4. Delivery Tracking
    // ==========================================

    @Transactional
    public PurchaseOrderDeliveryDto recordDelivery(Long poId, RecordDeliveryRequest req, String username) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + poId));
        validateProjectTenant(po.getProject());

        if (req.getQuantityDelivered() == null || req.getQuantityDelivered().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Delivered quantity must be greater than zero.");
        }
        if (req.getActualDeliveryDate() == null) {
            req.setActualDeliveryDate(LocalDate.now());
        }

        PurchaseOrderLine targetLine = null;
        if (req.getPoLineId() != null) {
            targetLine = purchaseOrderLineRepository.findById(req.getPoLineId()).orElse(null);
            if (targetLine != null && !targetLine.getPurchaseOrder().getId().equals(po.getId())) {
                throw new IllegalArgumentException("Purchase order line does not belong to this purchase order.");
            }
        } else if (!po.getLines().isEmpty()) {
            targetLine = po.getLines().get(0);
        }

        if (targetLine != null) {
            BigDecimal currentDelivered = targetLine.getDeliveredQuantity() != null ? targetLine.getDeliveredQuantity() : BigDecimal.ZERO;
            targetLine.setDeliveredQuantity(currentDelivered.add(req.getQuantityDelivered()).setScale(2, RoundingMode.HALF_UP));
            purchaseOrderLineRepository.save(targetLine);
        }

        // Evaluate overall PO delivery status
        boolean allDelivered = true;
        boolean anyDelivered = false;
        for (PurchaseOrderLine line : po.getLines()) {
            BigDecimal delivered = line.getDeliveredQuantity() != null ? line.getDeliveredQuantity() : BigDecimal.ZERO;
            if (delivered.compareTo(BigDecimal.ZERO) > 0) {
                anyDelivered = true;
            }
            if (delivered.compareTo(line.getQuantity()) < 0) {
                allDelivered = false;
            }
        }

        if (allDelivered) {
            po.setDeliveryStatus(PoDeliveryStatus.DELIVERED);
        } else if (anyDelivered) {
            po.setDeliveryStatus(PoDeliveryStatus.PARTIALLY_DELIVERED);
        }
        purchaseOrderRepository.save(po);

        PurchaseOrderDelivery delivery = new PurchaseOrderDelivery();
        delivery.setPurchaseOrder(po);
        delivery.setPoLine(targetLine);
        delivery.setDeliveryReference(req.getDeliveryReference() != null ? req.getDeliveryReference().trim() : "DEL-" + System.currentTimeMillis());
        delivery.setExpectedDeliveryDate(req.getExpectedDeliveryDate() != null ? req.getExpectedDeliveryDate() : po.getExpectedDeliveryDate());
        delivery.setActualDeliveryDate(req.getActualDeliveryDate());
        delivery.setQuantityDelivered(req.getQuantityDelivered().setScale(2, RoundingMode.HALF_UP));
        delivery.setReceivedBy(username);
        delivery.setStatus(req.getStatus() != null ? req.getStatus() : "ACCEPTED");
        delivery.setNotes(req.getNotes());

        return mapToDeliveryDto(deliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDeliveryDto> getDeliveries(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + poId));
        validateProjectTenant(po.getProject());
        return deliveryRepository.findByPurchaseOrderId(poId).stream()
                .map(this::mapToDeliveryDto).collect(Collectors.toList());
    }

    // ==========================================
    // 5. Actual Expenses & Cost Variance Analysis
    // ==========================================

    @Transactional(readOnly = true)
    public List<ProjectExpenseDto> getExpenses(Long projectId) {
        getValidatedProject(projectId);
        return expenseRepository.findByProjectId(projectId).stream()
                .map(this::mapToExpenseDto).collect(Collectors.toList());
    }

    @Transactional
    public ProjectExpenseDto recordExpense(Long projectId, RecordExpenseRequest req, String username) {
        Project project = getValidatedProject(projectId);

        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be strictly greater than zero.");
        }
        if (req.getCategory() == null) {
            throw new IllegalArgumentException("Cost category is required.");
        }
        if (req.getDescription() == null || req.getDescription().isBlank()) {
            throw new IllegalArgumentException("Expense description is required.");
        }
        if (req.getExpenseDate() == null) {
            req.setExpenseDate(LocalDate.now());
        }

        Task task = null;
        if (req.getTaskId() != null) {
            task = taskRepository.findById(req.getTaskId()).orElse(null);
        }

        Vendor vendor = null;
        if (req.getVendorId() != null) {
            vendor = vendorRepository.findById(req.getVendorId()).orElse(null);
            if (vendor != null) {
                validateCrossTenantVendor(project, vendor);
            }
        }

        PurchaseOrder po = null;
        if (req.getPurchaseOrderId() != null) {
            po = purchaseOrderRepository.findById(req.getPurchaseOrderId()).orElse(null);
            if (po != null) {
                validateProjectTenant(po.getProject());
            }
        }

        Worker worker = null;
        if (req.getWorkerId() != null) {
            worker = workerRepository.findById(req.getWorkerId()).orElse(null);
            if (worker != null && worker.getOrganization() != null && project.getOrganization() != null
                    && !worker.getOrganization().getId().equals(project.getOrganization().getId())) {
                throw new IllegalArgumentException("Cannot associate worker from another organization.");
            }
        }

        ProjectExpense expense = new ProjectExpense();
        expense.setProject(project);
        expense.setCategory(req.getCategory());
        expense.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        expense.setCurrency(req.getCurrency() != null ? req.getCurrency().toUpperCase() : "INR");
        expense.setExpenseDate(req.getExpenseDate());
        expense.setDescription(req.getDescription().trim());
        expense.setTask(task);
        expense.setVendor(vendor);
        expense.setPurchaseOrder(po);
        expense.setWorker(worker);
        expense.setRecordedBy(username);

        return mapToExpenseDto(expenseRepository.save(expense));
    }

    @Transactional(readOnly = true)
    public CostSummaryDto getCostSummary(Long projectId) {
        Project project = getValidatedProject(projectId);
        ProjectBudget budget = budgetRepository.findByProjectId(projectId)
                .orElseGet(() -> createDefaultDraftBudget(project));

        BigDecimal approvedBudget = budget.getApprovedBudget();
        String currency = budget.getCurrency();

        List<PurchaseOrder> approvedPos = purchaseOrderRepository.findByProjectId(projectId).stream()
                .filter(p -> p.getApprovalStatus() == PoApprovalStatus.APPROVED)
                .collect(Collectors.toList());

        BigDecimal totalCommitted = approvedPos.stream()
                .map(PurchaseOrder::getTotalCommittedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProjectExpense> allExpenses = expenseRepository.findByProjectId(projectId);
        BigDecimal totalActual = allExpenses.stream()
                .map(ProjectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingBudget = approvedBudget.subtract(totalActual);
        BigDecimal remainingCommitted = approvedBudget.subtract(totalCommitted);
        BigDecimal costVariance = totalActual.subtract(approvedBudget);

        BigDecimal costVariancePercent = BigDecimal.ZERO;
        if (approvedBudget.compareTo(BigDecimal.ZERO) > 0) {
            costVariancePercent = costVariance.divide(approvedBudget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        }

        // Category breakdown
        Map<CostCategory, BigDecimal> breakdown = new EnumMap<>(CostCategory.class);
        for (CostCategory cat : CostCategory.values()) {
            breakdown.put(cat, BigDecimal.ZERO);
        }
        for (ProjectExpense exp : allExpenses) {
            BigDecimal curr = breakdown.getOrDefault(exp.getCategory(), BigDecimal.ZERO);
            breakdown.put(exp.getCategory(), curr.add(exp.getAmount()).setScale(2, RoundingMode.HALF_UP));
        }

        CostSummaryDto dto = new CostSummaryDto();
        dto.setProjectId(project.getId());
        dto.setProjectName(project.getName());
        dto.setCurrency(currency);
        dto.setApprovedBudget(approvedBudget);
        dto.setTotalCommittedCost(totalCommitted);
        dto.setTotalActualCost(totalActual);
        dto.setRemainingBudget(remainingBudget);
        dto.setRemainingCommittedBudget(remainingCommitted);
        dto.setCostVariance(costVariance);
        dto.setCostVariancePercent(costVariancePercent);
        dto.setOverBudget(totalActual.compareTo(approvedBudget) > 0);
        dto.setCategoryBreakdown(breakdown);
        dto.setRecentExpenses(allExpenses.stream().limit(10).map(this::mapToExpenseDto).collect(Collectors.toList()));
        dto.setActivePurchaseOrders(approvedPos.stream().map(this::mapToPurchaseOrderDto).collect(Collectors.toList()));

        return dto;
    }

    // ==========================================
    // Internal Helper Methods & Mapping
    // ==========================================

    private Project getValidatedProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        validateProjectTenant(project);
        return project;
    }

    private void validateProjectTenant(Project project) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && project.getOrganization() != null && !project.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Project does not belong to active organization.");
        }
    }

    private void validateCrossTenantVendor(Project project, Vendor vendor) {
        if (project.getOrganization() != null && vendor.getOrganization() != null
                && !project.getOrganization().getId().equals(vendor.getOrganization().getId())) {
            throw new IllegalArgumentException("Cannot use vendor belonging to a different organization.");
        }
    }

    private ProjectBudget createDefaultDraftBudget(Project project) {
        ProjectBudget b = new ProjectBudget();
        b.setProject(project);
        b.setInitialBudget(BigDecimal.ZERO);
        b.setApprovedBudget(BigDecimal.ZERO);
        b.setCurrency("INR");
        b.setStatus(BudgetStatus.DRAFT);
        return b;
    }

    private ProjectBudgetDto mapToBudgetDto(ProjectBudget b) {
        ProjectBudgetDto dto = new ProjectBudgetDto();
        dto.setId(b.getId());
        dto.setProjectId(b.getProject().getId());
        dto.setProjectName(b.getProject().getName());
        dto.setInitialBudget(b.getInitialBudget());
        dto.setApprovedBudget(b.getApprovedBudget());
        dto.setCurrency(b.getCurrency());
        dto.setStatus(b.getStatus());
        dto.setNotes(b.getNotes());
        dto.setRevisionCount(b.getId() != null ? revisionRepository.countByBudgetId(b.getId()) : 0);
        dto.setCreatedAt(b.getCreatedAt());
        dto.setUpdatedAt(b.getUpdatedAt());
        return dto;
    }

    private BudgetRevisionDto mapToRevisionDto(BudgetRevision r) {
        BudgetRevisionDto dto = new BudgetRevisionDto();
        dto.setId(r.getId());
        dto.setBudgetId(r.getBudget().getId());
        dto.setRevisionNumber(r.getRevisionNumber());
        dto.setPreviousAmount(r.getPreviousAmount());
        dto.setRevisedAmount(r.getRevisedAmount());
        dto.setReason(r.getReason());
        dto.setApprovedBy(r.getApprovedBy());
        dto.setApprovedAt(r.getApprovedAt());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }

    private VendorQuotationDto mapToQuotationDto(VendorQuotation q) {
        VendorQuotationDto dto = new VendorQuotationDto();
        dto.setId(q.getId());
        dto.setProjectId(q.getProject().getId());
        dto.setProjectName(q.getProject().getName());
        dto.setVendorId(q.getVendor().getId());
        dto.setVendorName(q.getVendor().getName());
        dto.setQuoteReference(q.getQuoteReference());
        dto.setIssueDate(q.getIssueDate());
        dto.setValidUntil(q.getValidUntil());
        dto.setCurrency(q.getCurrency());
        dto.setTaxAmount(q.getTaxAmount());
        dto.setTotalAmount(q.getTotalAmount());
        dto.setStatus(q.getStatus());
        dto.setNotes(q.getNotes());
        dto.setCreatedAt(q.getCreatedAt());
        dto.setUpdatedAt(q.getUpdatedAt());

        List<QuotationItemDto> itemDtos = q.getItems().stream().map(i -> {
            QuotationItemDto idto = new QuotationItemDto();
            idto.setId(i.getId());
            idto.setDescription(i.getDescription());
            idto.setQuantity(i.getQuantity());
            idto.setUnitPrice(i.getUnitPrice());
            idto.setSubtotal(i.getSubtotal());
            return idto;
        }).collect(Collectors.toList());
        dto.setItems(itemDtos);
        return dto;
    }

    private PurchaseOrderDto mapToPurchaseOrderDto(PurchaseOrder po) {
        PurchaseOrderDto dto = new PurchaseOrderDto();
        dto.setId(po.getId());
        dto.setPoReference(po.getPoReference());
        dto.setProjectId(po.getProject().getId());
        dto.setProjectName(po.getProject().getName());
        dto.setVendorId(po.getVendor().getId());
        dto.setVendorName(po.getVendor().getName());
        if (po.getQuotation() != null) {
            dto.setQuotationId(po.getQuotation().getId());
            dto.setQuoteReference(po.getQuotation().getQuoteReference());
        }
        dto.setOrderDate(po.getOrderDate());
        dto.setExpectedDeliveryDate(po.getExpectedDeliveryDate());
        dto.setCurrency(po.getCurrency());
        dto.setTotalCommittedAmount(po.getTotalCommittedAmount());
        dto.setApprovalStatus(po.getApprovalStatus());
        dto.setDeliveryStatus(po.getDeliveryStatus());
        dto.setApprovedBy(po.getApprovedBy());
        dto.setApprovedAt(po.getApprovedAt());
        dto.setNotes(po.getNotes());
        dto.setCreatedAt(po.getCreatedAt());
        dto.setUpdatedAt(po.getUpdatedAt());

        List<PurchaseOrderLineDto> lineDtos = po.getLines().stream().map(l -> {
            PurchaseOrderLineDto ldto = new PurchaseOrderLineDto();
            ldto.setId(l.getId());
            ldto.setDescription(l.getDescription());
            ldto.setQuantity(l.getQuantity());
            ldto.setUnitPrice(l.getUnitPrice());
            ldto.setLineTotal(l.getLineTotal());
            ldto.setDeliveredQuantity(l.getDeliveredQuantity());
            return ldto;
        }).collect(Collectors.toList());
        dto.setLines(lineDtos);
        return dto;
    }

    private PurchaseOrderDeliveryDto mapToDeliveryDto(PurchaseOrderDelivery d) {
        PurchaseOrderDeliveryDto dto = new PurchaseOrderDeliveryDto();
        dto.setId(d.getId());
        dto.setPurchaseOrderId(d.getPurchaseOrder().getId());
        dto.setPoReference(d.getPurchaseOrder().getPoReference());
        if (d.getPoLine() != null) {
            dto.setPoLineId(d.getPoLine().getId());
            dto.setLineDescription(d.getPoLine().getDescription());
        }
        dto.setDeliveryReference(d.getDeliveryReference());
        dto.setExpectedDeliveryDate(d.getExpectedDeliveryDate());
        dto.setActualDeliveryDate(d.getActualDeliveryDate());
        dto.setQuantityDelivered(d.getQuantityDelivered());
        dto.setReceivedBy(d.getReceivedBy());
        dto.setStatus(d.getStatus());
        dto.setNotes(d.getNotes());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private ProjectExpenseDto mapToExpenseDto(ProjectExpense e) {
        ProjectExpenseDto dto = new ProjectExpenseDto();
        dto.setId(e.getId());
        dto.setProjectId(e.getProject().getId());
        dto.setProjectName(e.getProject().getName());
        dto.setCategory(e.getCategory());
        dto.setAmount(e.getAmount());
        dto.setCurrency(e.getCurrency());
        dto.setExpenseDate(e.getExpenseDate());
        dto.setDescription(e.getDescription());
        if (e.getTask() != null) {
            dto.setTaskId(e.getTask().getId());
            dto.setTaskName(e.getTask().getName());
        }
        if (e.getVendor() != null) {
            dto.setVendorId(e.getVendor().getId());
            dto.setVendorName(e.getVendor().getName());
        }
        if (e.getPurchaseOrder() != null) {
            dto.setPurchaseOrderId(e.getPurchaseOrder().getId());
            dto.setPoReference(e.getPurchaseOrder().getPoReference());
        }
        if (e.getWorker() != null) {
            dto.setWorkerId(e.getWorker().getId());
            dto.setWorkerName(e.getWorker().getName());
        }
        dto.setRecordedBy(e.getRecordedBy());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }
}
