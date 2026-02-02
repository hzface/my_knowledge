package com.demo.ai.exception;

import dev.langchain4j.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理异常并设置正确的字符编码
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 LangChain4j 限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException e) {
        log.error("API 限流异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "API 调用频率过高，请稍后再试");
        response.put("message", decodeMessage(e.getMessage()));

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(response);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "系统异常，请稍后再试");
        response.put("message", decodeMessage(e.getMessage()));

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "服务异常");
        response.put("message", decodeMessage(e.getMessage()));

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * 尝试修复可能被错误编码的消息
     */
    private String decodeMessage(String message) {
        if (message == null) {
            return null;
        }

        // 尝试修复 ISO-8859-1 编码的中文
        try {
            if (message.contains("æ") || message.contains("ç") || message.contains("è") || message.contains("é")) {
                byte[] bytes = message.getBytes(StandardCharsets.ISO_8859_1);
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                if (decoded.matches(".*[\\u4e00-\\u9fa5]+.*")) {
                    return decoded;
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to re-decode message", ex);
        }

        return message;
    }
}
