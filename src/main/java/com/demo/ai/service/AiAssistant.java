package com.demo.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * AI 助手服务接口
 * 使用 LangChain4j AiService 注解
 */
public interface AiAssistant {

    @SystemMessage("You are an intelligent knowledge base assistant responsible for answering user questions. " +
            "Please answer questions accurately based on the provided context. " +
            "If there is no relevant information in the context, please honestly inform the user. " +
            "Answers should be concise, accurate, and helpful. Please respond in Chinese.")
    String chat(@UserMessage String userMessage);

    @SystemMessage("You are an intelligent knowledge base assistant responsible for answering user questions. " +
            "Please answer questions accurately based on the provided context. " +
            "If there is no relevant information in the context, please honestly inform the user. " +
            "Answers should be concise, accurate, and helpful. Please respond in Chinese.")
    Flux<String> chatStream(@UserMessage String userMessage);

    @SystemMessage("You are an intelligent knowledge base assistant. " +
            "Please answer user questions based on the following retrieved document content:\n\n" +
            "{{context}}\n\n" +
            "Please provide an accurate answer based on the above document content. " +
            "If there is no relevant information in the documents, please honestly inform. Please respond in Chinese.")
    @UserMessage("{{question}}")
    String chatWithContext(@V("context") String context, @V("question") String question);

    @SystemMessage("You are an intelligent knowledge base assistant. " +
            "Please answer user questions based on the following retrieved document content:\n\n" +
            "{{context}}\n\n" +
            "Please provide an accurate answer based on the above document content. " +
            "If there is no relevant information in the documents, please honestly inform. Please respond in Chinese.")
    @UserMessage("{{question}}")
    Flux<String> chatStreamWithContext(@V("context") String context, @V("question") String question);
}
