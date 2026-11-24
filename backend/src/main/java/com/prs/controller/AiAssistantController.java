package com.prs.controller;

import com.prs.ai.AiAssistantService;
import com.prs.ai.AiServiceClient;
import com.prs.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/assistant")
@RequiredArgsConstructor

public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiServiceClient aiServiceClient;

    // 1. Send chat message to AI Assistant
    @PostMapping("/messages")
    public ResponseEntity<AiChatResponseDto> sendMessage(
            @PathVariable Long projectId,
            @RequestBody AiChatRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ProjectManager";
        return ResponseEntity.ok(aiAssistantService.chat(projectId, username, request.getMessage()));
    }

    // 2. Simulate what-if hypothetical scenario
    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioSimulationResponseDto> simulateScenario(
            @PathVariable Long projectId,
            @RequestBody ScenarioSimulationRequestDto request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ProjectManager";
        return ResponseEntity.ok(aiAssistantService.simulateScenario(projectId, username, request));
    }

    // 3. Get user-scoped chat history
    @GetMapping("/history")
    public ResponseEntity<List<AiChatMessageDto>> getHistory(
            @PathVariable Long projectId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ProjectManager";
        return ResponseEntity.ok(aiAssistantService.getHistory(projectId, username));
    }

    // 4. Clear chat history
    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory(
            @PathVariable Long projectId,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "ProjectManager";
        aiAssistantService.clearHistory(projectId, username);
        return ResponseEntity.ok(Map.of("message", "Chat history cleared successfully."));
    }

    // 5. Check AI Service status
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@PathVariable Long projectId) {
        boolean healthy = aiServiceClient.isHealthy();
        return ResponseEntity.ok(Map.of(
                "aiServiceAvailable", healthy,
                "status", healthy ? "ONLINE" : "OFFLINE_FALLBACK",
                "provider", "FastAPI Grounded AI Engine"
        ));
    }
}
