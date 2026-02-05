package com.demo.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * AI 助手服务接口
 * 使用 LangChain4j AiService 注解
 * 支持会话记忆（通过 @MemoryId）
 */
public interface AiAssistant {

    @SystemMessage("你是一个智能知识库助手，负责回答用户问题。" +
            "请根据提供的上下文准确回答问题。" +
            "如果上下文中没有相关信息，请诚实地告知用户。" +
            "回答应简洁、准确、有帮助。请用中文回答。")
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);

    @SystemMessage("你是一个智能知识库助手，负责回答用户问题。" +
            "请根据提供的上下文准确回答问题。" +
            "如果上下文中没有相关信息，请诚实地告知用户。" +
            "回答应简洁、准确、有帮助。请用中文回答。")
    Flux<String> chatStream(@MemoryId String sessionId, @UserMessage String userMessage);

    @SystemMessage("你是一个智能知识库助手。" +
            "请根据以下检索到的文档内容回答用户问题：\n\n" +
            "{{context}}\n\n" +
            "请根据以上文档内容提供准确的回答。" +
            "如果文档中没有相关信息，请诚实告知。请用中文回答。")
    @UserMessage("{{question}}")
    String chatWithContext(@MemoryId String sessionId, @V("context") String context, @V("question") String question);

    @SystemMessage("你是一个智能知识库助手。" +
            "请根据以下检索到的文档内容回答用户问题：\n\n" +
            "{{context}}\n\n" +
            "请根据以上文档内容提供准确的回答。" +
            "如果文档中没有相关信息，请诚实告知。请用中文回答。")
    @UserMessage("{{question}}")
    Flux<String> chatStreamWithContext(@MemoryId String sessionId, @V("context") String context, @V("question") String question);
}
