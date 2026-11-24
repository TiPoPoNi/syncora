package com.prs.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiChatMessageDto {
    private Long id;
    private Long projectId;
    private String username;
    private String role; // "user" or "assistant"
    private String content;
    private List<String> citations;
    private Boolean hypothetical;
    private LocalDateTime createdAt;
}
