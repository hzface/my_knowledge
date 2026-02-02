package com.demo.ai.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文档服务
 * 负责文档解析和向量化存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    @Value("${app.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * 上传并处理文档
     */
    public String uploadAndProcess(MultipartFile file) throws IOException {
        // 保存文件
        String fileName = saveFile(file);
        Path filePath = Paths.get(uploadPath, fileName);
        log.info("文件已保存: {}, 原始文件名: {}", fileName, file.getOriginalFilename());

        // 解析文档
        Document document = parseDocument(filePath, file.getOriginalFilename());
        String text = document.text();
        log.info("文档解析完成，提取文本长度: {} 字符", text == null ? 0 : text.length());
        if (text == null || text.trim().isEmpty()) {
            log.error("文档解析后文本为空！可能是扫描件/图片 PDF，无法提取文字");
            throw new IOException("文档解析后文本为空，可能是扫描件或图片格式的 PDF");
        }
        log.info("文档前200字: {}", text.substring(0, Math.min(200, text.length())));

        // 向量化并存储
        ingestDocument(document);

        log.info("Document processed and stored: {}", fileName);
        return fileName;
    }

    /**
     * 保存上传的文件
     */
    private String saveFile(MultipartFile file) throws IOException {
        Path uploadDir = Paths.get(uploadPath);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ?
                originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = UUID.randomUUID() + extension;

        Path filePath = uploadDir.resolve(fileName);
        file.transferTo(filePath);

        return fileName;
    }

    /**
     * 解析文档
     */
    private Document parseDocument(Path filePath, String originalFilename) throws IOException {
        DocumentParser parser = getParser(originalFilename);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return parser.parse(inputStream);
        }
    }

    /**
     * 根据文件类型获取解析器
     */
    private DocumentParser getParser(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) {
            return new ApachePdfBoxDocumentParser();
        } else if (lowerFilename.endsWith(".docx") || lowerFilename.endsWith(".doc") ||
                lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls") ||
                lowerFilename.endsWith(".pptx") || lowerFilename.endsWith(".ppt")) {
            return new ApachePoiDocumentParser();
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }
    }

    /**
     * 文档向量化并存储
     */
    private void ingestDocument(Document document) {
        log.info("开始向量化，chunk-size: {}, chunk-overlap: {}", chunkSize, chunkOverlap);

        // 先手动分块看看有多少段
        var splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        var segments = splitter.split(document);
        log.info("文档分块完成，共 {} 个分块", segments.size());
        for (int i = 0; i < Math.min(3, segments.size()); i++) {
            log.info("分块 {} 内容前100字: {}", i + 1,
                segments.get(i).text().substring(0, Math.min(100, segments.get(i).text().length())));
        }

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(chunkSize, chunkOverlap))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);
        log.info("向量化存储完成，已写入 Milvus");
    }

    /**
     * 测试向量检索
     * 用于验证向量化是否成功
     */
    public java.util.List<java.util.Map<String, Object>> testSearch(String query, int maxResults, double minScore) {
        try {
            log.info("测试向量检索，查询: {}, maxResults: {}, minScore: {}", query, maxResults, minScore);

            dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(query).content();

            dev.langchain4j.store.embedding.EmbeddingSearchRequest searchRequest =
                dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

            java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
            for (dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment> match : result.matches()) {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("score", match.score());
                item.put("text", match.embedded().text());
                results.add(item);
            }

            log.info("向量检索结果数量: {}", results.size());
            return results;
        } catch (Exception e) {
            log.error("测试向量检索失败", e);
            throw new RuntimeException("检索失败: " + e.getMessage());
        }
    }
}
