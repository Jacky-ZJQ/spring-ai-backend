<div align="center">

# Spring AI Demo

[![JDK](https://img.shields.io/badge/JDK-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue.svg)](https://spring.io/projects/spring-ai)
[![Vue](https://img.shields.io/badge/Vue-3.4.x-42b883.svg)](https://vuejs.org/)

![](https://img.shields.io/github/v/release/microsoft/vscode)
![](https://img.shields.io/github/license/microsoft/vscode)
![](https://img.shields.io/github/last-commit/microsoft/vscode)
![](https://img.shields.io/github/stars/microsoft/vscode)
</div>


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

## 故障应急 SOP（生产可直接执行）

> 适用于已部署在单机 Docker Compose 的线上环境。
> 默认目录：`/opt/spring-ai`。

### 1. 先做健康检查

```bash
cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
curl http://127.0.0.1/api/actuator/health
```

### 2. 标准发布（pull 模式）

```bash
/opt/spring-ai/deploy.sh pull all
/opt/spring-ai/deploy.sh pull backend
/opt/spring-ai/deploy.sh pull portal
```

说明：

- 不带参数时默认等价于 `/opt/spring-ai/deploy.sh nopull all`
- `pull backend` 只更新后端仓库并发布 `backend`
- `pull portal` 只更新前端仓库并发布 `portal`
- `pull all` 才会同时更新两个仓库

### 3. 发布后异常，快速回滚

```bash
cd /opt/spring-ai/spring-ai-backend
git log --oneline -n 5
cd /opt/spring-ai/spring-ai-portal
git log --oneline -n 5

# 前后端同版本回滚
/opt/spring-ai/rollback.sh <commit_id>

# 前后端不同版本回滚
/opt/spring-ai/rollback.sh <backend_commit_id> <portal_commit_id>
```

### 4. GitHub 网络异常时临时发布（不拉代码）

```bash
/opt/spring-ai/deploy.sh nopull backend
/opt/spring-ai/deploy.sh nopull portal
/opt/spring-ai/deploy.sh nopull all
```

### 5. 最后确认服务恢复

```bash
cd /opt/spring-ai/spring-ai-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 backend
curl http://127.0.0.1/api/actuator/health
```

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
## 最近更新（2026-03-02）

### 1. Skills 能力（feat(skill): 添加 skills）

本仓库新增了本地 Agent Skill：`ui-ux-pro-max`，目录如下：

- `.agents/skills/ui-ux-pro-max/SKILL.md`
- `.agents/skills/ui-ux-pro-max/data/*`
- `.agents/skills/ui-ux-pro-max/scripts/*`

主要用途：

- 提供 UI/UX 设计规则与检索能力（样式、配色、字体、图表、可访问性等）
- 支持多技术栈参考（React / Next.js / Vue / Svelte / Flutter / SwiftUI 等）
- 可通过脚本进行设计系统生成与规则搜索

示例命令：

```bash
python .agents/skills/ui-ux-pro-max/scripts/search.py "saas dashboard fintech" --design-system
```

> 说明：该能力主要服务于研发流程（设计与前端实现建议），不影响后端运行时 API。

### 2. MCP Gateway 管理能力（feat(mcp): create mcp gateway）

新增 MCP Gateway 管理 API，统一前缀：

- `/ai/mcp-gateway`

#### 2.1 服务端管理接口

- `GET /ai/mcp-gateway/servers`：查询服务器列表
- `POST /ai/mcp-gateway/servers`：创建服务器
- `GET /ai/mcp-gateway/servers/{id}`：查询服务器详情
- `PUT /ai/mcp-gateway/servers/{id}`：更新服务器
- `DELETE /ai/mcp-gateway/servers/{id}`：删除服务器
- `POST /ai/mcp-gateway/servers/{id}/connect`：连接服务器
- `POST /ai/mcp-gateway/servers/{id}/disconnect`：断开服务器
- `POST /ai/mcp-gateway/servers/{id}/ping`：连通性检测

#### 2.2 工具策略接口

- `POST /ai/mcp-gateway/servers/{id}/tools/sync`：同步工具列表
- `PATCH /ai/mcp-gateway/servers/{id}/tools/{toolName}`：更新单个工具策略
- `POST /ai/mcp-gateway/servers/{id}/tools/batch`：批量启停/策略操作
- `POST /ai/mcp-gateway/servers/{id}/tools/{toolName}/debug`：调试执行工具

#### 2.3 配置项（application.yaml / application-prod.yaml）

新增配置：

```yaml
app:
  mcp-gateway:
    stdio-enabled: false
    stdio-command-whitelist: ""
    connect-timeout-ms: 5000
    request-timeout-ms: 10000
```

生产环境可通过以下环境变量覆盖：

- `APP_MCP_GATEWAY_STDIO_ENABLED`
- `APP_MCP_GATEWAY_STDIO_COMMAND_WHITELIST`
- `APP_MCP_GATEWAY_CONNECT_TIMEOUT_MS`
- `APP_MCP_GATEWAY_REQUEST_TIMEOUT_MS`

#### 2.4 数据库变更

`deploy/mysql/init/01-schema.sql` 新增两张表：

- `mcp_gateway_server`：MCP 服务连接配置（HTTP/SSE/STDIO、鉴权、连接参数等）
- `mcp_gateway_tool_policy`：工具级策略（启用、自动执行、成本、排序、同步时间等）

如果是增量升级，请执行对应 DDL 迁移，确保上述两张表存在。

#### 2.5 最小调用示例

创建 MCP 服务器：

```bash
curl -X POST "http://localhost:8080/ai/mcp-gateway/servers" \
  -H "Content-Type: application/json" \
  -d '{
    "serverName": "demo-http-server",
    "connectionType": "HTTP",
    "connectionUrl": "http://127.0.0.1:3001/mcp",
    "enabled": true
  }'
```

连接并同步工具：

```bash
curl -X POST "http://localhost:8080/ai/mcp-gateway/servers/1/connect"
curl -X POST "http://localhost:8080/ai/mcp-gateway/servers/1/tools/sync"
```
