package com.prs.service;

import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverableManagementService {

    private final DeliverableRepository deliverableRepository;
    private final DeliverableDocumentRepository documentRepository;
    private final DeliverableApprovalRepository approvalRepository;
    private final TaskApprovalGateRepository taskGateRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final OptimizationAuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final ProjectScheduleService projectScheduleService;

    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "docx", "xlsx", "zip", "txt", "csv", "md"
    );

    private final Path storageDirectory = Paths.get("data", "documents").toAbsolutePath().normalize();

    // ==========================================
    // 1. Deliverable CRUD & Retrieval
    // ==========================================

    @Transactional(readOnly = true)
    public List<DeliverableDto> getProjectDeliverables(Long projectId, String username) {
        Project project = getValidatedProject(projectId);
        User user = userRepository.findByUsername(username).orElse(null);

        List<Deliverable> deliverables;
        if (user != null && user.getRole() == Role.CLIENT) {
            // Client users only view non-confidential client-visible deliverables
            validateClientProjectAccess(project, user);
            deliverables = deliverableRepository.findByProjectIdAndIsClientVisibleTrueAndIsConfidentialFalse(projectId);
        } else {
            deliverables = deliverableRepository.findByProjectId(projectId);
        }

        return deliverables.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DeliverableDto getDeliverableById(Long deliverableId, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.getRole() == Role.CLIENT) {
            validateClientProjectAccess(deliverable.getProject(), user);
            if (Boolean.TRUE.equals(deliverable.getIsConfidential()) || Boolean.FALSE.equals(deliverable.getIsClientVisible())) {
                throw new TenantAccessDeniedException("Client is not authorized to view this deliverable.");
            }
        }

        return mapToDto(deliverable);
    }

    @Transactional
    public DeliverableDto createDeliverable(Long projectId, CreateDeliverableRequest req, String username) {
        Project project = getValidatedProject(projectId);

        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Deliverable name is required.");
        }

        Task task = null;
        if (req.getTaskId() != null) {
            task = taskRepository.findById(req.getTaskId())
                    .orElseThrow(() -> new IllegalArgumentException("Associated task not found: " + req.getTaskId()));
            if (!task.getProject().getId().equals(project.getId())) {
                throw new IllegalArgumentException("Task does not belong to the deliverable's project.");
            }
        }

        User responsibleUser = null;
        if (req.getResponsibleUserId() != null) {
            responsibleUser = userRepository.findById(req.getResponsibleUserId()).orElse(null);
        }

        User assignedReviewer = null;
        if (req.getAssignedReviewerId() != null) {
            assignedReviewer = userRepository.findById(req.getAssignedReviewerId()).orElse(null);
        }

        Deliverable deliverable = new Deliverable();
        deliverable.setProject(project);
        deliverable.setTask(task);
        deliverable.setName(req.getName().trim());
        deliverable.setDescription(req.getDescription());
        deliverable.setDeliverableType(req.getDeliverableType() != null ? req.getDeliverableType() : DeliverableType.TECHNICAL_DOCUMENT);
        deliverable.setResponsibleUser(responsibleUser);
        deliverable.setAssignedReviewer(assignedReviewer);
        deliverable.setRequiredSubmissionDate(req.getRequiredSubmissionDate());
        deliverable.setRequestedDecisionDate(req.getRequestedDecisionDate());
        deliverable.setStatus(DeliverableStatus.PLANNED);
        deliverable.setCurrentVersionNumber(1);
        deliverable.setIsClientVisible(req.getIsClientVisible() != null ? req.getIsClientVisible() : true);
        deliverable.setIsConfidential(req.getIsConfidential() != null ? req.getIsConfidential() : false);

        deliverable = deliverableRepository.save(deliverable);
        recordAudit(project, "DELIVERABLE_CREATED", username, "Created deliverable '" + deliverable.getName() + "' (Type: " + deliverable.getDeliverableType() + ")");

        return mapToDto(deliverable);
    }

    @Transactional
    public DeliverableDto updateDeliverable(Long deliverableId, UpdateDeliverableRequest req, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        if (req.getName() != null && !req.getName().isBlank()) {
            deliverable.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            deliverable.setDescription(req.getDescription());
        }
        if (req.getDeliverableType() != null) {
            deliverable.setDeliverableType(req.getDeliverableType());
        }
        if (req.getTaskId() != null) {
            Task task = taskRepository.findById(req.getTaskId()).orElse(null);
            deliverable.setTask(task);
        }
        if (req.getResponsibleUserId() != null) {
            deliverable.setResponsibleUser(userRepository.findById(req.getResponsibleUserId()).orElse(null));
        }
        if (req.getAssignedReviewerId() != null) {
            deliverable.setAssignedReviewer(userRepository.findById(req.getAssignedReviewerId()).orElse(null));
        }
        if (req.getRequiredSubmissionDate() != null) {
            deliverable.setRequiredSubmissionDate(req.getRequiredSubmissionDate());
        }
        if (req.getRequestedDecisionDate() != null) {
            deliverable.setRequestedDecisionDate(req.getRequestedDecisionDate());
        }
        if (req.getIsClientVisible() != null) {
            deliverable.setIsClientVisible(req.getIsClientVisible());
        }
        if (req.getIsConfidential() != null) {
            deliverable.setIsConfidential(req.getIsConfidential());
        }

        // Status transition handling
        if (req.getStatus() != null && req.getStatus() != deliverable.getStatus()) {
            validateStatusTransition(deliverable, req.getStatus());
            deliverable.setStatus(req.getStatus());
        }

        deliverable = deliverableRepository.save(deliverable);
        recordAudit(deliverable.getProject(), "DELIVERABLE_UPDATED", username, "Updated deliverable '" + deliverable.getName() + "'");
        return mapToDto(deliverable);
    }

    // ==========================================
    // 2. Document Upload, Versioning & Storage
    // ==========================================

    @Transactional
    public DeliverableDocumentDto uploadDocument(Long deliverableId, MultipartFile file, String versionNotes, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 25 MB.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        // Path traversal defense
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new IllegalArgumentException("Filename contains invalid path sequences: " + originalFilename);
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type '." + extension + "' is not supported. Allowed: " + ALLOWED_EXTENSIONS);
        }

        // Ensure storage directory exists
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize document storage location.", e);
        }

        // Generate unique server-side storage key
        String storageKey = UUID.randomUUID().toString() + "_" + originalFilename;
        Path targetLocation = storageDirectory.resolve(storageKey).normalize();

        // Verify target path remains within storage directory
        if (!targetLocation.startsWith(storageDirectory)) {
            throw new SecurityException("Storage path traversal attempted.");
        }

        // Save file & calculate checksum
        String checksum;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                Files.copy(dis, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            checksum = bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Failed to store file or compute checksum.", e);
        }

        // Determine next version number
        int nextVersionNumber = documentRepository.countByDeliverableId(deliverable.getId()) + 1;

        DeliverableDocument doc = new DeliverableDocument();
        doc.setDeliverable(deliverable);
        doc.setVersionNumber(nextVersionNumber);
        doc.setOriginalFilename(originalFilename);
        doc.setStorageKey(storageKey);
        doc.setFilePath(targetLocation.toString());
        doc.setFileSize(file.getSize());
        doc.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setChecksum(checksum);
        doc.setVersionNotes(versionNotes);
        doc.setSubmissionStatus("SUBMITTED");
        doc.setUploadedBy(username != null ? username : "anonymous");
        doc = documentRepository.save(doc);

        deliverable.setCurrentVersionNumber(nextVersionNumber);
        if (deliverable.getStatus() == DeliverableStatus.APPROVED || deliverable.getStatus() == DeliverableStatus.CHANGES_REQUESTED) {
            // New version on an approved or changes-requested deliverable requires re-review; previous approval remains preserved in approval history
            deliverable.setStatus(DeliverableStatus.UNDER_REVIEW);
            recordAudit(deliverable.getProject(), "DELIVERABLE_REVISION_UPLOADED", username,
                    "New version v" + nextVersionNumber + " uploaded for deliverable '" + deliverable.getName() + "'. Deliverable reset to UNDER_REVIEW.");
        } else if (deliverable.getStatus() == DeliverableStatus.PLANNED) {
            deliverable.setStatus(DeliverableStatus.IN_PROGRESS);
        }
        deliverableRepository.save(deliverable);

        recordAudit(deliverable.getProject(), "DOCUMENT_UPLOADED", username,
                "Uploaded v" + nextVersionNumber + " (" + originalFilename + ", " + file.getSize() + " bytes) for deliverable '" + deliverable.getName() + "'");

        return mapToDocumentDto(doc);
    }

    @Transactional(readOnly = true)
    public List<DeliverableDocumentDto> getDeliverableDocuments(Long deliverableId, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());
        return documentRepository.findByDeliverableIdOrderByVersionNumberDesc(deliverableId).stream()
                .map(this::mapToDocumentDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Resource downloadDocument(Long deliverableId, Long documentId, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && user.getRole() == Role.CLIENT) {
            validateClientProjectAccess(deliverable.getProject(), user);
            if (Boolean.TRUE.equals(deliverable.getIsConfidential()) || Boolean.FALSE.equals(deliverable.getIsClientVisible())) {
                throw new TenantAccessDeniedException("Client is not authorized to download confidential documents.");
            }
        }

        DeliverableDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (!doc.getDeliverable().getId().equals(deliverable.getId())) {
            throw new IllegalArgumentException("Document does not belong to this deliverable.");
        }

        try {
            Path filePath = Paths.get(doc.getFilePath()).normalize();
            if (!filePath.startsWith(storageDirectory)) {
                throw new SecurityException("Illegal file access attempted outside storage directory.");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalStateException("Requested file could not be read from storage.");
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException("File URL error: " + e.getMessage(), e);
        }
    }

    // ==========================================
    // 3. Workflow Transitions: Submit & Review
    // ==========================================

    @Transactional
    public DeliverableDto submitDeliverable(Long deliverableId, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        if (documentRepository.countByDeliverableId(deliverableId) == 0) {
            throw new IllegalStateException("Cannot submit deliverable without at least one uploaded document attachment.");
        }

        deliverable.setStatus(DeliverableStatus.SUBMITTED);
        deliverable.setActualSubmissionDate(LocalDate.now());
        deliverable = deliverableRepository.save(deliverable);

        // Notify reviewer or project manager
        User recipient = deliverable.getAssignedReviewer() != null ? deliverable.getAssignedReviewer() : deliverable.getProject().getManager();
        if (recipient != null) {
            notificationService.sendNotification(
                    recipient,
                    NotificationEventType.DELIVERABLE_SUBMITTED,
                    "Deliverable Submitted for Review",
                    "Deliverable '" + deliverable.getName() + "' (v" + deliverable.getCurrentVersionNumber() + ") in project '" + deliverable.getProject().getName() + "' was submitted by " + username + ".",
                    deliverable.getProject().getId(),
                    deliverable.getTask() != null ? deliverable.getTask().getId() : null
            );
        }

        recordAudit(deliverable.getProject(), "DELIVERABLE_SUBMITTED", username,
                "Submitted deliverable '" + deliverable.getName() + "' (v" + deliverable.getCurrentVersionNumber() + ")");
        return mapToDto(deliverable);
    }

    @Transactional
    public DeliverableDto assignReviewer(Long deliverableId, Long reviewerId, LocalDate requestedDecisionDate, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer user not found: " + reviewerId));

        deliverable.setAssignedReviewer(reviewer);
        if (requestedDecisionDate != null) {
            deliverable.setRequestedDecisionDate(requestedDecisionDate);
        }
        deliverable.setStatus(DeliverableStatus.UNDER_REVIEW);
        deliverable = deliverableRepository.save(deliverable);

        notificationService.sendNotification(
                reviewer,
                NotificationEventType.DELIVERABLE_REVIEW_ASSIGNED,
                "Deliverable Review Assignment",
                "You have been assigned to review deliverable '" + deliverable.getName() + "' in project '" + deliverable.getProject().getName() + "'. Target Decision Date: " + (deliverable.getRequestedDecisionDate() != null ? deliverable.getRequestedDecisionDate() : "Immediate"),
                deliverable.getProject().getId(),
                deliverable.getTask() != null ? deliverable.getTask().getId() : null
        );

        recordAudit(deliverable.getProject(), "DELIVERABLE_REVIEW_ASSIGNED", username,
                "Assigned review of '" + deliverable.getName() + "' to user '" + reviewer.getUsername() + "'");
        return mapToDto(deliverable);
    }

    // ==========================================
    // 4. Approval Decisions & Governance
    // ==========================================

    @Transactional
    public DeliverableApprovalDto recordApprovalDecision(Long deliverableId, ApprovalDecisionRequest req, String username) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        User reviewer = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + username));

        // Anti-self-approval rule: responsible user cannot approve their own deliverable unless global ADMIN or Client
        if (deliverable.getResponsibleUser() != null
                && deliverable.getResponsibleUser().getId().equals(reviewer.getId())
                && req.getDecision() == ApprovalDecision.APPROVED
                && reviewer.getRole() != Role.ADMIN
                && reviewer.getRole() != Role.CLIENT) {
            throw new IllegalStateException("Self-approval prohibited: Responsible user cannot approve their own deliverable.");
        }

        DeliverableStatus prevStatus = deliverable.getStatus();
        DeliverableStatus resultingStatus;
        NotificationEventType eventType;

        switch (req.getDecision()) {
            case APPROVED -> {
                resultingStatus = DeliverableStatus.APPROVED;
                eventType = NotificationEventType.DELIVERABLE_APPROVED;
            }
            case CHANGES_REQUESTED -> {
                resultingStatus = DeliverableStatus.CHANGES_REQUESTED;
                eventType = NotificationEventType.DELIVERABLE_CHANGES_REQUESTED;
            }
            case REJECTED -> {
                resultingStatus = DeliverableStatus.REJECTED;
                eventType = NotificationEventType.DELIVERABLE_REJECTED;
            }
            default -> throw new IllegalArgumentException("Invalid approval decision: " + req.getDecision());
        }

        DeliverableDocument targetDoc = null;
        if (req.getDocumentId() != null) {
            targetDoc = documentRepository.findById(req.getDocumentId()).orElse(null);
        } else {
            List<DeliverableDocument> docs = documentRepository.findByDeliverableIdOrderByVersionNumberDesc(deliverableId);
            if (!docs.isEmpty()) {
                targetDoc = docs.get(0);
            }
        }

        if (targetDoc != null) {
            targetDoc.setSubmissionStatus(resultingStatus.name());
            documentRepository.save(targetDoc);
        }

        // Calculate turnaround hours
        BigDecimal turnaroundHours = null;
        if (deliverable.getActualSubmissionDate() != null) {
            long days = ChronoUnit.DAYS.between(deliverable.getActualSubmissionDate(), LocalDate.now());
            turnaroundHours = BigDecimal.valueOf(Math.max(1, days * 24)).setScale(2, RoundingMode.HALF_UP);
        }

        DeliverableApproval approval = new DeliverableApproval();
        approval.setDeliverable(deliverable);
        approval.setDocument(targetDoc);
        approval.setReviewer(reviewer);
        approval.setDecision(req.getDecision());
        approval.setDecisionDate(LocalDateTime.now());
        approval.setComments(req.getComments());
        approval.setPreviousStatus(prevStatus);
        approval.setResultingStatus(resultingStatus);
        approval.setTurnaroundHours(turnaroundHours);
        approval = approvalRepository.save(approval);

        // Update deliverable
        deliverable.setStatus(resultingStatus);
        if (req.getDecision() == ApprovalDecision.APPROVED) {
            deliverable.setActualDecisionDate(LocalDate.now());
            // Check if any dependent tasks have an approval gate satisfied
            notifyGatedTasksOnApproval(deliverable);
        }
        deliverableRepository.save(deliverable);

        // Notify responsible user or PM
        User notifyRecipient = deliverable.getResponsibleUser() != null ? deliverable.getResponsibleUser() : deliverable.getProject().getManager();
        if (notifyRecipient != null) {
            notificationService.sendNotification(
                    notifyRecipient,
                    eventType,
                    "Deliverable " + req.getDecision().name().replace('_', ' '),
                    "Deliverable '" + deliverable.getName() + "' decision: " + req.getDecision() + ". Reviewer comments: " + (req.getComments() != null ? req.getComments() : "None"),
                    deliverable.getProject().getId(),
                    deliverable.getTask() != null ? deliverable.getTask().getId() : null
            );
        }

        recordAudit(deliverable.getProject(), "APPROVAL_DECISION_" + req.getDecision(), username,
                "Recorded decision '" + req.getDecision() + "' on deliverable '" + deliverable.getName() + "' (Comments: " + req.getComments() + ")");

        return mapToApprovalDto(approval);
    }

    @Transactional(readOnly = true)
    public List<DeliverableApprovalDto> getDeliverableApprovals(Long deliverableId) {
        return approvalRepository.findByDeliverableIdOrderByDecisionDateDesc(deliverableId).stream()
                .map(this::mapToApprovalDto).collect(Collectors.toList());
    }

    // ==========================================
    // 5. Task Completion Gates
    // ==========================================

    @Transactional
    public TaskApprovalGateDto configureTaskGate(Long taskId, ConfigureTaskGateRequest req, String username) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Deliverable deliverable = deliverableRepository.findById(req.getDeliverableId())
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + req.getDeliverableId()));

        if (!task.getProject().getId().equals(deliverable.getProject().getId())) {
            throw new IllegalArgumentException("Task and Deliverable must belong to the same project.");
        }
        validateProjectTenant(task.getProject());

        GateType gateType = req.getGateType() != null ? req.getGateType() : GateType.COMPLETION_GATE;
        if (taskGateRepository.existsByTaskIdAndDeliverableIdAndGateType(taskId, deliverable.getId(), gateType)) {
            throw new IllegalArgumentException("Approval gate already exists for this task and deliverable.");
        }

        TaskApprovalGate gate = new TaskApprovalGate();
        gate.setTask(task);
        gate.setDeliverable(deliverable);
        gate.setGateType(gateType);
        gate.setIsMandatory(req.getIsMandatory() != null ? req.getIsMandatory() : true);
        gate = taskGateRepository.save(gate);

        recordAudit(task.getProject(), "TASK_APPROVAL_GATE_CONFIGURED", username,
                "Configured gate: Task '" + task.getName() + "' requires deliverable '" + deliverable.getName() + "' approval (" + gateType + ")");
        return mapToGateDto(gate);
    }

    @Transactional(readOnly = true)
    public List<TaskApprovalGateDto> getTaskGates(Long taskId) {
        return taskGateRepository.findByTaskId(taskId).stream()
                .map(this::mapToGateDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteTaskGate(Long gateId, String username) {
        TaskApprovalGate gate = taskGateRepository.findById(gateId)
                .orElseThrow(() -> new IllegalArgumentException("Gate not found: " + gateId));
        validateProjectTenant(gate.getTask().getProject());
        taskGateRepository.deleteById(gateId);
        recordAudit(gate.getTask().getProject(), "TASK_APPROVAL_GATE_REMOVED", username,
                "Removed gate between task '" + gate.getTask().getName() + "' and deliverable '" + gate.getDeliverable().getName() + "'");
    }

    /**
     * Gate Validator: called during task completion to ensure mandatory deliverable gates are satisfied.
     */
    @Transactional(readOnly = true)
    public void validateTaskCanBeCompleted(Task task) {
        List<TaskApprovalGate> gates = taskGateRepository.findByTaskId(task.getId());
        for (TaskApprovalGate gate : gates) {
            if (gate.getGateType() == GateType.COMPLETION_GATE && Boolean.TRUE.equals(gate.getIsMandatory())) {
                Deliverable deliverable = gate.getDeliverable();
                if (deliverable.getStatus() != DeliverableStatus.APPROVED) {
                    throw new IllegalStateException("Task '" + task.getName() + "' cannot be marked COMPLETED because mandatory deliverable '"
                            + deliverable.getName() + "' is not approved. Current status: " + deliverable.getStatus());
                }
            }
        }
    }

    /**
     * Gate Validator: called during task start to ensure mandatory start gates are satisfied.
     */
    @Transactional(readOnly = true)
    public void validateTaskCanStart(Task task) {
        List<TaskApprovalGate> gates = taskGateRepository.findByTaskId(task.getId());
        for (TaskApprovalGate gate : gates) {
            if (gate.getGateType() == GateType.START_GATE && Boolean.TRUE.equals(gate.getIsMandatory())) {
                Deliverable deliverable = gate.getDeliverable();
                if (deliverable.getStatus() != DeliverableStatus.APPROVED) {
                    throw new IllegalStateException("Task '" + task.getName() + "' cannot start because prerequisite deliverable '"
                            + deliverable.getName() + "' is not approved. Current status: " + deliverable.getStatus());
                }
            }
        }
    }

    private void notifyGatedTasksOnApproval(Deliverable deliverable) {
        List<TaskApprovalGate> gates = taskGateRepository.findByDeliverableId(deliverable.getId());
        for (TaskApprovalGate gate : gates) {
            Task task = gate.getTask();
            User pm = task.getProject().getManager();
            if (pm != null) {
                notificationService.sendNotification(
                        pm,
                        NotificationEventType.APPROVAL_GATE_UNBLOCKED,
                        "Task Approval Gate Satisfied",
                        "Deliverable '" + deliverable.getName() + "' was approved. Dependent task '" + task.getName() + "' is now unblocked.",
                        task.getProject().getId(),
                        task.getId()
                );
            }
        }
    }

    // ==========================================
    // 6. Approval Overdue & Schedule Impact Analysis
    // ==========================================

    @Transactional(readOnly = true)
    public ApprovalDelayImpactDto getApprovalDelayImpact(Long deliverableId) {
        Deliverable deliverable = deliverableRepository.findById(deliverableId)
                .orElseThrow(() -> new IllegalArgumentException("Deliverable not found: " + deliverableId));
        validateProjectTenant(deliverable.getProject());

        ApprovalDelayImpactDto dto = new ApprovalDelayImpactDto();
        dto.setDeliverableId(deliverable.getId());
        dto.setDeliverableName(deliverable.getName());
        dto.setRequestedDecisionDate(deliverable.getRequestedDecisionDate());

        LocalDate now = LocalDate.now();
        boolean isOverdue = deliverable.getRequestedDecisionDate() != null
                && now.isAfter(deliverable.getRequestedDecisionDate())
                && (deliverable.getStatus() == DeliverableStatus.SUBMITTED || deliverable.getStatus() == DeliverableStatus.UNDER_REVIEW);

        dto.setIsOverdue(isOverdue);
        long daysOverdue = isOverdue ? ChronoUnit.DAYS.between(deliverable.getRequestedDecisionDate(), now) : 0;
        dto.setDaysOverdue(daysOverdue);

        List<TaskApprovalGate> gates = taskGateRepository.findByDeliverableId(deliverableId);
        dto.setBlockedTasksCount(gates.size());
        dto.setBlockedTaskNames(gates.stream().map(g -> g.getTask().getName()).collect(Collectors.toList()));

        // CPM impact check
        boolean criticalPathImpact = false;
        try {
            CriticalPathDto cpm = projectScheduleService.getCriticalPath(deliverable.getProject().getId());
            Set<Long> cpTaskIds = new HashSet<>(cpm.getCriticalTaskIds());
            for (TaskApprovalGate gate : gates) {
                if (cpTaskIds.contains(gate.getTask().getId())) {
                    criticalPathImpact = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Could not calculate CPM for deliverable {}: {}", deliverableId, e.getMessage());
        }

        dto.setImpactsCriticalPath(criticalPathImpact);
        dto.setPotentialScheduleSlipDays(criticalPathImpact ? (int) daysOverdue : 0);

        return dto;
    }

    // ==========================================
    // 7. Helper Mappers & Validation
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

    private void validateClientProjectAccess(Project project, User clientUser) {
        if (project.getClient() != null && clientUser.getEmail() != null) {
            if (!clientUser.getEmail().equalsIgnoreCase(project.getClient().getEmail())) {
                // Client must match project client email
                throw new TenantAccessDeniedException("Client user is not associated with this project.");
            }
        }
    }

    private void validateStatusTransition(Deliverable deliverable, DeliverableStatus targetStatus) {
        DeliverableStatus current = deliverable.getStatus();
        if (current == targetStatus) return;

        // Valid transitions
        boolean valid = switch (current) {
            case PLANNED -> targetStatus == DeliverableStatus.IN_PROGRESS || targetStatus == DeliverableStatus.CANCELLED;
            case IN_PROGRESS -> targetStatus == DeliverableStatus.SUBMITTED || targetStatus == DeliverableStatus.CANCELLED;
            case SUBMITTED -> targetStatus == DeliverableStatus.UNDER_REVIEW || targetStatus == DeliverableStatus.CANCELLED;
            case UNDER_REVIEW -> targetStatus == DeliverableStatus.APPROVED || targetStatus == DeliverableStatus.CHANGES_REQUESTED
                    || targetStatus == DeliverableStatus.REJECTED || targetStatus == DeliverableStatus.CANCELLED;
            case CHANGES_REQUESTED -> targetStatus == DeliverableStatus.IN_PROGRESS || targetStatus == DeliverableStatus.SUBMITTED
                    || targetStatus == DeliverableStatus.CANCELLED;
            case REJECTED, APPROVED, CANCELLED -> false; // Terminal or requires new document revision
        };

        if (!valid) {
            throw new IllegalStateException("Invalid deliverable transition from " + current + " to " + targetStatus);
        }
    }

    private void recordAudit(Project project, String action, String username, String details) {
        OptimizationAuditLog audit = new OptimizationAuditLog();
        audit.setProject(project);
        audit.setAction(action);
        audit.setPerformedBy(username != null ? username : "System");
        audit.setDetails(details);
        auditLogRepository.save(audit);
    }

    private DeliverableDto mapToDto(Deliverable d) {
        DeliverableDto dto = new DeliverableDto();
        dto.setId(d.getId());
        dto.setProjectId(d.getProject().getId());
        dto.setProjectName(d.getProject().getName());
        if (d.getTask() != null) {
            dto.setTaskId(d.getTask().getId());
            dto.setTaskName(d.getTask().getName());
        }
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setDeliverableType(d.getDeliverableType());
        if (d.getResponsibleUser() != null) {
            dto.setResponsibleUserId(d.getResponsibleUser().getId());
            dto.setResponsibleUsername(d.getResponsibleUser().getUsername());
        }
        if (d.getAssignedReviewer() != null) {
            dto.setAssignedReviewerId(d.getAssignedReviewer().getId());
            dto.setAssignedReviewerUsername(d.getAssignedReviewer().getUsername());
        }
        dto.setRequiredSubmissionDate(d.getRequiredSubmissionDate());
        dto.setActualSubmissionDate(d.getActualSubmissionDate());
        dto.setRequestedDecisionDate(d.getRequestedDecisionDate());
        dto.setActualDecisionDate(d.getActualDecisionDate());
        dto.setStatus(d.getStatus());
        dto.setCurrentVersionNumber(d.getCurrentVersionNumber());
        dto.setIsClientVisible(d.getIsClientVisible());
        dto.setIsConfidential(d.getIsConfidential());

        boolean isOverdue = d.getRequestedDecisionDate() != null
                && LocalDate.now().isAfter(d.getRequestedDecisionDate())
                && (d.getStatus() == DeliverableStatus.SUBMITTED || d.getStatus() == DeliverableStatus.UNDER_REVIEW);
        dto.setIsOverdue(isOverdue);

        dto.setDocumentsCount(documentRepository.countByDeliverableId(d.getId()));
        dto.setApprovalsCount(approvalRepository.findByDeliverableIdOrderByDecisionDateDesc(d.getId()).size());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());
        return dto;
    }

    private DeliverableDocumentDto mapToDocumentDto(DeliverableDocument doc) {
        DeliverableDocumentDto dto = new DeliverableDocumentDto();
        dto.setId(doc.getId());
        dto.setDeliverableId(doc.getDeliverable().getId());
        dto.setVersionNumber(doc.getVersionNumber());
        dto.setOriginalFilename(doc.getOriginalFilename());
        dto.setStorageKey(doc.getStorageKey());
        dto.setFileSize(doc.getFileSize());
        dto.setContentType(doc.getContentType());
        dto.setChecksum(doc.getChecksum());
        dto.setVersionNotes(doc.getVersionNotes());
        dto.setSubmissionStatus(doc.getSubmissionStatus());
        dto.setUploadedBy(doc.getUploadedBy());
        dto.setUploadedAt(doc.getUploadedAt());
        return dto;
    }

    private DeliverableApprovalDto mapToApprovalDto(DeliverableApproval a) {
        DeliverableApprovalDto dto = new DeliverableApprovalDto();
        dto.setId(a.getId());
        dto.setDeliverableId(a.getDeliverable().getId());
        if (a.getDocument() != null) {
            dto.setDocumentId(a.getDocument().getId());
            dto.setDocumentVersionNumber(a.getDocument().getVersionNumber());
        }
        if (a.getReviewer() != null) {
            dto.setReviewerId(a.getReviewer().getId());
            dto.setReviewerUsername(a.getReviewer().getUsername());
        }
        dto.setDecision(a.getDecision());
        dto.setDecisionDate(a.getDecisionDate());
        dto.setComments(a.getComments());
        dto.setPreviousStatus(a.getPreviousStatus());
        dto.setResultingStatus(a.getResultingStatus());
        dto.setTurnaroundHours(a.getTurnaroundHours());
        return dto;
    }

    private TaskApprovalGateDto mapToGateDto(TaskApprovalGate g) {
        TaskApprovalGateDto dto = new TaskApprovalGateDto();
        dto.setId(g.getId());
        dto.setTaskId(g.getTask().getId());
        dto.setTaskName(g.getTask().getName());
        dto.setDeliverableId(g.getDeliverable().getId());
        dto.setDeliverableName(g.getDeliverable().getName());
        dto.setDeliverableStatus(g.getDeliverable().getStatus());
        dto.setGateType(g.getGateType());
        dto.setIsMandatory(g.getIsMandatory());
        dto.setIsSatisfied(g.getDeliverable().getStatus() == DeliverableStatus.APPROVED);
        return dto;
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
