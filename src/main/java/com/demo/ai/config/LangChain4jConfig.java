package com.demo.ai.config;

import com.demo.ai.service.AiAssistant;
import com.demo.ai.tools.FileSystemTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * LangChain4j 配置类
 * 配置 AI 助手服务
 * 集成本地 @Tool 工具和 MCP 远程工具
 *
 * 注意：EmbeddingStore 由 MilvusConfig 配置
 * 使用 Milvus 作为向量数据库存储
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    // 统一的 MCP 工具提供者（可选注入）
    @Autowired(required = false)
    @Qualifier("mcpToolProvider")
    private ToolProvider mcpToolProvider;

    @Value("${app.session.max-history:10}")
    private int maxHistoryMessages;

    @Value("${app.session.timeout:3600}")
    private long sessionTimeout;

    /**
     * 聊天记忆存储
     * 使用 Redis 存储，支持分布式和持久化
     */
    @Bean
    public ChatMemoryStore chatMemoryStore(StringRedisTemplate stringRedisTemplate) {
        log.info("Initializing Redis ChatMemoryStore with TTL: {} seconds", sessionTimeout);
        return new RedisChatMemoryStore(stringRedisTemplate, sessionTimeout);
    }

    /**
     * AI 助手服务
     * 集成本地 FileSystemTool（兜底）和 MCP 远程工具
     * 启用会话记忆功能（基于 Redis 存储）
     */
    @Bean
    public AiAssistant aiAssistant(ChatModel chatModel,
                                   StreamingChatModel streamingChatModel,
                                   FileSystemTool fileSystemTool,
                                   ChatMemoryStore chatMemoryStore) {
        AiServices<AiAssistant> builder = AiServices.builder(AiAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(fileSystemTool)  // 本地文件系统工具作为兜底
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxHistoryMessages)
                        .chatMemoryStore(chatMemoryStore)
                        .build());

        // 注入统一的 MCP 工具提供者
        if (mcpToolProvider != null) {
            builder.toolProvider(mcpToolProvider);
            log.info("Unified MCP ToolProvider registered to AiAssistant");
        } else {
            log.warn("No MCP ToolProvider enabled. Only local @Tool classes are available.");
        }

        log.info("ChatMemory enabled with Redis storage, max {} messages per session", maxHistoryMessages);
        return builder.build();
    }
}
