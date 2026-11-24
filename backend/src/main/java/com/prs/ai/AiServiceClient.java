package com.prs.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class AiServiceClient {

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiServiceClient() {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5s connect timeout
        requestFactory.setReadTimeout(10000);   // 10s read timeout
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = new ObjectMapper();
    }

    public boolean isHealthy() {
        try {
            ResponseEntity<Map> res = restTemplate.getForEntity(aiServiceUrl + "/health", Map.class);
            return res.getStatusCode().is2xxSuccessful() && "healthy".equalsIgnoreCase((String) res.getBody().get("status"));
        } catch (Exception e) {
            log.warn("AI service health check failed at {}: {}", aiServiceUrl, e.getMessage());
            return false;
        }
    }

    public Map<String, Object> chat(Map<String, Object> requestPayload) {
        String endpoint = aiServiceUrl + "/api/assistant/chat";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("AI service chat request failed: {}", e.getMessage());
        }

        // Graceful fallback if AI service is temporarily unavailable
        return createFallbackChatResponse(requestPayload);
    }

    public Map<String, Object> explainScenario(Map<String, Object> requestPayload) {
        String endpoint = aiServiceUrl + "/api/assistant/explain-scenario";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("AI service scenario explanation request failed: {}", e.getMessage());
        }

        // Graceful fallback
        return createFallbackScenarioResponse(requestPayload);
    }

    private Map<String, Object> createFallbackChatResponse(Map<String, Object> requestPayload) {
        Map<String, Object> fallback = new HashMap<>();
        Map<String, Object> ctx = (Map<String, Object>) requestPayload.get("projectContext");
        String projectName = ctx != null ? (String) ctx.get("projectName") : "Project";
        String status = ctx != null ? (String) ctx.get("deadlineStatus") : "ON_TRACK";
        String forecast = ctx != null ? (String) ctx.get("currentForecastCompletion") : "N/A";

        fallback.put("answer", "Here is the verified project status for **" + projectName + "** (Fallback mode): Current status is **" + status + "** with forecast completion on **" + forecast + "**. (AI Service is running in offline fallback mode).");
        fallback.put("citations", List.of("Local Java Project State"));
        fallback.put("relevantTaskIds", Collections.emptyList());
        fallback.put("hypothetical", false);
        fallback.put("status", "DEGRADED_FALLBACK");
        return fallback;
    }

    private Map<String, Object> createFallbackScenarioResponse(Map<String, Object> requestPayload) {
        Map<String, Object> fallback = new HashMap<>();
        Map<String, Object> sim = (Map<String, Object>) requestPayload.get("simulationResult");
        String orig = sim != null ? (String) sim.get("originalForecast") : "N/A";
        String simForecast = sim != null ? (String) sim.get("simulatedForecast") : "N/A";

        fallback.put("explanation", "Deterministic simulation: Original forecast " + orig + " shifts to " + simForecast + ".");
        fallback.put("impactSummary", "Calculated by Java scheduling engine.");
        fallback.put("risksAndAssumptions", List.of("Simulation is hypothetical; active schedule was not changed."));
        fallback.put("recommendedNextStep", "Review with project manager.");
        fallback.put("citations", List.of("Java SchedulingEngine Simulation"));
        fallback.put("status", "DEGRADED_FALLBACK");
        return fallback;
    }
}
