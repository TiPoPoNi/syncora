package com.prs.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prs.dto.*;
import com.prs.entity.AssistantChatMessage;
import com.prs.entity.Project;
import com.prs.repository.AssistantChatMessageRepository;
import com.prs.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final ProjectRepository projectRepository;
    private final AssistantChatMessageRepository chatMessageRepository;
    private final ProjectContextBuilder projectContextBuilder;
    private final ScenarioSimulationEngine simulationEngine;
    private final AiServiceClient aiServiceClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AiChatResponseDto chat(Long projectId, String username, String userMessage) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("User message cannot be empty");
        }

        // 1. Save user message
        AssistantChatMessage userMsg = new AssistantChatMessage();
        userMsg.setProject(project);
        userMsg.setUsername(username != null ? username : "User");
        userMsg.setRole("user");
        userMsg.setContent(userMessage.trim());
        userMsg.setHypothetical(false);
        chatMessageRepository.save(userMsg);

        // 2. Build verified project context
        Map<String, Object> projectContext = projectContextBuilder.buildProjectContext(projectId);

        // 3. Get recent conversation history
        List<AssistantChatMessage> history = chatMessageRepository.findByProjectIdAndUsernameOrderByCreatedAtAsc(projectId, username);
        List<Map<String, String>> conversationHistory = history.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getHypothetical()))
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        // 4. Prepare payload for Python AI service
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectContext", projectContext);
        payload.put("userMessage", userMessage.trim());
        payload.put("conversationHistory", conversationHistory);

        // 5. Call AI service
        Map<String, Object> aiResponse = aiServiceClient.chat(payload);

        String answer = (String) aiResponse.getOrDefault("answer", "No response generated.");
        List<String> citations = (List<String>) aiResponse.getOrDefault("citations", Collections.emptyList());
        List<Object> rawTaskIds = (List<Object>) aiResponse.getOrDefault("relevantTaskIds", Collections.emptyList());
        List<Long> relevantTaskIds = rawTaskIds.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList());
        String snapshotVersion = (String) aiResponse.getOrDefault("dataSnapshotVersion", "v1.0");
        String status = (String) aiResponse.getOrDefault("status", "SUCCESS");

        // 6. Save assistant message
        AssistantChatMessage assistantMsg = new AssistantChatMessage();
        assistantMsg.setProject(project);
        assistantMsg.setUsername(username != null ? username : "User");
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        try {
            assistantMsg.setCitations(objectMapper.writeValueAsString(citations));
        } catch (JsonProcessingException e) {
            assistantMsg.setCitations("[]");
        }
        assistantMsg.setHypothetical(false);
        chatMessageRepository.save(assistantMsg);

        AiChatResponseDto responseDto = new AiChatResponseDto();
        responseDto.setAnswer(answer);
        responseDto.setCitations(citations);
        responseDto.setRelevantTaskIds(relevantTaskIds);
        responseDto.setHypothetical(false);
        responseDto.setDataSnapshotVersion(snapshotVersion);
        responseDto.setStatus(status);

        return responseDto;
    }

    @Transactional
    public ScenarioSimulationResponseDto simulateScenario(Long projectId, String username, ScenarioSimulationRequestDto request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // 1. Calculate deterministic simulation without saving
        ScenarioSimulationResponseDto simResult = simulationEngine.simulate(projectId, request);

        // 2. Build verified project context
        Map<String, Object> projectContext = projectContextBuilder.buildProjectContext(projectId);

        // 3. Prepare payload for AI service explanation
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectContext", projectContext);
        payload.put("scenarioParameters", Map.of(
                "scenarioType", request.getScenarioType() != null ? request.getScenarioType() : "TASK_DELAY",
                "taskId", simResult.getTargetTaskId(),
                "taskName", simResult.getTargetTaskName(),
                "additionalDelayDays", request.getAdditionalDelayDays() != null ? request.getAdditionalDelayDays() : 0,
                "delayReason", request.getDelayReason() != null ? request.getDelayReason() : "Hypothetical Scenario"
        ));
        payload.put("simulationResult", Map.of(
                "originalForecast", simResult.getOriginalForecast(),
                "simulatedForecast", simResult.getSimulatedForecast(),
                "workingDaysDifference", simResult.getWorkingDaysDifference(),
                "originalDeadlineStatus", simResult.getOriginalDeadlineStatus(),
                "simulatedDeadlineStatus", simResult.getSimulatedDeadlineStatus(),
                "clientDeadline", simResult.getClientDeadline(),
                "affectedTasks", simResult.getAffectedTasks(),
                "conflictsResolvedCount", simResult.getConflictsResolvedCount(),
                "newConflictsCount", simResult.getNewConflictsCount(),
                "isHypothetical", true
        ));

        // 4. Request AI grounded explanation
        Map<String, Object> aiResponse = aiServiceClient.explainScenario(payload);

        simResult.setAiExplanation((String) aiResponse.getOrDefault("explanation", "Scenario simulation calculated."));
        simResult.setImpactSummary((String) aiResponse.getOrDefault("impactSummary", "Deterministic schedule variance calculated."));
        simResult.setRisksAndAssumptions((List<String>) aiResponse.getOrDefault("risksAndAssumptions", List.of("Hypothetical calculation; active schedule was not modified.")));
        simResult.setRecommendedNextStep((String) aiResponse.getOrDefault("recommendedNextStep", "Review scenario with project manager."));
        simResult.setCitations((List<String>) aiResponse.getOrDefault("citations", List.of("Java SchedulingEngine Simulation")));

        // 5. Save record in chat history with hypothetical flag
        AssistantChatMessage simMsg = new AssistantChatMessage();
        simMsg.setProject(project);
        simMsg.setUsername(username != null ? username : "User");
        simMsg.setRole("assistant");
        simMsg.setContent("[HYPOTHETICAL SCENARIO]\n" + simResult.getAiExplanation());
        try {
            simMsg.setCitations(objectMapper.writeValueAsString(simResult.getCitations()));
        } catch (Exception ignored) {}
        simMsg.setHypothetical(true);
        chatMessageRepository.save(simMsg);

        return simResult;
    }

    @Transactional(readOnly = true)
    public List<AiChatMessageDto> getHistory(Long projectId, String username) {
        return chatMessageRepository.findByProjectIdAndUsernameOrderByCreatedAtAsc(projectId, username)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void clearHistory(Long projectId, String username) {
        chatMessageRepository.deleteByProjectIdAndUsername(projectId, username);
    }

    private AiChatMessageDto mapToDto(AssistantChatMessage m) {
        AiChatMessageDto dto = new AiChatMessageDto();
        dto.setId(m.getId());
        dto.setProjectId(m.getProject().getId());
        dto.setUsername(m.getUsername());
        dto.setRole(m.getRole());
        dto.setContent(m.getContent());
        dto.setHypothetical(m.getHypothetical());
        dto.setCreatedAt(m.getCreatedAt());

        if (m.getCitations() != null && !m.getCitations().isBlank()) {
            try {
                dto.setCitations(objectMapper.readValue(m.getCitations(), List.class));
            } catch (Exception e) {
                dto.setCitations(Collections.emptyList());
            }
        } else {
            dto.setCitations(Collections.emptyList());
        }
        return dto;
    }
}
