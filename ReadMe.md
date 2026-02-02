# 项目技术栈概览

本项目是一个基于 Spring Boot 构建的智能问答/知识库系统，深度融合了 LangChain4j 生态，支持本地与云端大模型，并具备会话保持、流式传输、文档解析、向量存储和 RAG（检索增强生成）、MCP、SKILLS能力。

## 🧠 核心框架

- **Spring Boot 3.5.0**（要求 Java 17+）
  - Web 应用：`spring-boot-starter-web`
  - 响应式支持：`spring-boot-starter-webflux`
  - 模板引擎：Vue3
  - 测试支持：`spring-boot-starter-test`

## 🤖 AI / LLM 集成（LangChain4j）

所有 LangChain4j 相关依赖版本均为 `1.0.1-beta6`：

- `langchain4j-open-ai-spring-boot-starter`：集成 OpenAI API、向量大模型
- `langchain4j-spring-boot-starter`：启用 AI Service 编程模型
- `langchain4j-reactor`：支持响应式流式输出（如 SSE）
- `langchain4j-easy-rag`：简化 RAG 流程
- `langchain4j-ollama`：支持本地运行的大模型（通过 Ollama）
- `langchain4j-mcp`：支持 Model Context Protocol（MCP）

## 📄 文档解析能力

- **PDF 解析**：通过 `langchain4j-document-parser-apache-pdfbox`（底层使用 Apache PDFBox）
- **Office 文档（.docx, .xlsx 等）解析**：通过 `langchain4j-document-parser-apache-poi`（底层使用 Apache POI）

## 🗃️ 数据存储

- **关系型数据库**
  - MySQL 驱动：`mysql-connector-j`
  - ORM 框架：MyBatis（`mybatis-spring-boot-starter` v3.0.3）
  
- **缓存 & 向量数据库**
  - Redis：`spring-boot-starter-data-redis`（用于打开窗口的会话保存）
  - 向量存储与检索：`langchain4j-community-redis-spring-boot-starter`（基于 Redis 的向量搜索）

## 🔧 工具类

- **Lombok**：用于简化 Java Bean 的 getter/setter/constructor 等样板代码
- **多种AI工具集成**：
  - GitHub工具：查询GitHub仓库信息、提交历史等
  - 网页获取工具：获取网页内容并基于内容回答问题
  - 文件系统工具：可选的操作有：
    1. list - 列出目录内容
    2. read - 读取文件内容
    3. write - 写入文件内容
    4. delete - 删除文件或目录
    5. search - 搜索文件
    6. info - 获取文件系统信息

## ✅ 系统能力总结

- 支持 **OpenAI 和 Ollama（本地大模型）** 混合模式，结合多种工具的优势，配置化可选择需要的模型
- 实现完整的 **RAG（Retrieval-Augmented Generation）** 流程
- 能自动解析 **PDF、Word、Excel** 等常见办公文档
- 使用 **Redis 作为向量数据库**，实现高效语义检索
- 提供传统 **Web 页面（Vue3）** 与 **响应式 API（WebFlux）** 双模式交互，支持文件上传、多窗口会话保持

> 该架构非常适合构建企业内部的 AI 知识库、智能客服或文档问答系统。