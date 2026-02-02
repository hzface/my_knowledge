package com.demo.ai.mapper;

import com.demo.ai.model.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 插入会话
     */
    int insert(ChatSession session);

    /**
     * 更新会话
     */
    int update(ChatSession session);

    /**
     * 根据ID查询会话
     */
    ChatSession selectById(@Param("id") String id);

    /**
     * 查询所有会话（按更新时间倒序）
     */
    List<ChatSession> selectAll();

    /**
     * 删除会话
     */
    int deleteById(@Param("id") String id);
}
