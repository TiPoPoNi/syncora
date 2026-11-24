package com.prs.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiChatResponseDto {
    private String role = "assistant";
    private String answer;
    private String content;
    private List<String> citations;
    private List<Long> relevantTaskIds;
    private boolean hypothetical = false;
    private String dataSnapshotVersion;
    private String status = "SUCCESS";

    public void setAnswer(String answer) {
        this.answer = answer;
        this.content = answer;
    }
}
