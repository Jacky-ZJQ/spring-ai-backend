# Spring AI Demo

一个基于 Spring Boot + Spring AI 的多场景 AI 示例项目，包含：

- 基础文本聊天（Ollama 本地模型）
- 多模态聊天（文本 + 图片）
- 智能客服（Tool Calling + MySQL）
- PDF 文档问答（RAG）
- 会话历史查询与 PDF 下载

适合作为 Spring AI 学习、课程演示和原型开发项目。

## 功能概览

### 1. 通用聊天 `/ai/chat`
- 支持纯文本聊天（Ollama）
- 支持携带图片的多模态聊天（OpenAI 兼容接口）
- 支持流式输出与 `chatId` 会话记忆

### 2. 智能客服 `/ai/service`
- 预置客服角色提示词（课程咨询场景）
- 通过 `@Tool` 调用课程、校区、预约工具
- 预约结果落库（MyBatis-Plus + MySQL）

### 3. PDF 问答 `/ai/pdf/*`
- 上传 PDF 并向量化
- 基于 `SimpleVectorStore` 进行检索增强问答（RAG）
- 支持按会话下载源 PDF

### 4. 游戏对话 `/ai/game`
- 角色扮演式对话示例（“女友哄人”规则）
- 使用独立系统提示词与会话上下文

### 5. 历史会话 `/ai/history/*`
- 按业务类型查询 `chatId` 列表
- 查询指定会话的历史消息

## 技术栈

- Java 17
- Spring Boot 3.4.3
- Spring AI 1.0.0-M6
- MyBatis-Plus 3.5.10.1
- MySQL 8.x
- Ollama（本地模型）
- DashScope OpenAI Compatible API（Qwen / Embedding）

## 项目结构

```text
spring-ai/
├── src/main/java/com/jacky/ai/
│   ├── config/         # ChatClient、CORS、API日志切面
│   ├── constants/      # 系统提示词
│   ├── controller/     # 对外 REST API
│   ├── entity/         # PO/VO/Query
│   ├── mapper/         # MyBatis-Plus Mapper
│   ├── repository/     # 会话历史与 PDF 文件映射
│   ├── service/        # 业务服务
│   └── tools/          # AI 可调用工具（@Tool）
├── src/main/resources/
│   ├── application.yaml
│   └── static/index.html
├── storage/pdf/        # PDF 本地存储目录（运行时创建）
├── chat-pdf.properties # chatId -> 文件路径映射（运行期生成）
└── chat-pdf.json       # 向量库持久化文件（运行期生成）
```

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.9+
- MySQL（已创建数据库）
- 已安装并启动 Ollama

### 2. 启动本地模型（Ollama）

```bash
ollama pull deepseek-r1:1.5b
ollama serve
```

### 3. 配置环境变量

项目使用 `OPENAI_API_KEY`（用于 DashScope OpenAI 兼容接口）：

```bash
export OPENAI_API_KEY=你的密钥
```

### 4. 修改数据库配置

编辑 `src/main/resources/application.yaml`：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

并确保存在以下表：

- `course`
- `school`
- `course_reservation`

### 5. 启动项目

```bash
mvn spring-boot:run
```

默认端口：`http://localhost:8080`

## 接口说明

> 除文件上传外，大多数接口可通过 Query 参数传递 `prompt`、`chatId`。
> `chatId` 建议按业务前缀区分，如 `chat_...`、`pdf_...`、`service_...`、`game_...`。

### 1. 通用聊天

- 路径：`/ai/chat`
- 说明：纯文本或多模态聊天（流式）

文本聊天示例：

```bash
curl -N "http://localhost:8080/ai/chat?prompt=你好&chatId=chat_1700000000000"
```

多模态聊天示例：

```bash
curl -N -X POST "http://localhost:8080/ai/chat" \
  -F "prompt=请描述这张图片" \
  -F "chatId=chat_1700000000001" \
  -F "files=@/absolute/path/demo.png"
```

### 2. 智能客服

- 路径：`/ai/service`
- 说明：课程咨询与预约（Tool Calling）

```bash
curl "http://localhost:8080/ai/service?prompt=我想学编程&chatId=service_1700000000000"
```

### 3. 游戏对话

- 路径：`/ai/game`
- 说明：角色扮演流式对话

```bash
curl -N "http://localhost:8080/ai/game?prompt=她因为我忘记纪念日生气了&chatId=game_1700000000000"
```

### 4. PDF 上传

- 路径：`/ai/pdf/upload/{chatId}`
- 说明：上传并向量化 PDF

```bash
curl -X POST "http://localhost:8080/ai/pdf/upload/pdf_1700000000000" \
  -F "file=@/absolute/path/知识笔记.pdf"
```

### 5. PDF 问答

- 路径：`/ai/pdf/chat`
- 说明：基于已上传 PDF 进行流式问答

```bash
curl -N "http://localhost:8080/ai/pdf/chat?prompt=这份文档主要讲了什么&chatId=pdf_1700000000000"
```

### 6. PDF 下载

- 路径：`/ai/pdf/file/{chatId}`
- 说明：下载会话对应 PDF

```bash
curl -OJ "http://localhost:8080/ai/pdf/file/pdf_1700000000000"
```

### 7. 历史会话查询

查询会话 ID 列表：

```bash
curl "http://localhost:8080/ai/history/pdf"
```

查询某个会话消息：

```bash
curl "http://localhost:8080/ai/history/pdf/pdf_1700000000000"
```

## 关键实现说明

- `CommonConfiguration` 中定义了多个 `ChatClient`：
  - `ollamaChatClient`
  - `openAiChatClient`
  - `serviceOpenAiChatClient`
  - `gameOpenAiChatClient`
  - `pdfOpenAiChatClient`
- 会话记忆：`MessageChatMemoryAdvisor` + `InMemoryChatMemory`
- RAG：`QuestionAnswerAdvisor` + `SimpleVectorStore`
- API 访问日志：`ApiLogAspect` 统一打印请求、响应与耗时

## 生产化建议

- 将数据库账号密码迁移到环境变量或密钥系统
- 收紧 CORS 白名单（避免 `*`）
- 将会话历史从内存迁移到 Redis / DB
- 将向量库替换为可扩展的持久化方案（如 PGVector / Milvus / Elasticsearch）
- 增加全局异常处理与接口限流

## Docker 生产部署（2核4G 可用）

> 建议：2核4G 仅部署 `mysql + backend + portal`，不要在同机再跑 Ollama 推理。  
> 如需文本聊天，请把 `OLLAMA_BASE_URL` 指到外部 Ollama 服务。

1. 复制环境变量模板并修改密码/API Key

```bash
cp .env.prod.example .env.prod
```

2. 确认前端仓库路径（默认 `../spring-ai-protal`）

```bash
echo $PORTAL_BUILD_CONTEXT
```

3. 启动服务

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

4. 查看状态

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

默认访问：

- 前端：`http://服务器IP/`
- 后端：由前端通过 `/api/*` 反向代理访问
- MySQL：`3306`（可在 `.env.prod` 中修改）

## 常见问题

### 1. PDF 回答“没有相关上下文”

请检查：

- 是否先调用了 `/ai/pdf/upload/{chatId}`
- 问答时是否使用了同一个 `chatId`
- 上传文件是否为 `application/pdf`

### 2. `chatId` 为什么常见 `pdf_`、`chat_` 前缀

这是会话命名约定，用于区分业务类型，便于历史查询和排查日志，不影响接口功能。

## License

本项目采用 `Apache License 2.0`，详见 `LICENSE` 文件。
