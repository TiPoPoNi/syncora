package com.prs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 13: Budget, Procurement & Cost Management Integration Tests")
public class Phase13CostManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ProjectBudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionRepository revisionRepository;

    @Autowired
    private VendorQuotationRepository quotationRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProjectExpenseRepository expenseRepository;

    private String adminJwt;
    private Organization orgAlpha;
    private Organization orgBeta;
    private Project projectAlpha;
    private Project projectBeta;
    private Vendor vendorAlpha;
    private Vendor vendorBeta;

    @BeforeEach
    void setUp() throws Exception {
        // Authenticate admin
        AuthRequest loginReq = new AuthRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin");

        MvcResult actualLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseStr = actualLogin.getResponse().getContentAsString();
        adminJwt = objectMapper.readTree(responseStr).get("token").asText();
        User adminUser = userRepository.findByUsername("admin").orElseThrow();

        // Organizations
        orgAlpha = organizationRepository.findBySlug("cost-alpha-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Cost Alpha Corp");
            o.setSlug("cost-alpha-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        orgBeta = organizationRepository.findBySlug("cost-beta-org").orElseGet(() -> {
            Organization o = new Organization();
            o.setName("Cost Beta Corp");
            o.setSlug("cost-beta-org");
            o.setStatus("ACTIVE");
            return organizationRepository.save(o);
        });

        // Ensure memberships
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgAlpha.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgAlpha);
            m.setRole("ADMIN");
            membershipRepository.save(m);
        }
        if (membershipRepository.findByUserIdAndOrganizationId(adminUser.getId(), orgBeta.getId()).isEmpty()) {
            OrganizationMembership m = new OrganizationMembership();
            m.setUser(adminUser);
            m.setOrganization(orgBeta);
            m.setRole("ADMIN");
            membershipRepository.save(m);
        }

        // Projects
        projectAlpha = new Project();
        projectAlpha.setName("Alpha Construction Project");
        projectAlpha.setStartDate(LocalDate.now());
        projectAlpha.setDeadline(LocalDate.now().plusMonths(6));
        projectAlpha.setStatus(ProjectStatus.IN_PROGRESS);
        projectAlpha.setOrganization(orgAlpha);
        projectAlpha = projectRepository.save(projectAlpha);

        projectBeta = new Project();
        projectBeta.setName("Beta Software Project");
        projectBeta.setStartDate(LocalDate.now());
        projectBeta.setDeadline(LocalDate.now().plusMonths(4));
        projectBeta.setStatus(ProjectStatus.PLANNED);
        projectBeta.setOrganization(orgBeta);
        projectBeta = projectRepository.save(projectBeta);

        // Vendors
        vendorAlpha = new Vendor();
        vendorAlpha.setName("Alpha Steel Supplies");
        vendorAlpha.setServices("Materials, Steel Rods");
        vendorAlpha.setOrganization(orgAlpha);
        vendorAlpha = vendorRepository.save(vendorAlpha);

        vendorBeta = new Vendor();
        vendorBeta.setName("Beta Cloud Hosting");
        vendorBeta.setServices("Cloud Hosting, Servers");
        vendorBeta.setOrganization(orgBeta);
        vendorBeta = vendorRepository.save(vendorBeta);
    }

    @Test
    @DisplayName("1. Project Budget Lifecycle & Revision History")
    void testBudgetLifecycleAndRevisions() throws Exception {
        Long projectId = projectAlpha.getId();

        // 1a. Set Budget
        SetBudgetRequest setReq = new SetBudgetRequest();
        setReq.setInitialBudget(new BigDecimal("500000.00"));
        setReq.setCurrency("INR");
        setReq.setNotes("Initial baseline budget for Alpha");

        MvcResult setRes = mockMvc.perform(post("/api/projects/" + projectId + "/budget")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setReq)))
                .andExpect(status().isOk())
                .andReturn();

        ProjectBudgetDto budgetDto = objectMapper.readValue(setRes.getResponse().getContentAsString(), ProjectBudgetDto.class);
        assertThat(budgetDto.getInitialBudget()).isEqualByComparingTo("500000.00");
        assertThat(budgetDto.getStatus()).isEqualTo(BudgetStatus.PENDING_APPROVAL);
        assertThat(budgetDto.getCurrency()).isEqualTo("INR");

        // 1b. Approve Budget
        MvcResult appRes = mockMvc.perform(post("/api/projects/" + projectId + "/budget/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        ProjectBudgetDto approvedBudgetDto = objectMapper.readValue(appRes.getResponse().getContentAsString(), ProjectBudgetDto.class);
        assertThat(approvedBudgetDto.getStatus()).isEqualTo(BudgetStatus.APPROVED);
        assertThat(approvedBudgetDto.getApprovedBudget()).isEqualByComparingTo("500000.00");

        // 1c. Revise Budget
        ReviseBudgetRequest revReq = new ReviseBudgetRequest();
        revReq.setRevisedAmount(new BigDecimal("650000.00"));
        revReq.setReason("Scope expansion for structural reinforcement");

        MvcResult revRes = mockMvc.perform(post("/api/projects/" + projectId + "/budget/revisions")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isCreated())
                .andReturn();

        BudgetRevisionDto revDto = objectMapper.readValue(revRes.getResponse().getContentAsString(), BudgetRevisionDto.class);
        assertThat(revDto.getRevisionNumber()).isEqualTo(1);
        assertThat(revDto.getPreviousAmount()).isEqualByComparingTo("500000.00");
        assertThat(revDto.getRevisedAmount()).isEqualByComparingTo("650000.00");
        assertThat(revDto.getReason()).isEqualTo("Scope expansion for structural reinforcement");

        // 1d. Verify history and updated budget
        MvcResult historyRes = mockMvc.perform(get("/api/projects/" + projectId + "/budget/revisions")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        List<?> history = objectMapper.readValue(historyRes.getResponse().getContentAsString(), List.class);
        assertThat(history).hasSize(1);

        MvcResult updatedBudgetRes = mockMvc.perform(get("/api/projects/" + projectId + "/budget")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        ProjectBudgetDto currentBudget = objectMapper.readValue(updatedBudgetRes.getResponse().getContentAsString(), ProjectBudgetDto.class);
        assertThat(currentBudget.getApprovedBudget()).isEqualByComparingTo("650000.00");
        assertThat(currentBudget.getInitialBudget()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("2. Vendor Quotations & Line Items Calculation")
    void testVendorQuotationsLifecycle() throws Exception {
        Long projectId = projectAlpha.getId();

        CreateQuotationRequest qReq = new CreateQuotationRequest();
        qReq.setVendorId(vendorAlpha.getId());
        qReq.setQuoteReference("QT-ALPHA-2026-001");
        qReq.setIssueDate(LocalDate.now());
        qReq.setValidUntil(LocalDate.now().plusDays(30));
        qReq.setCurrency("INR");
        qReq.setTaxAmount(new BigDecimal("5000.00"));
        qReq.setNotes("Special discounted quotation");

        QuotationItemDto item1 = new QuotationItemDto();
        item1.setDescription("TMT Steel Rods (12mm) - 100 bundles");
        item1.setQuantity(new BigDecimal("100.00"));
        item1.setUnitPrice(new BigDecimal("550.00")); // subtotal 55000

        QuotationItemDto item2 = new QuotationItemDto();
        item2.setDescription("Structural Steel Beams - 10 units");
        item2.setQuantity(new BigDecimal("10.00"));
        item2.setUnitPrice(new BigDecimal("4500.00")); // subtotal 45000

        qReq.setItems(List.of(item1, item2));

        MvcResult createRes = mockMvc.perform(post("/api/projects/" + projectId + "/quotations")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qReq)))
                .andExpect(status().isCreated())
                .andReturn();

        VendorQuotationDto qDto = objectMapper.readValue(createRes.getResponse().getContentAsString(), VendorQuotationDto.class);
        assertThat(qDto.getQuoteReference()).isEqualTo("QT-ALPHA-2026-001");
        // items subtotal = 55000 + 45000 = 100000. Total = 100000 + 5000 tax = 105000
        assertThat(qDto.getTotalAmount()).isEqualByComparingTo("105000.00");
        assertThat(qDto.getStatus()).isEqualTo(QuotationStatus.RECEIVED);
        assertThat(qDto.getItems()).hasSize(2);

        // Update status to ACCEPTED
        MvcResult statusRes = mockMvc.perform(put("/api/quotations/" + qDto.getId() + "/status")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACCEPTED"))))
                .andExpect(status().isOk())
                .andReturn();

        VendorQuotationDto updatedDto = objectMapper.readValue(statusRes.getResponse().getContentAsString(), VendorQuotationDto.class);
        assertThat(updatedDto.getStatus()).isEqualTo(QuotationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("3. Purchase Orders, Delivery Tracking & Lifecycle Transitions")
    void testPurchaseOrderCommitmentAndDeliveries() throws Exception {
        Long projectId = projectAlpha.getId();

        CreatePurchaseOrderRequest poReq = new CreatePurchaseOrderRequest();
        poReq.setVendorId(vendorAlpha.getId());
        poReq.setPoReference("PO-ALPHA-8801");
        poReq.setOrderDate(LocalDate.now());
        poReq.setExpectedDeliveryDate(LocalDate.now().plusWeeks(2));
        poReq.setCurrency("INR");
        poReq.setNotes("First bulk procurement order");

        PurchaseOrderLineDto line1 = new PurchaseOrderLineDto();
        line1.setDescription("Cement Bags Grade 53");
        line1.setQuantity(new BigDecimal("200.00"));
        line1.setUnitPrice(new BigDecimal("350.00")); // total 70000

        PurchaseOrderLineDto line2 = new PurchaseOrderLineDto();
        line2.setDescription("Aggregate Coarse Sand - Tons");
        line2.setQuantity(new BigDecimal("50.00"));
        line2.setUnitPrice(new BigDecimal("1200.00")); // total 60000

        poReq.setLines(List.of(line1, line2));

        // Create PO
        MvcResult poRes = mockMvc.perform(post("/api/projects/" + projectId + "/purchase-orders")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(poReq)))
                .andExpect(status().isCreated())
                .andReturn();

        PurchaseOrderDto poDto = objectMapper.readValue(poRes.getResponse().getContentAsString(), PurchaseOrderDto.class);
        assertThat(poDto.getPoReference()).isEqualTo("PO-ALPHA-8801");
        assertThat(poDto.getTotalCommittedAmount()).isEqualByComparingTo("130000.00");
        assertThat(poDto.getApprovalStatus()).isEqualTo(PoApprovalStatus.DRAFT);
        assertThat(poDto.getDeliveryStatus()).isEqualTo(PoDeliveryStatus.PENDING);

        Long poId = poDto.getId();
        Long line1Id = poDto.getLines().get(0).getId();

        // Approve PO
        MvcResult approveRes = mockMvc.perform(put("/api/purchase-orders/" + poId + "/status")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isOk())
                .andReturn();

        PurchaseOrderDto approvedPo = objectMapper.readValue(approveRes.getResponse().getContentAsString(), PurchaseOrderDto.class);
        assertThat(approvedPo.getApprovalStatus()).isEqualTo(PoApprovalStatus.APPROVED);

        // Record Partial Delivery (100 bags of Cement)
        RecordDeliveryRequest d1 = new RecordDeliveryRequest();
        d1.setPoLineId(line1Id);
        d1.setQuantityDelivered(new BigDecimal("100.00"));
        d1.setActualDeliveryDate(LocalDate.now());
        d1.setDeliveryReference("CHAL-1001");
        d1.setNotes("First batch delivered in good condition");

        MvcResult del1Res = mockMvc.perform(post("/api/purchase-orders/" + poId + "/deliveries")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(d1)))
                .andExpect(status().isCreated())
                .andReturn();

        PurchaseOrderDeliveryDto del1Dto = objectMapper.readValue(del1Res.getResponse().getContentAsString(), PurchaseOrderDeliveryDto.class);
        assertThat(del1Dto.getQuantityDelivered()).isEqualByComparingTo("100.00");

        // Verify PO delivery status is now PARTIALLY_DELIVERED
        MvcResult poCheck1 = mockMvc.perform(get("/api/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        PurchaseOrderDto poPartial = objectMapper.readValue(poCheck1.getResponse().getContentAsString(), PurchaseOrderDto.class);
        assertThat(poPartial.getDeliveryStatus()).isEqualTo(PoDeliveryStatus.PARTIALLY_DELIVERED);

        // Deliver remaining cement (100) and all sand (50)
        RecordDeliveryRequest d2 = new RecordDeliveryRequest();
        d2.setPoLineId(line1Id);
        d2.setQuantityDelivered(new BigDecimal("100.00"));
        d2.setDeliveryReference("CHAL-1002");
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/deliveries")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(d2)))
                .andExpect(status().isCreated());

        Long line2Id = poDto.getLines().get(1).getId();
        RecordDeliveryRequest d3 = new RecordDeliveryRequest();
        d3.setPoLineId(line2Id);
        d3.setQuantityDelivered(new BigDecimal("50.00"));
        d3.setDeliveryReference("CHAL-1003");
        mockMvc.perform(post("/api/purchase-orders/" + poId + "/deliveries")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(d3)))
                .andExpect(status().isCreated());

        // Verify PO delivery status is now DELIVERED
        MvcResult poCheck2 = mockMvc.perform(get("/api/purchase-orders/" + poId)
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        PurchaseOrderDto poFull = objectMapper.readValue(poCheck2.getResponse().getContentAsString(), PurchaseOrderDto.class);
        assertThat(poFull.getDeliveryStatus()).isEqualTo(PoDeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("4. Actual Expense Recording & Cost Variance Analysis")
    void testExpenseRecordingAndCostVariance() throws Exception {
        Long projectId = projectAlpha.getId();

        // First set & approve budget: 200,000 INR
        SetBudgetRequest setReq = new SetBudgetRequest();
        setReq.setInitialBudget(new BigDecimal("200000.00"));
        setReq.setCurrency("INR");
        mockMvc.perform(post("/api/projects/" + projectId + "/budget")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setReq)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/" + projectId + "/budget/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk());

        // Record expenses
        RecordExpenseRequest e1 = new RecordExpenseRequest();
        e1.setCategory(CostCategory.MATERIALS);
        e1.setAmount(new BigDecimal("45000.00"));
        e1.setCurrency("INR");
        e1.setDescription("Payment for raw cement batch");
        mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(e1)))
                .andExpect(status().isCreated());

        RecordExpenseRequest e2 = new RecordExpenseRequest();
        e2.setCategory(CostCategory.LABOR);
        e2.setAmount(new BigDecimal("25000.00"));
        e2.setCurrency("INR");
        e2.setDescription("Weekly masons & helpers wages");
        mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(e2)))
                .andExpect(status().isCreated());

        RecordExpenseRequest e3 = new RecordExpenseRequest();
        e3.setCategory(CostCategory.EQUIPMENT);
        e3.setAmount(new BigDecimal("15000.00"));
        e3.setCurrency("INR");
        e3.setDescription("Excavator rental for site clearing");
        mockMvc.perform(post("/api/projects/" + projectId + "/expenses")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(e3)))
                .andExpect(status().isCreated());

        // Total actual = 45000 + 25000 + 15000 = 85000
        // Approved budget = 200,000
        // Remaining budget = 200,000 - 85,000 = 115,000
        // Cost variance = 85,000 - 200,000 = -115,000 (-57.5%)
        MvcResult summaryRes = mockMvc.perform(get("/api/projects/" + projectId + "/cost-summary")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId()))
                .andExpect(status().isOk())
                .andReturn();

        CostSummaryDto summary = objectMapper.readValue(summaryRes.getResponse().getContentAsString(), CostSummaryDto.class);
        assertThat(summary.getApprovedBudget()).isEqualByComparingTo("200000.00");
        assertThat(summary.getTotalActualCost()).isEqualByComparingTo("85000.00");
        assertThat(summary.getRemainingBudget()).isEqualByComparingTo("115000.00");
        assertThat(summary.getCostVariance()).isEqualByComparingTo("-115000.00");
        assertThat(summary.isOverBudget()).isFalse();

        assertThat(summary.getCategoryBreakdown().get(CostCategory.MATERIALS)).isEqualByComparingTo("45000.00");
        assertThat(summary.getCategoryBreakdown().get(CostCategory.LABOR)).isEqualByComparingTo("25000.00");
        assertThat(summary.getCategoryBreakdown().get(CostCategory.EQUIPMENT)).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("5. Tenant Boundary Enforcement on Budgets & Procurement")
    void testTenantIsolationOnCostAndProcurement() throws Exception {
        // Alpha project accessed with Beta organization header should be rejected (403 Forbidden)
        mockMvc.perform(get("/api/projects/" + projectAlpha.getId() + "/budget")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgBeta.getId()))
                .andExpect(status().isForbidden());

        // Attempting to create a quotation for Alpha project with Beta's vendor should be rejected
        CreateQuotationRequest qReq = new CreateQuotationRequest();
        qReq.setVendorId(vendorBeta.getId()); // Cross-tenant vendor!
        qReq.setQuoteReference("QT-CROSS-001");

        mockMvc.perform(post("/api/projects/" + projectAlpha.getId() + "/quotations")
                        .header("Authorization", "Bearer " + adminJwt)
                        .header("X-Organization-Id", orgAlpha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qReq)))
                .andExpect(status().isBadRequest());
    }
}
