package com.demo.ai.controller;

import com.demo.ai.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档控制器
 * 提供文档上传和管理接口
 */
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档
     * 支持 PDF、Word、Excel 等格式
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "请选择要上传的文件");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String fileName = documentService.uploadAndProcess(file);
            response.put("success", true);
            response.put("message", "文档上传成功并已处理");
            response.put("fileName", fileName);
            response.put("originalName", file.getOriginalFilename());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", "不支持的文件格式: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "文件处理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<Map<String, Object>> uploadDocuments(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> response = new HashMap<>();

        if (files == null || files.length == 0) {
            response.put("success", false);
            response.put("message", "请选择要上传的文件");
            return ResponseEntity.badRequest().body(response);
        }

        int successCount = 0;
        int failCount = 0;

        for (MultipartFile file : files) {
            try {
                if (!file.isEmpty()) {
                    documentService.uploadAndProcess(file);
                    successCount++;
                }
            } catch (Exception e) {
                failCount++;
            }
        }

        response.put("success", true);
        response.put("message", String.format("上传完成: 成功 %d 个, 失败 %d 个", successCount, failCount));
        response.put("successCount", successCount);
        response.put("failCount", failCount);

        return ResponseEntity.ok(response);
    }

    /**
     * 测试向量检索
     * 用于验证文档是否正确向量化
     * 示例: GET /api/document/search?query=结账单&maxResults=5&minScore=0
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> testSearch(
            @RequestParam("query") String query,
            @RequestParam(value = "maxResults", defaultValue = "5") int maxResults,
            @RequestParam(value = "minScore", defaultValue = "0") double minScore) {

        Map<String, Object> response = new HashMap<>();

        try {
            var results = documentService.testSearch(query, maxResults, minScore);
            response.put("success", true);
            response.put("query", query);
            response.put("count", results.size());
            response.put("results", results);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "检索失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            // 打印完整堆栈到日志
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
