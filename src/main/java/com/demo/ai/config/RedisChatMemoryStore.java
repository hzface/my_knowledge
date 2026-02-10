package com.demo.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的聊天记忆存储
 * 实现 LangChain4j 的 ChatMemoryStore 接口
 */
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> rawMessages = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return deserializeMessages(rawMessages);
        } catch (Exception e) {
            log.error("Failed to get messages from Redis for memoryId: {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId;
        try {
            List<Map<String, Object>> serialized = serializeMessages(messages);
            String json = objectMapper.writeValueAsString(serialized);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Updated {} messages in Redis for memoryId: {}", messages.size(), memoryId);
        } catch (JsonProcessingException e) {
            log.error("Failed to update messages in Redis for memoryId: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        redisTemplate.delete(key);
        log.debug("Deleted messages from Redis for memoryId: {}", memoryId);
    }

    /**
     * 序列化消息列表
     */
    private List<Map<String, Object>> serializeMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> map = new java.util.HashMap<>();
            // 通过 instanceof 判断类型，避免 IDE 类型推断问题
            String type;
            if (message instanceof UserMessage) {
                type = "USER";
                map.put("content", ((UserMessage) message).singleText());
            } else if (message instanceof AiMessage) {
                type = "AI";
                map.put("content", ((AiMessage) message).text());
            } else if (message instanceof SystemMessage) {
                type = "SYSTEM";
                map.put("content", ((SystemMessage) message).text());
            } else {
                type = "UNKNOWN";
                map.put("content", "");
            }
            map.put("type", type);
            result.add(map);
        }
        return result;
    }

    /**
     * 反序列化消息列表
     */
    private List<ChatMessage> deserializeMessages(List<Map<String, Object>> rawMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        for (Map<String, Object> raw : rawMessages) {
            String type = (String) raw.get("type");
            String content = (String) raw.get("content");
            if (content == null) continue;

            ChatMessage message = switch (type) {
                case "USER" -> UserMessage.from(content);
                case "AI" -> AiMessage.from(content);
                case "SYSTEM" -> SystemMessage.from(content);
                default -> null;
            };
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }
}
