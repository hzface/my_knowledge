package com.demo.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统工具
 * 提供文件操作能力：列出、读取、写入、删除、搜索、获取信息
 */
@Slf4j
@Component
public class FileSystemTool {

    @Value("${app.filesystem.base-path:./workspace}")
    private String basePath;

    /**
     * 获取安全的文件路径（防止目录遍历攻击）
     */
    private Path getSafePath(String relativePath) throws IOException {
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();

        if (!target.startsWith(base)) {
            throw new SecurityException("访问路径超出允许范围");
        }

        return target;
    }

    @Tool("列出指定目录的内容，返回文件和子目录列表")
    public String listDirectory(String path) {
        try {
            Path dir = getSafePath(path);

            if (!Files.exists(dir)) {
                return "目录不存在: " + path;
            }

            if (!Files.isDirectory(dir)) {
                return "指定路径不是目录: " + path;
            }

            try (Stream<Path> stream = Files.list(dir)) {
                List<String> items = stream.map(p -> {
                    String name = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        return "[目录] " + name;
                    } else {
                        try {
                            long size = Files.size(p);
                            return "[文件] " + name + " (" + formatSize(size) + ")";
                        } catch (IOException e) {
                            return "[文件] " + name;
                        }
                    }
                }).collect(Collectors.toList());

                if (items.isEmpty()) {
                    return "目录为空";
                }

                return String.join("\n", items);
            }

        } catch (Exception e) {
            log.error("Failed to list directory: {}", path, e);
            return "列出目录失败: " + e.getMessage();
        }
    }

    @Tool("读取指定文件的内容")
    public String readFile(String path) {
        try {
            Path file = getSafePath(path);

            if (!Files.exists(file)) {
                return "文件不存在: " + path;
            }

            if (!Files.isRegularFile(file)) {
                return "指定路径不是文件: " + path;
            }

            // 限制文件大小
            long size = Files.size(file);
            if (size > 1024 * 1024) { // 1MB
                return "文件过大，无法读取 (>" + formatSize(size) + ")";
            }

            String content = Files.readString(file);

            // 限制返回内容长度
            if (content.length() > 50000) {
                content = content.substring(0, 50000) + "\n...(内容已截断)";
            }

            return content;

        } catch (Exception e) {
            log.error("Failed to read file: {}", path, e);
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool("写入内容到指定文件（会覆盖已有内容）")
    public String writeFile(String path, String content) {
        try {
            Path file = getSafePath(path);

            // 确保父目录存在
            Files.createDirectories(file.getParent());

            Files.writeString(file, content);
            log.info("File written: {}", path);

            return "文件写入成功: " + path;

        } catch (Exception e) {
            log.error("Failed to write file: {}", path, e);
            return "写入文件失败: " + e.getMessage();
        }
    }

    @Tool("删除指定的文件或目录")
    public String delete(String path) {
        try {
            Path target = getSafePath(path);

            if (!Files.exists(target)) {
                return "路径不存在: " + path;
            }

            if (Files.isDirectory(target)) {
                // 递归删除目录 - 使用 Java 17 菱形操作符
                Files.walkFileTree(target, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(target);
            }

            log.info("Deleted: {}", path);
            return "删除成功: " + path;

        } catch (Exception e) {
            log.error("Failed to delete: {}", path, e);
            return "删除失败: " + e.getMessage();
        }
    }

    @Tool("在指定目录下搜索匹配模式的文件")
    public String searchFiles(String directory, String pattern) {
        try {
            Path dir = getSafePath(directory);

            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return "目录不存在或无效: " + directory;
            }

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> results = new ArrayList<>();

            // 使用 Java 17 菱形操作符
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matcher.matches(file.getFileName())) {
                        results.add(dir.relativize(file).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if (results.isEmpty()) {
                return "未找到匹配的文件";
            }

            return "找到 " + results.size() + " 个文件:\n" + String.join("\n", results);

        } catch (Exception e) {
            log.error("Failed to search files in: {}", directory, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    @Tool("获取文件或目录的详细信息")
    public String getInfo(String path) {
        try {
            Path target = getSafePath(path);

            if (!Files.exists(target)) {
                return "路径不存在: " + path;
            }

            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);

            // 使用 Java 17 文本块
            return """
                    路径: %s
                    类型: %s
                    大小: %s
                    创建时间: %s
                    修改时间: %s
                    访问时间: %s
                    可读: %s
                    可写: %s
                    """.formatted(
                    path,
                    Files.isDirectory(target) ? "目录" : "文件",
                    formatSize(attrs.size()),
                    attrs.creationTime(),
                    attrs.lastModifiedTime(),
                    attrs.lastAccessTime(),
                    Files.isReadable(target),
                    Files.isWritable(target)
            );

        } catch (Exception e) {
            log.error("Failed to get info: {}", path, e);
            return "获取信息失败: " + e.getMessage();
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
