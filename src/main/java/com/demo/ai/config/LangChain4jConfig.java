package com.demo.ai.config;

import com.demo.ai.service.AiAssistant;
import com.demo.ai.tools.FileSystemTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /**
     * AI 助手服务
     * 集成本地 FileSystemTool（兜底）和 MCP 远程工具
     */
    @Bean
    public AiAssistant aiAssistant(ChatModel chatModel,
                                   StreamingChatModel streamingChatModel,
                                   FileSystemTool fileSystemTool) {
        AiServices<AiAssistant> builder = AiServices.builder(AiAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(fileSystemTool);  // 本地文件系统工具作为兜底

        // 注入统一的 MCP 工具提供者
        if (mcpToolProvider != null) {
            builder.toolProvider(mcpToolProvider);
            log.info("Unified MCP ToolProvider registered to AiAssistant");
        } else {
            log.warn("No MCP ToolProvider enabled. Only local @Tool classes are available.");
        }

        return builder.build();
    }
}
