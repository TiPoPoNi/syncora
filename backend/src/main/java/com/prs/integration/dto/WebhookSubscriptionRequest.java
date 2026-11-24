package com.prs.integration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Destination URL is required")
    private String destinationUrl;

    private String secret; // Optional: If empty, a secure 32-byte secret is generated

    private Boolean isEnabled;

    private List<String> subscribedEvents;
}
