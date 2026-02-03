package com.demo.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Milvus 向量数据库配置
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${app.milvus.host:localhost}")
    private String host;

    @Value("${app.milvus.port:19530}")
    private int port;

    @Value("${app.milvus.collection-name:knowledge_base}")
    private String collectionName;

    @Value("${app.milvus.dimension:1024}")
    private int dimension;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Configuring Milvus embedding store: {}:{}, collection: {}, dimension: {}",
                host, port, collectionName, dimension);

        String uri = String.format("http://%s:%d", host, port);

        return MilvusEmbeddingStore.builder()
                .uri(uri)
                .collectionName(collectionName)
                .dimension(dimension) // 指定存储在 Milvus 中的向量数据的维度大小
                .build();
    }
}
