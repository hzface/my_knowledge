package com.demo.ai.mapper;

import com.demo.ai.model.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入消息
     */
    int insert(ChatMessage message);

    /**
     * 批量插入消息
     */
    int batchInsert(@Param("messages") List<ChatMessage> messages);

    /**
     * 根据会话ID查询消息（按时间升序）
     */
    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据ID查询消息
     */
    ChatMessage selectById(@Param("id") String id);

    /**
     * 删除会话的所有消息
     */
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
