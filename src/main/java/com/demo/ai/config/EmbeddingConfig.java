package com.demo.ai.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * 向量化模型配置
 * 支持 OpenAI 和 Ollama 两种嵌入模型提供者
 * 包含详细的请求/响应日志
 */
@Slf4j
@Configuration
public class EmbeddingConfig {

    @Value("${app.embedding.provider:openai}")
    private String embeddingProvider;

    @Value("${app.embedding.log-requests:true}")
    private boolean logRequests;

    @Value("${app.embedding.log-responses:true}")
    private boolean logResponses;

    // OpenAI 配置
    @Value("${langchain4j.openai.embedding-model.api-key:}")
    private String openaiApiKey;

    @Value("${langchain4j.openai.embedding-model.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${langchain4j.openai.embedding-model.model-name:text-embedding-3-small}")
    private String openaiModelName;

    // Ollama 配置
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model.model-name:nomic-embed-text}")
    private String ollamaEmbeddingModel;

    @Value("${ollama.embedding-model.timeout:300}")
    private int ollamaTimeout;

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        log.info("Configuring embedding model with provider: {}", embeddingProvider);
        log.info("Embedding log requests: {}, log responses: {}", logRequests, logResponses);

        if ("ollama".equalsIgnoreCase(embeddingProvider)) {
            return createOllamaEmbeddingModel();
        } else {
            return createOpenAiEmbeddingModel();
        }
    }

    private EmbeddingModel createOpenAiEmbeddingModel() {
        log.info("Creating OpenAI embedding model: {} at {}", openaiModelName, openaiBaseUrl);
        return OpenAiEmbeddingModel.builder()
                .apiKey(openaiApiKey)
                .baseUrl(openaiBaseUrl)
                .modelName(openaiModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    private EmbeddingModel createOllamaEmbeddingModel() {
        log.info("Creating Ollama embedding model: {} at {}, timeout: {}s", ollamaEmbeddingModel, ollamaBaseUrl, ollamaTimeout);
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModel)
                .timeout(Duration.ofSeconds(ollamaTimeout))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
