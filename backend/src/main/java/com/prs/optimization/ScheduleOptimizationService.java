package com.prs.optimization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.cpm.CriticalPathResult;
import com.prs.cpm.CriticalPathService;
import com.prs.dto.*;
import com.prs.entity.*;
import com.prs.repository.*;
import com.prs.scheduler.ConflictDetector;
import com.prs.scheduler.ResourceConflict;
import com.prs.scheduler.SchedulingEngine;
import com.prs.scheduler.WorkingDayCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleOptimizationService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final WorkerRepository workerRepository;
    private final VendorRepository vendorRepository;
    private final OptimizationRunRepository optimizationRunRepository;
    private final ScheduleAlternativeRepository scheduleAlternativeRepository;
    private final OptimizationRecommendationRepository optimizationRecommendationRepository;
    private final OptimizationAuditLogRepository optimizationAuditLogRepository;

    private final CriticalPathService criticalPathService;
    private final SchedulingEngine schedulingEngine;
    private final ConflictDetector conflictDetector;
    private final WorkingDayCalculator workingDayCalculator;
    private final CandidateGenerator candidateGenerator;
    private final CandidateValidator candidateValidator;
    private final CandidateEvaluator candidateEvaluator;
    private final com.prs.service.NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public OptimizationRunDto runOptimization(Long projectId, String username, String selectedObjective) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<Task> tasks = taskRepository.findByProjectId(projectId);
        List<TaskDependency> dependencies = taskDependencyRepository.findBySuccessorTaskProjectId(projectId);
        List<TaskAssignment> projectAssignments = taskAssignmentRepository.findByTaskProjectId(projectId);
        List<TaskAssignment> allSystemAssignments = taskAssignmentRepository.findAll();
        List<Worker> allWorkers = workerRepository.findAll();
        List<Vendor> allVendors = vendorRepository.findAll();

        if (project.getOrganization() != null) {
            Long orgId = project.getOrganization().getId();
            allWorkers = allWorkers.stream()
                    .filter(w -> w.getOrganization() == null || w.getOrganization().getId().equals(orgId))
                    .collect(Collectors.toList());
            allVendors = allVendors.stream()
                    .filter(v -> v.getOrganization() == null || v.getOrganization().getId().equals(orgId))
                    .collect(Collectors.toList());
        }

        Map<Long, Worker> workerMap = allWorkers.stream().collect(Collectors.toMap(Worker::getId, w -> w));
        Map<Long, Vendor> vendorMap = allVendors.stream().collect(Collectors.toMap(Vendor::getId, v -> v));

        // Detect initial conflict count
        List<Task> allGlobalTasks = allSystemAssignments.stream()
                .map(TaskAssignment::getTask)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<ResourceConflict> initialConflicts = conflictDetector.detectConflicts(allGlobalTasks, allSystemAssignments);
        int initialConflictCount = initialConflicts.size();

        // Calculate CPM
        CriticalPathResult cpmResult = null;
        try {
            cpmResult = criticalPathService.calculate(project.getStartDate(), tasks, dependencies);
        } catch (Exception e) {
            log.warn("CPM calculation error for project {}: {}", projectId, e.getMessage());
        }

        LocalDate currentForecastCompletion = project.getExpectedCompletionDate();
        if (currentForecastCompletion == null) {
            schedulingEngine.calculateSchedule(project, tasks, dependencies);
            currentForecastCompletion = project.getExpectedCompletionDate();
        }

        // Generate bounded candidates
        List<OptimizationCandidate> candidates = candidateGenerator.generateCandidates(
                project, tasks, dependencies, projectAssignments, allSystemAssignments, allWorkers, allVendors, cpmResult);

        // Validate and evaluate each candidate
        List<OptimizationCandidate> validCandidates = new ArrayList<>();
        List<OptimizationCandidate> invalidCandidates = new ArrayList<>();

        for (OptimizationCandidate c : candidates) {
            var validation = candidateValidator.validate(c, tasks, dependencies, workerMap, vendorMap);
            if (!validation.isValid()) {
                c.setConstraintStatus(validation.getMessage());
                c.setObjectiveScore(0.0);
                invalidCandidates.add(c);
            } else {
                c.setConstraintStatus("VALID");
                candidateEvaluator.evaluate(c, project, tasks, dependencies, allSystemAssignments, initialConflictCount, currentForecastCompletion);
                validCandidates.add(c);
            }
        }

        // Rank valid candidates deterministically:
        // 1. Feasible & deadline met
        // 2. Highest objective score
        // 3. Fewer remaining conflicts
        // 4. Lowest disruption count
        validCandidates.sort((c1, c2) -> {
            boolean d1 = !"DELAYED".equals(c1.getDeadlineStatus());
            boolean d2 = !"DELAYED".equals(c2.getDeadlineStatus());
            if (d1 != d2) return d1 ? -1 : 1;

            int scoreComp = Double.compare(c2.getObjectiveScore(), c1.getObjectiveScore());
            if (scoreComp != 0) return scoreComp;

            int confComp = Integer.compare(c1.getRemainingConflicts(), c2.getRemainingConflicts());
            if (confComp != 0) return confComp;

            return Integer.compare(c1.getDisruptionCount(), c2.getDisruptionCount());
        });

        // Combine for ranking
        List<OptimizationCandidate> ranked = new ArrayList<>(validCandidates);
        ranked.addAll(invalidCandidates);

        int rank = 1;
        for (OptimizationCandidate c : ranked) {
            c.setRank(rank++);
        }

        // Save OptimizationRun
        OptimizationRun run = new OptimizationRun();
        run.setProject(project);
        run.setInitiatedBy(username != null ? username : "System");
        run.setSelectedObjective(selectedObjective != null ? selectedObjective : "BALANCED");
        run.setStatus("COMPLETED");
        run.setCandidatesEvaluated(ranked.size());
        run.setFeasibleCandidatesCount(validCandidates.size());
        run.setInputScheduleVersion("v" + System.currentTimeMillis());
        run.setCompletedAt(LocalDateTime.now());
        run = optimizationRunRepository.save(run);

        // Save alternatives
        List<ScheduleAlternative> savedAlternatives = new ArrayList<>();
        for (OptimizationCandidate c : ranked) {
            ScheduleAlternative alt = new ScheduleAlternative();
            alt.setOptimizationRun(run);
            alt.setAlternativeRank(c.getRank());
            alt.setTitle(c.getTitle());
            alt.setSummary(c.getSummary());

            try {
                alt.setProposedReassignments(objectMapper.writeValueAsString(c.getProposedWorkerReassignments()));
                alt.setProposedScheduleChanges(objectMapper.writeValueAsString(c.getProposedTaskStartDates()));
            } catch (JsonProcessingException e) {
                alt.setProposedReassignments("{}");
                alt.setProposedScheduleChanges("{}");
            }

            alt.setProjectCompletionForecast(c.getProjectCompletion() != null ? c.getProjectCompletion() : currentForecastCompletion);
            alt.setDeadlineStatus(c.getDeadlineStatus());
            alt.setWorkingDaysSaved(c.getWorkingDaysSaved());
            alt.setConflictsResolved(c.getConflictsResolved());
            alt.setRemainingConflicts(c.getRemainingConflicts());
            alt.setDisruptionCount(c.getDisruptionCount());
            alt.setObjectiveScore(c.getObjectiveScore());
            alt.setConstraintStatus(c.getConstraintStatus());
            alt.setTradeOffs(c.getTradeOffs());

            savedAlternatives.add(scheduleAlternativeRepository.save(alt));
        }

        // Generate and save OptimizationRecommendations
        if (!validCandidates.isEmpty()) {
            OptimizationCandidate topCandidate = validCandidates.get(0);
            ScheduleAlternative topAlt = savedAlternatives.get(0);

            OptimizationRecommendation rec = new OptimizationRecommendation();
            rec.setProject(project);
            rec.setOptimizationRun(run);
            rec.setScheduleAlternative(topAlt);
            rec.setCategory(topCandidate.getCategory());
            rec.setSummary(topCandidate.getTitle());
            rec.setExplanation(topCandidate.getExplanation() != null ? topCandidate.getExplanation() : topCandidate.getSummary());

            String impact = String.format("Conflicts resolved: %d | Remaining conflicts: %d | Days saved: %d | Forecast completion: %s | Status: %s",
                    topCandidate.getConflictsResolved(),
                    topCandidate.getRemainingConflicts(),
                    topCandidate.getWorkingDaysSaved(),
                    topCandidate.getProjectCompletion(),
                    topCandidate.getDeadlineStatus());
            rec.setEstimatedImpact(impact);
            rec.setStatus(RecommendationStatus.PROPOSED);
            optimizationRecommendationRepository.save(rec);
        }

        // Audit log
        OptimizationAuditLog audit = new OptimizationAuditLog();
        audit.setProject(project);
        audit.setRunId(run.getId());
        audit.setAction("OPTIMIZATION_RUN");
        audit.setPerformedBy(username != null ? username : "System");
        audit.setDetails("Executed deterministic schedule optimization. Evaluated " + ranked.size() + " candidates (" + validCandidates.size() + " feasible). Top score: " + (validCandidates.isEmpty() ? 0.0 : validCandidates.get(0).getObjectiveScore()));
        optimizationAuditLogRepository.save(audit);

        return mapToRunDto(run);
    }

    @Transactional(readOnly = true)
    public List<OptimizationRunDto> listRuns(Long projectId) {
        return optimizationRunRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::mapToRunDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OptimizationRunDto getRun(Long runId) {
        OptimizationRun run = optimizationRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Optimization run not found: " + runId));
        return mapToRunDto(run);
    }

    @Transactional(readOnly = true)
    public List<ScheduleAlternativeDto> getAlternatives(Long runId) {
        return scheduleAlternativeRepository.findByOptimizationRunIdOrderByAlternativeRankAsc(runId)
                .stream()
                .map(this::mapToAlternativeDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OptimizationRecommendationDto> listRecommendations(Long projectId) {
        return optimizationRecommendationRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::mapToRecommendationDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OptimizationRecommendationDto approveRecommendation(Long recommendationId, String username) {
        OptimizationRecommendation rec = optimizationRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if (rec.getStatus() != RecommendationStatus.PROPOSED) {
            throw new IllegalStateException("Only PROPOSED recommendations can be approved. Current status: " + rec.getStatus());
        }

        rec.setStatus(RecommendationStatus.APPROVED);
        rec.setApprovedBy(username != null ? username : "Manager");
        rec.setApprovedAt(LocalDateTime.now());
        rec = optimizationRecommendationRepository.save(rec);

        OptimizationAuditLog audit = new OptimizationAuditLog();
        audit.setProject(rec.getProject());
        audit.setRunId(rec.getOptimizationRun() != null ? rec.getOptimizationRun().getId() : null);
        audit.setRecommendationId(rec.getId());
        audit.setAction("RECOMMENDATION_APPROVED");
        audit.setPerformedBy(username != null ? username : "Manager");
        audit.setDetails("Approved recommendation #" + rec.getId() + " (" + rec.getSummary() + ")");
        optimizationAuditLogRepository.save(audit);

        return mapToRecommendationDto(rec);
    }

    @Transactional
    public OptimizationRecommendationDto rejectRecommendation(Long recommendationId, String username) {
        OptimizationRecommendation rec = optimizationRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if (rec.getStatus() == RecommendationStatus.APPLIED) {
            throw new IllegalStateException("Cannot reject an already APPLIED recommendation.");
        }

        rec.setStatus(RecommendationStatus.REJECTED);
        rec = optimizationRecommendationRepository.save(rec);

        OptimizationAuditLog audit = new OptimizationAuditLog();
        audit.setProject(rec.getProject());
        audit.setRunId(rec.getOptimizationRun() != null ? rec.getOptimizationRun().getId() : null);
        audit.setRecommendationId(rec.getId());
        audit.setAction("RECOMMENDATION_REJECTED");
        audit.setPerformedBy(username != null ? username : "Manager");
        audit.setDetails("Rejected recommendation #" + rec.getId());
        optimizationAuditLogRepository.save(audit);

        return mapToRecommendationDto(rec);
    }

    @Transactional
    public OptimizationRecommendationDto applyRecommendation(Long recommendationId, String username) {
        OptimizationRecommendation rec = optimizationRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        if (rec.getStatus() != RecommendationStatus.APPROVED) {
            throw new IllegalStateException("Recommendation must be APPROVED before applying. Current status: " + rec.getStatus());
        }

        ScheduleAlternative alt = rec.getScheduleAlternative();
        if (alt == null) {
            throw new IllegalStateException("No associated schedule alternative found for recommendation.");
        }

        Project project = rec.getProject();
        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        List<TaskDependency> dependencies = taskDependencyRepository.findBySuccessorTaskProjectId(project.getId());
        List<TaskAssignment> assignments = taskAssignmentRepository.findByTaskProjectId(project.getId());

        // Parse proposed reassignments
        try {
            Map<String, Object> reassignments = objectMapper.readValue(
                    alt.getProposedReassignments() != null ? alt.getProposedReassignments() : "{}", Map.class);

            for (Map.Entry<String, Object> entry : reassignments.entrySet()) {
                Long taskId = Long.parseLong(entry.getKey());
                Long newWorkerId = Long.parseLong(entry.getValue().toString());

                // Stale check 1: Target task still exists
                Task t = tasks.stream().filter(item -> item.getId().equals(taskId)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Recommendation is stale: target task #" + taskId + " no longer exists in project."));

                // Stale check 2: Completed tasks cannot be reassigned
                if (t.getStatus() == TaskStatus.COMPLETED) {
                    throw new IllegalStateException("Recommendation is stale: task '" + t.getName() + "' is already COMPLETED and cannot be modified.");
                }

                // Stale check 3: Recommended worker exists and is AVAILABLE
                Worker newWorker = workerRepository.findById(newWorkerId)
                        .orElseThrow(() -> new IllegalStateException("Recommendation is stale: worker #" + newWorkerId + " no longer exists."));

                if (newWorker.getStatus() == WorkerStatus.UNAVAILABLE) {
                    throw new IllegalStateException("Recommendation is stale: recommended worker '" + newWorker.getName() + "' is currently UNAVAILABLE.");
                }

                // Find existing assignment for this task
                Optional<TaskAssignment> existingOpt = assignments.stream()
                        .filter(a -> a.getTask().getId().equals(taskId))
                        .findFirst();

                if (existingOpt.isPresent()) {
                    TaskAssignment a = existingOpt.get();
                    a.setWorker(newWorker);
                    a.setVendor(null);
                    a.setAssignmentType(AssignmentType.WORKER);
                    taskAssignmentRepository.save(a);
                } else {
                    TaskAssignment a = new TaskAssignment();
                    a.setTask(t);
                    a.setWorker(newWorker);
                    a.setAssignmentType(AssignmentType.WORKER);
                    taskAssignmentRepository.save(a);
                }
            }

            // Recalculate schedule
            schedulingEngine.calculateSchedule(project, tasks, dependencies);
            taskRepository.saveAll(tasks);
            projectRepository.save(project);

            rec.setStatus(RecommendationStatus.APPLIED);
            rec.setAppliedAt(LocalDateTime.now());
            rec = optimizationRecommendationRepository.save(rec);

            // Audit record
            OptimizationAuditLog audit = new OptimizationAuditLog();
            audit.setProject(project);
            audit.setRunId(rec.getOptimizationRun() != null ? rec.getOptimizationRun().getId() : null);
            audit.setRecommendationId(rec.getId());
            audit.setAction("RECOMMENDATION_APPLIED");
            audit.setPerformedBy(username != null ? username : "Manager");
            audit.setDetails("Successfully applied recommendation #" + rec.getId() + ". Reassigned tasks: " + reassignments + ". New project completion: " + project.getExpectedCompletionDate());
            optimizationAuditLogRepository.save(audit);

            if (project.getManager() != null) {
                notificationService.sendNotification(
                        project.getManager(),
                        com.prs.entity.NotificationEventType.RECOMMENDATION_APPLIED,
                        "Schedule Recommendation Applied: #" + rec.getId(),
                        String.format("Applied recommendation #%d (%s). Schedule recalculated with expected completion: %s",
                                rec.getId(), rec.getCategory(), project.getExpectedCompletionDate()),
                        project.getId(),
                        null
                );
            }
        } catch (Exception e) {
            log.error("Failed to apply recommendation {}: {}", recommendationId, e.getMessage());
            throw new RuntimeException("Failed to apply recommendation: " + e.getMessage(), e);
        }

        return mapToRecommendationDto(rec);
    }

    @Transactional(readOnly = true)
    public TaskResourceRecommendationsResponse getTaskResourceRecommendations(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        List<Worker> allWorkers = workerRepository.findAll();
        List<Vendor> allVendors = vendorRepository.findAll();
        List<TaskAssignment> allAssignments = taskAssignmentRepository.findAll();

        TaskResourceRecommendationsResponse resp = new TaskResourceRecommendationsResponse();
        resp.setTaskId(task.getId());
        resp.setTaskName(task.getName());
        resp.setDurationDays(task.getEstimatedDuration());
        resp.setRequiredSkills(task.getRequiredSkills());
        resp.setPlannedStartDate(task.getPlannedStartDate());
        resp.setPlannedFinishDate(task.getPlannedFinishDate());

        // Find current assignment
        allAssignments.stream()
                .filter(a -> a.getTask().getId().equals(taskId))
                .findFirst()
                .ifPresent(a -> {
                    if (a.getWorker() != null) {
                        resp.setCurrentAssignee(a.getWorker().getName());
                        resp.setCurrentAssigneeType("WORKER");
                    } else if (a.getVendor() != null) {
                        resp.setCurrentAssignee(a.getVendor().getName());
                        resp.setCurrentAssigneeType("VENDOR");
                    }
                });

        // Evaluate workers
        List<ResourceCandidateDto> candidateWorkers = new ArrayList<>();
        String required = task.getRequiredSkills() != null ? task.getRequiredSkills() : "";

        for (Worker w : allWorkers) {
            ResourceCandidateDto c = new ResourceCandidateDto();
            c.setId(w.getId());
            c.setName(w.getName());
            c.setType("WORKER");
            c.setSkillsOrServices(w.getSkills());
            c.setExperience(w.getExperience());
            c.setDailyCapacity(w.getDailyCapacity());
            c.setAvailable(w.getStatus() == WorkerStatus.AVAILABLE);

            long activeCount = allAssignments.stream()
                    .filter(a -> a.getWorker() != null && a.getWorker().getId().equals(w.getId()))
                    .count();
            c.setActiveAssignmentsCount((int) activeCount);

            boolean hasSkills = candidateValidator.workerHasRequiredSkills(w, required);
            c.setHasRequiredSkills(hasSkills);

            // Calculate suitability score (0-100)
            double score = 0.0;
            if (hasSkills) score += 50.0;
            if (w.getStatus() == WorkerStatus.AVAILABLE) score += 30.0;
            if (w.getExperience() != null) score += Math.min(10.0, w.getExperience() * 2.0);
            if (activeCount == 0) score += 10.0;
            else score = Math.max(0.0, score - (activeCount * 3.0));

            c.setSuitabilityScore(Math.round(score * 10.0) / 10.0);

            if (hasSkills && w.getStatus() == WorkerStatus.AVAILABLE) {
                c.setRecommendationReason("Fully qualified with " + (w.getExperience() != null ? w.getExperience() : 3) + " yrs experience and matching skills [" + w.getSkills() + "].");
            } else if (!hasSkills) {
                c.setRecommendationReason("Missing mandatory skill requirements (" + required + ").");
            } else {
                c.setRecommendationReason("Worker currently unavailable (Status: " + w.getStatus() + ").");
            }

            candidateWorkers.add(c);
        }

        candidateWorkers.sort(Comparator.comparingDouble(ResourceCandidateDto::getSuitabilityScore).reversed());
        resp.setCandidateWorkers(candidateWorkers);

        // Evaluate vendors
        List<ResourceCandidateDto> candidateVendors = new ArrayList<>();
        for (Vendor v : allVendors) {
            ResourceCandidateDto c = new ResourceCandidateDto();
            c.setId(v.getId());
            c.setName(v.getName() + " (" + v.getCompany() + ")");
            c.setType("VENDOR");
            c.setSkillsOrServices(v.getServices());
            c.setAvailable("ACTIVE".equalsIgnoreCase(v.getStatus()));

            boolean match = false;
            if (v.getServices() != null && !required.isEmpty()) {
                for (String part : required.split("[,;]")) {
                    if (v.getServices().toLowerCase().contains(part.trim().toLowerCase())) {
                        match = true;
                        break;
                    }
                }
            }
            c.setHasRequiredSkills(match);
            double score = match ? 75.0 : 30.0;
            if ("ACTIVE".equalsIgnoreCase(v.getStatus())) score += 20.0;
            c.setSuitabilityScore(score);
            c.setRecommendationReason(match ? "Vendor services match task scope: " + v.getServices() : "Vendor capabilities do not match required skills.");
            candidateVendors.add(c);
        }
        candidateVendors.sort(Comparator.comparingDouble(ResourceCandidateDto::getSuitabilityScore).reversed());
        resp.setCandidateVendors(candidateVendors);

        return resp;
    }

    @Transactional(readOnly = true)
    public List<OptimizationAuditLogDto> listAuditLogs(Long projectId) {
        return optimizationAuditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(this::mapToAuditLogDto)
                .collect(Collectors.toList());
    }

    private OptimizationRunDto mapToRunDto(OptimizationRun r) {
        OptimizationRunDto dto = new OptimizationRunDto();
        dto.setId(r.getId());
        dto.setProjectId(r.getProject().getId());
        dto.setProjectName(r.getProject().getName());
        dto.setInitiatedBy(r.getInitiatedBy());
        dto.setSelectedObjective(r.getSelectedObjective());
        dto.setStatus(r.getStatus());
        dto.setCandidatesEvaluated(r.getCandidatesEvaluated());
        dto.setFeasibleCandidatesCount(r.getFeasibleCandidatesCount());
        dto.setInputScheduleVersion(r.getInputScheduleVersion());
        dto.setCreatedAt(r.getCreatedAt());
        dto.setCompletedAt(r.getCompletedAt());
        return dto;
    }

    private ScheduleAlternativeDto mapToAlternativeDto(ScheduleAlternative a) {
        ScheduleAlternativeDto dto = new ScheduleAlternativeDto();
        dto.setId(a.getId());
        dto.setOptimizationRunId(a.getOptimizationRun().getId());
        dto.setAlternativeRank(a.getAlternativeRank());
        dto.setTitle(a.getTitle());
        dto.setSummary(a.getSummary());
        dto.setProposedReassignments(a.getProposedReassignments());
        dto.setProposedScheduleChanges(a.getProposedScheduleChanges());
        dto.setProjectCompletionForecast(a.getProjectCompletionForecast());
        dto.setDeadlineStatus(a.getDeadlineStatus());
        dto.setWorkingDaysSaved(a.getWorkingDaysSaved());
        dto.setConflictsResolved(a.getConflictsResolved());
        dto.setRemainingConflicts(a.getRemainingConflicts());
        dto.setDisruptionCount(a.getDisruptionCount());
        dto.setObjectiveScore(a.getObjectiveScore());
        dto.setConstraintStatus(a.getConstraintStatus());
        dto.setTradeOffs(a.getTradeOffs());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private OptimizationRecommendationDto mapToRecommendationDto(OptimizationRecommendation r) {
        OptimizationRecommendationDto dto = new OptimizationRecommendationDto();
        dto.setId(r.getId());
        dto.setProjectId(r.getProject().getId());
        dto.setOptimizationRunId(r.getOptimizationRun() != null ? r.getOptimizationRun().getId() : null);
        dto.setAlternativeId(r.getScheduleAlternative() != null ? r.getScheduleAlternative().getId() : null);
        dto.setCategory(r.getCategory());
        dto.setSummary(r.getSummary());
        dto.setExplanation(r.getExplanation());
        dto.setEstimatedImpact(r.getEstimatedImpact());
        dto.setStatus(r.getStatus());
        dto.setApprovedBy(r.getApprovedBy());
        dto.setApprovedAt(r.getApprovedAt());
        dto.setAppliedAt(r.getAppliedAt());
        dto.setCreatedAt(r.getCreatedAt());
        if (r.getScheduleAlternative() != null) {
            dto.setAlternative(mapToAlternativeDto(r.getScheduleAlternative()));
        }
        return dto;
    }

    private OptimizationAuditLogDto mapToAuditLogDto(OptimizationAuditLog a) {
        OptimizationAuditLogDto dto = new OptimizationAuditLogDto();
        dto.setId(a.getId());
        dto.setProjectId(a.getProject().getId());
        dto.setRunId(a.getRunId());
        dto.setRecommendationId(a.getRecommendationId());
        dto.setAction(a.getAction());
        dto.setPerformedBy(a.getPerformedBy());
        dto.setDetails(a.getDetails());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
