package com.demo.ai.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 配置类
 * 每个 MCP 服务可独立配置使用 npx (Node.js) 或 uvx (Python)
 * 所有 MCP 客户端合并为一个 ToolProvider 注册到 AiAssistant
 *
 * 可用的 MCP Server:
 * - uvx (Python): mcp-server-fetch, mcp-server-git
 * - npx (Node.js): @modelcontextprotocol/server-github, server-filesystem
 */
@Slf4j
@Configuration
public class McpConfig {

    // 各服务独立 runner 配置
    @Value("${app.mcp.github.enabled:false}")
    private boolean githubEnabled;

    @Value("${app.mcp.github.runner:npx}")
    private String githubRunner;

    @Value("${app.mcp.github.token:}")
    private String githubToken;

    @Value("${app.mcp.fetch.enabled:false}")
    private boolean fetchEnabled;

    @Value("${app.mcp.fetch.runner:uvx}")
    private String fetchRunner;

    @Value("${app.mcp.git.enabled:false}")
    private boolean gitEnabled;

    @Value("${app.mcp.git.runner:uvx}")
    private String gitRunner;

    @Value("${app.mcp.filesystem.enabled:false}")
    private boolean filesystemEnabled;

    @Value("${app.mcp.filesystem.runner:npx}")
    private String filesystemRunner;

    @Value("${app.mcp.filesystem.allowed-dir:./workspace}")
    private String filesystemAllowedDir;

    @Value("${app.mcp.timeout:120}")
    private int mcpTimeoutSeconds;

    private final List<McpClient> mcpClients = new ArrayList<>();

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * 构建跨平台命令
     */
    private List<String> buildCommand(String runner, String packageName, String... extraArgs) {
        List<String> command = new ArrayList<>();

        if (IS_WINDOWS) {
            command.add("cmd");
            command.add("/c");
        }

        if ("uvx".equalsIgnoreCase(runner)) {
            command.add("uvx");
        } else {
            command.add("npx");
            command.add("-y");
        }

        command.add(packageName);
        for (String arg : extraArgs) {
            command.add(arg);
        }

        log.info("MCP command (runner={}): {}", runner, command);
        return command;
    }

    private boolean isUvx(String runner) {
        return "uvx".equalsIgnoreCase(runner);
    }

    /**
     * 创建 MCP 客户端
     */
    private McpClient createClient(StdioMcpTransport transport) {
        McpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .initializationTimeout(Duration.ofSeconds(mcpTimeoutSeconds))
                .toolExecutionTimeout(Duration.ofSeconds(mcpTimeoutSeconds))
                .build();
        mcpClients.add(client);
        return client;
    }

    /**
     * 统一的 MCP ToolProvider
     * 将所有启用的 MCP 客户端合并为一个 ToolProvider
     */
    @Bean("mcpToolProvider")
    public ToolProvider mcpToolProvider() {
        List<McpClient> enabledClients = new ArrayList<>();

        // GitHub MCP (仅 npx)
        if (githubEnabled) {
            if (isUvx(githubRunner)) {
                log.warn("GitHub MCP Server is only available with npx. Configured runner '{}' is not supported. Skipping...", githubRunner);
            } else {
                try {
                    log.info("Initializing GitHub MCP Server (runner={})...", githubRunner);
                    Map<String, String> env = new java.util.HashMap<>(System.getenv());
                    if (githubToken != null && !githubToken.isEmpty()) {
                        env.put("GITHUB_PERSONAL_ACCESS_TOKEN", githubToken);
                    }
                    StdioMcpTransport transport = new StdioMcpTransport.Builder()
                            .command(buildCommand(githubRunner, "@modelcontextprotocol/server-github"))
                            .environment(env)
                            .build();
                    enabledClients.add(createClient(transport));
                    log.info("GitHub MCP Server initialized successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize GitHub MCP Server", e);
                }
            }
        }

        // Fetch MCP (仅 uvx)
        if (fetchEnabled) {
            if (!isUvx(fetchRunner)) {
                log.warn("Fetch MCP Server is only available with uvx. Configured runner '{}' is not supported. Skipping...", fetchRunner);
            } else {
                try {
                    log.info("Initializing Fetch MCP Server (runner={})...", fetchRunner);
                    StdioMcpTransport transport = new StdioMcpTransport.Builder()
                            .command(buildCommand(fetchRunner, "mcp-server-fetch"))
                            .build();
                    enabledClients.add(createClient(transport));
                    log.info("Fetch MCP Server initialized successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize Fetch MCP Server", e);
                }
            }
        }

        // Git MCP (仅 uvx)
        if (gitEnabled) {
            if (!isUvx(gitRunner)) {
                log.warn("Git MCP Server is only available with uvx. Configured runner '{}' is not supported. Skipping...", gitRunner);
            } else {
                try {
                    log.info("Initializing Git MCP Server (runner={})...", gitRunner);
                    StdioMcpTransport transport = new StdioMcpTransport.Builder()
                            .command(buildCommand(gitRunner, "mcp-server-git"))
                            .build();
                    enabledClients.add(createClient(transport));
                    log.info("Git MCP Server initialized successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize Git MCP Server", e);
                }
            }
        }

        // Filesystem MCP (仅 npx)
        if (filesystemEnabled) {
            if (isUvx(filesystemRunner)) {
                log.warn("Filesystem MCP Server is only available with npx. Configured runner '{}' is not supported. Skipping...", filesystemRunner);
            } else {
                try {
                    log.info("Initializing Filesystem MCP Server (runner={}, allowed-dir={})...", filesystemRunner, filesystemAllowedDir);
                    StdioMcpTransport transport = new StdioMcpTransport.Builder()
                            .command(buildCommand(filesystemRunner, "@modelcontextprotocol/server-filesystem", filesystemAllowedDir))
                            .build();
                    enabledClients.add(createClient(transport));
                    log.info("Filesystem MCP Server initialized successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize Filesystem MCP Server", e);
                }
            }
        }

        if (enabledClients.isEmpty()) {
            log.warn("No MCP Server enabled. Only local @Tool classes are available.");
            return null;
        }

        log.info("Total {} MCP client(s) initialized, creating unified McpToolProvider", enabledClients.size());
        return McpToolProvider.builder()
                .mcpClients(enabledClients)
                .build();
    }

    @PreDestroy
    public void cleanup() {
        for (McpClient client : mcpClients) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client", e);
            }
        }
        mcpClients.clear();
        log.info("All MCP clients closed");
    }
}
