package com.demo.ai.service;

import com.demo.ai.model.ChatMessage;
import com.demo.ai.model.ChatRequest;
import com.demo.ai.model.ChatResponse;
import com.demo.ai.model.ChatSession;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 聊天服务
 * 整合会话管理、RAG 检索和 AI 对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SessionService sessionService;
    private final AiAssistant aiAssistant;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 普通聊天（非流式）
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            // 获取或创建会话
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                ChatSession session = sessionService.createSession();
                sessionId = session.getId();
            }

            // 保存用户消息
            ChatMessage userMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .role("user")
                    .content(request.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            sessionService.addMessage(sessionId, userMessage);

            // RAG 检索相关文档
            String context = retrieveContext(request.getMessage());

            // 调用 AI 获取回复
            String response;
            if (context != null && !context.isEmpty()) {
                response = aiAssistant.chatWithContext(context, request.getMessage());
            } else {
                response = aiAssistant.chat(request.getMessage());
            }

            // 保存 AI 回复
            String messageId = UUID.randomUUID().toString();
            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(messageId)
                    .sessionId(sessionId)
                    .role("assistant")
                    .content(response)
                    .timestamp(LocalDateTime.now())
                    .build();
            sessionService.addMessage(sessionId, assistantMessage);

            return ChatResponse.success(sessionId, messageId, response);

        } catch (Exception e) {
            log.error("Chat error", e);
            return ChatResponse.error(parseErrorMessage(e));
        }
    }

    /**
     * 流式聊天
     * 返回 SSE 流，并在完成后保存完整回复
     */
    public Flux<ServerSentEvent<String>> chatStream(ChatRequest request) {
        // 获取或创建会话
        final String sessionId;
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            ChatSession session = sessionService.createSession();
            sessionId = session.getId();
        } else {
            sessionId = request.getSessionId();
        }

        // 保存用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .role("user")
                .content(request.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        sessionService.addMessage(sessionId, userMessage);

        // RAG 检索相关文档
        String context = retrieveContext(request.getMessage());

        // 用于收集完整回复
        StringBuilder fullResponse = new StringBuilder();
        String messageId = UUID.randomUUID().toString();

        // 先发送 sessionId 信息
        Flux<ServerSentEvent<String>> sessionInfo = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("session")
                        .data(sessionId)
                        .build()
        );

        // 流式调用 AI（根据是否有上下文选择不同方法）
        Flux<String> aiResponse;
        if (context != null && !context.isEmpty()) {
            log.info("流式聊天使用 RAG 上下文");
            aiResponse = aiAssistant.chatStreamWithContext(context, request.getMessage());
        } else {
            log.info("流式聊天无 RAG 上下文");
            aiResponse = aiAssistant.chatStream(request.getMessage());
        }

        Flux<ServerSentEvent<String>> aiStream = aiResponse
                .doOnNext(chunk -> {
                    fullResponse.append(chunk);
                })
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build())
                .onErrorResume(e -> {
                    // 错误时发送错误信息给前端
                    log.error("Stream chat error: {}", e.getMessage());
                    // 尝试解析错误信息中的中文
                    String errorMsg = parseErrorMessage(e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(errorMsg)
                            .build());
                })
                .doOnComplete(() -> {
                    // 流结束后保存完整的 AI 回复
                    if (fullResponse.length() > 0) {
                        try {
                            ChatMessage assistantMessage = ChatMessage.builder()
                                    .id(messageId)
                                    .sessionId(sessionId)
                                    .role("assistant")
                                    .content(fullResponse.toString())
                                    .timestamp(LocalDateTime.now())
                                    .build();
                            sessionService.addMessage(sessionId, assistantMessage);
                            log.info("Stream completed, saved AI response for session: {}", sessionId);
                        } catch (Exception e) {
                            log.error("Failed to save stream response", e);
                        }
                    }
                });

        // 最后发送完成事件
        Flux<ServerSentEvent<String>> doneEvent = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("complete")
                        .build()
        );

        return Flux.concat(sessionInfo, aiStream, doneEvent);
    }

    /**
     * RAG 检索相关文档上下文
     */
    private String retrieveContext(String query) {
        try {
            log.info("RAG 检索开始，查询: {}", query);
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 使用 LangChain4j 1.0.x API
            // 降低 minScore 阈值以提高召回率
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(5)
                    .minScore(0.1)  // 降低阈值，提高召回率
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

            List<EmbeddingMatch<TextSegment>> matches = result.matches();
            log.info("RAG 检索结果数量: {}", matches.size());

            if (matches.isEmpty()) {
                log.info("RAG 未检索到相关文档");
                return null;
            }

            // 打印检索到的内容和分数
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<TextSegment> match = matches.get(i);
                log.info("RAG 结果 {}: 分数={}, 内容前100字={}",
                    i + 1,
                    match.score(),
                    match.embedded().text().substring(0, Math.min(100, match.embedded().text().length())));
            }

            return matches.stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.warn("RAG retrieval failed", e);
            return null;
        }
    }

    /**
     * 解析错误信息，处理可能的编码问题
     */
    private String parseErrorMessage(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return "抱歉，AI 服务暂时不可用";
        }

        // 尝试修复 ISO-8859-1 编码的中文
        try {
            // 检查是否包含乱码特征（常见的 UTF-8 被错误解码为 ISO-8859-1 的模式）
            if (message.contains("æ") || message.contains("ç") || message.contains("è") || message.contains("é")) {
                byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                // 验证解码后是否包含中文
                if (decoded.matches(".*[\\u4e00-\\u9fa5]+.*")) {
                    message = decoded;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to re-decode error message", ex);
        }

        // 尝试从 JSON 中提取错误信息
        if (message.contains("\"message\"")) {
            try {
                int start = message.indexOf("\"message\"");
                if (start >= 0) {
                    int colonPos = message.indexOf(":", start);
                    int quoteStart = message.indexOf("\"", colonPos + 1);
                    int quoteEnd = message.indexOf("\"", quoteStart + 1);
                    if (quoteStart >= 0 && quoteEnd > quoteStart) {
                        String extracted = message.substring(quoteStart + 1, quoteEnd);
                        // 再次尝试修复编码
                        try {
                            byte[] bytes = extracted.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                            if (decoded.matches(".*[\\u4e00-\\u9fa5]+.*")) {
                                return "抱歉，AI 服务暂时不可用：" + decoded;
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                        if (!extracted.isEmpty()) {
                            return "抱歉，AI 服务暂时不可用：" + extracted;
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to extract message from JSON", ex);
            }
        }

        // 检查是否是限流错误
        if (message.contains("1302") || message.toLowerCase().contains("rate") || message.contains("并发")) {
            return "API 调用频率过高，请稍后再试";
        }

        return "抱歉，AI 服务暂时不可用：" + message;
    }
}
