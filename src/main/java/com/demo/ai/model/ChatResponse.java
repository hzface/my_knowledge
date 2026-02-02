package com.demo.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String sessionId;
    private String messageId;
    private String content;
    private Boolean success;
    private String error;

    public static ChatResponse success(String sessionId, String messageId, String content) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .messageId(messageId)
                .content(content)
                .success(true)
                .build();
    }

    public static ChatResponse error(String error) {
        return ChatResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
