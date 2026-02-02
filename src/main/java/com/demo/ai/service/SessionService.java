package com.demo.ai.service;

import com.demo.ai.mapper.ChatMessageMapper;
import com.demo.ai.mapper.ChatSessionMapper;
import com.demo.ai.model.ChatMessage;
import com.demo.ai.model.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话服务
 * 使用 Redis 缓存 + MySQL 持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    private static final String SESSION_PREFIX = "chat:session:";
    private static final int MAX_TITLE_LENGTH = 30;

    @Value("${app.session.timeout:3600}")
    private long sessionTimeout;

    @Value("${app.session.max-history:100}")
    private int maxHistory;

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession() {
        String sessionId = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));

        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .title("对话 " + timestamp)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 保存到 MySQL
        chatSessionMapper.insert(session);
        // 缓存到 Redis
        saveToRedis(session);

        log.info("Created new session: {}", sessionId);
        return session;
    }

    /**
     * 获取会话（优先从 Redis 获取，不存在则从 MySQL 加载）
     */
    public ChatSession getSession(String sessionId) {
        // 先从 Redis 获取
        ChatSession session = getFromRedis(sessionId);
        if (session != null) {
            return session;
        }

        // Redis 没有，从 MySQL 加载
        session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            // 加载消息列表
            List<ChatMessage> messages = chatMessageMapper.selectBySessionId(sessionId);
            session.setMessages(messages);
            // 缓存到 Redis
            saveToRedis(session);
        }

        return session;
    }

    /**
     * 保存会话到 Redis
     */
    private void saveToRedis(ChatSession session) {
        String key = SESSION_PREFIX + session.getId();
        redisTemplate.opsForValue().set(key, session, sessionTimeout, TimeUnit.SECONDS);
    }

    /**
     * 从 Redis 获取会话
     */
    private ChatSession getFromRedis(String sessionId) {
        String key = SESSION_PREFIX + sessionId;
        Object session = redisTemplate.opsForValue().get(key);
        if (session instanceof ChatSession chatSession) {
            return chatSession;
        }
        return null;
    }

    /**
     * 添加消息到会话
     */
    @Transactional
    public void addMessage(String sessionId, ChatMessage message) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            // 确保消息有 ID
            if (message.getId() == null) {
                message.setId(UUID.randomUUID().toString());
            }
            message.setSessionId(sessionId);
            if (message.getTimestamp() == null) {
                message.setTimestamp(LocalDateTime.now());
            }

            // 保存消息到 MySQL
            chatMessageMapper.insert(message);

            // 如果是第一条用户消息，根据内容自动生成标题
            if ("user".equals(message.getRole()) && isDefaultTitle(session.getTitle())) {
                String newTitle = generateTitle(message.getContent());
                session.setTitle(newTitle);
                log.info("Auto-generated session title: {}", newTitle);
            }

            // 更新内存中的会话
            session.addMessage(message);

            // 限制历史消息数量
            if (session.getMessages().size() > maxHistory) {
                List<ChatMessage> messages = session.getMessages();
                session.setMessages(messages.subList(messages.size() - maxHistory, messages.size()));
            }

            // 更新 MySQL 会话时间和标题
            chatSessionMapper.update(session);
            // 更新 Redis 缓存
            saveToRedis(session);
        }
    }

    /**
     * 检查是否是默认标题（未自定义）
     */
    private boolean isDefaultTitle(String title) {
        if (title == null) return true;
        // 匹配 "新对话" 或 "对话 MM-dd HH:mm" 格式
        return title.equals("新对话") || title.matches("对话 \\d{2}-\\d{2} \\d{2}:\\d{2}");
    }

    /**
     * 根据消息内容生成标题
     */
    private String generateTitle(String content) {
        if (content == null || content.isBlank()) {
            return "新对话";
        }

        // 去除多余空白字符
        String title = content.trim().replaceAll("\\s+", " ");

        // 截取合适长度
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH) + "...";
        }

        return title;
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(String sessionId) {
        // 删除 Redis 缓存
        String key = SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);

        // 删除 MySQL 数据（先删消息再删会话，避免外键约束问题）
        chatMessageMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteById(sessionId);

        log.info("Deleted session: {}", sessionId);
    }

    /**
     * 获取会话历史消息
     */
    public List<ChatMessage> getHistory(String sessionId) {
        ChatSession session = getSession(sessionId);
        return session != null ? session.getMessages() : Collections.emptyList();
    }

    /**
     * 获取所有会话列表
     */
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.selectAll();
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateTitle(String sessionId, String title) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.update(session);
            saveToRedis(session);
        }
    }
}
