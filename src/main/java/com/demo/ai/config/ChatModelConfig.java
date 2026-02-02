package com.demo.ai.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 聊天模型配置
 * 支持 OpenAI 和 Ollama 两种模型提供者
 * 包含详细的请求/响应日志
 */
@Slf4j
@Configuration
public class ChatModelConfig {

    @Value("${app.llm.provider:openai}")
    private String llmProvider;

    @Value("${app.llm.log-requests:true}")
    private boolean logRequests;

    @Value("${app.llm.log-responses:true}")
    private boolean logResponses;

    // OpenAI 配置
    @Value("${langchain4j.openai.chat-model.api-key:}")
    private String openaiApiKey;

    @Value("${langchain4j.openai.chat-model.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${langchain4j.openai.chat-model.model-name:gpt-4}")
    private String openaiModelName;

    // Ollama 配置
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model-name:qwen3:4b}")
    private String ollamaModelName;

    /**
     * 自定义 OkHttpClient，确保正确处理 UTF-8 编码
     */
    @Bean
    public OkHttpClient customOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 聊天模型监听器 - 记录请求和响应日志
     */
    @Bean
    public ChatModelListener chatModelListener() {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                if (logRequests) {
                    log.info("========== LLM Request ==========");
                    log.info("Messages: ");
                    context.chatRequest().messages().forEach(msg -> {
                        String text = getMessageText(msg);
//                        log.info("  [{}]: {}", msg.type(), truncate(text, 500));
                    });
                    if (context.chatRequest().toolSpecifications() != null &&
                        !context.chatRequest().toolSpecifications().isEmpty()) {
                        log.info("Tools: {}", context.chatRequest().toolSpecifications().stream()
                            .map(t -> t.name()).toList());
                    }
                    log.info("==================================");
                }
            }

            @Override
            public void onResponse(ChatModelResponseContext context) {
                if (logResponses) {
                    log.info("========== LLM Response ==========");
                    var response = context.chatResponse();
                    AiMessage aiMessage = response.aiMessage();
                    log.info("Content: {}", truncate(aiMessage.text(), 500));
                    if (aiMessage.toolExecutionRequests() != null &&
                        !aiMessage.toolExecutionRequests().isEmpty()) {
                        log.info("Tool Calls: {}", aiMessage.toolExecutionRequests().stream()
                            .map(t -> t.name() + "(" + truncate(t.arguments(), 100) + ")").toList());
                    }
                    if (response.tokenUsage() != null) {
                        log.info("Token Usage: input={}, output={}, total={}",
                            response.tokenUsage().inputTokenCount(),
                            response.tokenUsage().outputTokenCount(),
                            response.tokenUsage().totalTokenCount());
                    }
                    log.info("===================================");
                }
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                log.error("========== LLM Error ==========");
                log.error("Error: {}", context.error().getMessage(), context.error());
                log.error("================================");
            }

            private String getMessageText(ChatMessage msg) {
                if (msg instanceof UserMessage userMsg) {
                    return userMsg.singleText();
                } else if (msg instanceof AiMessage aiMsg) {
                    return aiMsg.text();
                } else if (msg instanceof SystemMessage sysMsg) {
                    return sysMsg.text();
                }
                return msg.toString();
            }

            private String truncate(String text, int maxLength) {
                if (text == null) return "null";
                if (text.length() <= maxLength) return text;
                return text.substring(0, maxLength) + "... [truncated, total " + text.length() + " chars]";
            }
        };
    }

    @Bean
    @Primary
    public ChatModel chatModel(ChatModelListener chatModelListener, OkHttpClient okHttpClient) {
        log.info("Configuring chat model with provider: {}", llmProvider);
        log.info("Log requests: {}, Log responses: {}", logRequests, logResponses);

        if ("ollama".equalsIgnoreCase(llmProvider)) {
            return createOllamaChatModel(chatModelListener);
        } else {
            return createOpenAiChatModel(chatModelListener, okHttpClient);
        }
    }

    @Bean
    @Primary
    public StreamingChatModel streamingChatModel(ChatModelListener chatModelListener, OkHttpClient okHttpClient) {
        log.info("Configuring streaming chat model with provider: {}", llmProvider);

        if ("ollama".equalsIgnoreCase(llmProvider)) {
            return createOllamaStreamingChatModel(chatModelListener);
        } else {
            return createOpenAiStreamingChatModel(chatModelListener, okHttpClient);
        }
    }

    private ChatModel createOpenAiChatModel(ChatModelListener listener, OkHttpClient okHttpClient) {
        log.info("Creating OpenAI chat model: {} at {}", openaiModelName, openaiBaseUrl);
        return OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModelName)
                .timeout(Duration.ofSeconds(120))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(listener))
                .customHeaders(java.util.Map.of("Accept-Charset", "UTF-8"))
                .build();
    }

    private StreamingChatModel createOpenAiStreamingChatModel(ChatModelListener listener, OkHttpClient okHttpClient) {
        log.info("Creating OpenAI streaming chat model: {} at {}", openaiModelName, openaiBaseUrl);
        return OpenAiStreamingChatModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModelName)
                .timeout(Duration.ofSeconds(120))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(listener))
                .customHeaders(java.util.Map.of("Accept-Charset", "UTF-8"))
                .build();
    }

    private ChatModel createOllamaChatModel(ChatModelListener listener) {
        log.info("Creating Ollama chat model: {} at {}", ollamaModelName, ollamaBaseUrl);
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .timeout(Duration.ofSeconds(180))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(listener))
                .build();
    }

    private StreamingChatModel createOllamaStreamingChatModel(ChatModelListener listener) {
        log.info("Creating Ollama streaming chat model: {} at {}", ollamaModelName, ollamaBaseUrl);
        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .timeout(Duration.ofSeconds(180))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(listener))
                .build();
    }
}
