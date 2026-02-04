# AI 知识库系统

基于 Spring Boot 3 和 LangChain4j 构建的智能知识库问答系统，支持文档上传、向量化存储、RAG 检索增强生成，以及多种大语言模型的灵活切换。

## 功能特性

- **智能对话**：支持流式/非流式聊天，实时 SSE 输出
- **RAG 检索**：基于向量相似度的文档检索增强生成
- **多模型支持**：OpenAI API 兼容接口 + Ollama 本地模型
- **文档处理**：PDF、Word、Excel、PowerPoint 全格式支持
- **会话管理**：Redis + MySQL 双层存储，支持历史对话
- **MCP 工具集成**：文件系统操作、网页抓取、Git 操作等扩展能力

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.0、Java 17 |
| AI 框架 | LangChain4j 1.0.1-beta6 |
| 向量数据库 | Milvus |
| 关系数据库 | MySQL 8.0+ |
| 缓存 | Redis |
| 前端 | Vue 3 |
| 文档解析 | Apache PDFBox、Apache Tika、Apache POI |

## 项目结构

```
src/main/java/com/demo/ai/
├── config/                 # 配置类
│   ├── ChatModelConfig     # 聊天模型配置（OpenAI/Ollama）
│   ├── EmbeddingConfig     # 嵌入模型配置
│   ├── MilvusConfig        # 向量数据库配置
│   ├── McpConfig           # MCP 工具配置
│   └── RedisConfig         # Redis 配置
├── controller/             # API 接口层
│   ├── ChatController      # 聊天相关接口
│   └── DocumentController  # 文档上传接口
├── service/                # 业务逻辑层
│   ├── AiAssistant         # AI 助手接口（LangChain4j AiService）
│   ├── ChatService         # 聊天服务
│   ├── DocumentService     # 文档处理服务
│   └── SessionService      # 会话管理服务
├── model/                  # 数据模型
├── mapper/                 # MyBatis 数据访问层
├── tools/                  # AI 工具类
└── exception/              # 异常处理
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Milvus 2.0+
- Ollama（可选，用于本地模型）

### 配置说明

编辑 `application.yml` 配置文件：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_database
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379

# 大模型配置
langchain4j:
  openai:
    chat-model:
      api-key: ${YOUR_API_KEY}
      base-url: https://your-api-endpoint/v1
      model-name: your-model
    embedding-model:
      api-key: ${YOUR_EMBEDDING_API_KEY}
      base-url: https://your-embedding-endpoint/v1
      model-name: text-embedding-v3

# Ollama 本地模型（可选）
ollama:
  base-url: http://localhost:11434
  chat-model:
    model-name: qwen3:4b
  embedding-model:  # Ollama 嵌入模型配置
    model-name: bge-m3
    timeout: 3000  # 超时时间（秒），大文档需要更长时间

# 应用配置
app:
  llm:
    provider: ollama          # 可选：openai 或 ollama
  embedding:
    provider: ollama          # 可选：openai 或 ollama
  milvus:
    host: localhost
    port: 19530
    collection-name: knowledge_base
    dimension: 1024
```

### 启动服务

```bash
# Windows
start.bat

# Linux/Mac
./start.sh

# 或使用 Maven
mvn spring-boot:run
```

访问 http://localhost:8089 打开 Web 界面。

## API 接口

### 聊天接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/session` | 创建新会话 |
| GET | `/api/chat/sessions` | 获取所有会话 |
| GET | `/api/chat/session/{id}` | 获取会话详情 |
| DELETE | `/api/chat/session/{id}` | 删除会话 |
| PUT | `/api/chat/session/{id}/title` | 更新会话标题 |
| POST | `/api/chat/message` | 发送消息（非流式） |
| POST | `/api/chat/stream` | 发送消息（流式 SSE） |
| GET | `/api/chat/history/{id}` | 获取聊天历史 |

### 文档接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/document/upload` | 上传单个文档 |
| POST | `/api/document/upload/batch` | 批量上传文档 |
| GET | `/api/document/search` | 向量检索测试 |

## 核心功能实现

### RAG 检索流程

```
用户提问 → 文本向量化 → Milvus 相似度检索 → 获取相关文档片段 → 组装上下文 → LLM 生成回答
```

**关键参数**：
- 向量维度：1024
- 相似度阈值：0.5
- 返回结果数：5

### 文档处理流程

```
文件上传 → 格式识别 → 文本提取 → 递归分块 → 多线程向量化 → 存储到 Milvus
```

**支持格式**：
- PDF（Apache PDFBox）
- Word .doc/.docx（Apache Tika）
- Excel .xls/.xlsx（Apache POI）
- PowerPoint .ppt/.pptx（Apache POI）

**分块参数**：
- 分块大小：500 字符
- 重叠大小：50 字符
- 并行线程：4
- 批处理大小：10

### 会话管理

采用 Redis + MySQL 双层存储架构：
- Redis：热数据缓存，快速读写
- MySQL：持久化存储，数据可靠

**配置参数**：
- 会话超时：3600 秒
- 最大历史消息：100 条

## MCP 工具扩展

系统集成了 Model Context Protocol，支持以下工具：

| 工具 | 功能 |
|------|------|
| GitHub | 查询 GitHub 仓库、Issue、PR 等信息 |
| Fetch | 网页内容抓取和解析 |
| Git | Git 仓库操作（clone、diff、log） |
| Filesystem | 文件系统读写操作 |


### GitHub 工具 (7个)

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `github_init` | 初始化 GitHub 客户端 | `token`(可选) |
| `github_get_repo` | 获取仓库信息 | `owner`, `repo` |
| `github_list_repos` | 列出用户仓库 | `username`, `type`(可选) |
| `github_get_file` | 获取文件内容 | `owner`, `repo`, `path`, `ref`(可选) |
| `github_search_repos` | 搜索仓库 | `query`, `sort`(可选) |
| `github_list_issues` | 列出 Issues | `owner`, `repo`, `state`(可选) |
| `github_get_user` | 获取用户信息 | `username` |

### 网页解析工具 (8个)

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `web_fetch` | 获取网页原始 HTML | `url` |
| `web_extract_text` | 提取纯文本内容 | `url` |
| `web_extract_links` | 提取所有链接 | `url` |
| `web_extract_metadata` | 提取元数据 (OG, Twitter) | `url` |
| `web_extract_structured_data` | 提取 JSON-LD 数据 | `url` |
| `web_parse_html` | CSS 选择器解析 | `html`, `selector` |
| `web_extract_images` | 提取所有图片 | `url` |
| `web_extract_headings` | 提取所有标题 | `url` |

在 `application.yml` 中启用：

```yaml
app:
  mcp:
    timeout: 120
    github:
      enabled: true
      runner: npx
    fetch:
      enabled: true
      runner: uvx
```

## 数据库初始化

执行 `src/main/resources/schema.sql` 创建必要的表：

```sql
-- 会话表
CREATE TABLE chat_session (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
) CHARSET=utf8mb4;

-- 消息表
CREATE TABLE chat_message (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  timestamp DATETIME NOT NULL,
  INDEX idx_session_id (session_id),
  FOREIGN KEY (session_id) REFERENCES chat_session(id)
) CHARSET=utf8mb4;
```

## 日志配置

日志输出到 `logs/` 目录：
- `app.log`：应用日志（每日轮转，保留 30 天）
- `error.log`：错误日志

调试模式下可开启详细日志：
```yaml
app:
  llm:
    log-requests: true
    log-responses: true
```

## 性能优化

- **多线程向量化**：使用线程池并行处理文档分块
- **批量入库**：向量数据批量写入 Milvus
- **Redis 缓存**：热点会话数据缓存
- **流式输出**：SSE 实时返回，提升用户体验

## 依赖版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.0 |
| LangChain4j | 1.0.1-beta6 |
| LangChain4j MCP | 1.1.0-beta7 |
| MyBatis | 3.0.3 |
| OkHttp | 4.12.0 |


