package com.demo.ai.controller;

import com.demo.ai.model.ChatRequest;
import com.demo.ai.model.ChatResponse;
import com.demo.ai.model.ChatSession;
import com.demo.ai.service.ChatService;
import com.demo.ai.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天控制器
 * 提供聊天 API 接口
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SessionService sessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public ChatSession createSession() {
        return sessionService.createSession();
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/session/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId);
    }

    /**
     * 获取所有会话列表
     */
    @GetMapping("/sessions")
    public List<ChatSession> getAllSessions() {
        return sessionService.getAllSessions();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{sessionId}/title")
    public void updateSessionTitle(@PathVariable String sessionId, @RequestBody String title) {
        sessionService.updateTitle(sessionId, title);
    }

    /**
     * 发送消息（非流式）
     */
    @PostMapping("/message")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    /**
     * 发送消息（流式 SSE）
     */
    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history/{sessionId}")
    public List<?> getHistory(@PathVariable String sessionId) {
        return sessionService.getHistory(sessionId);
    }
}
