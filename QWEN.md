# QWEN.md — emp-script-ai

## 项目概述

`emp-script-ai` 是一个 Java 工具库，用于统一调用多家大模型服务商（通义千问、OpenAI、Gemini、豆包、Grok、腾讯、DeepSeek、OpenRouter、Anthropic 等）。同时支持通用的 OpenAI 兼容模式（`openai_compat`）和 Anthropic 兼容模式（`anthropic_compat`），可自定义 URL 对接任意兼容端点。核心能力包括：

- **统一请求构建**：通过 `RequestDataFactory` / `RequestAIFactory` 创建 provider-agnostic 的请求体
- **流式输出（SSE）**：`AiStreamOrPost` 支持 SSE 流式和非流式 POST 两种模式
- **XML 场景模式（Mode）解析**：`Modes` 解析 XML 中的 Mode/Step/Prompt/SqlQuery，支持 `stream` 和 `actionSqlRef` 属性
- **MD5 缓存**：`Modes` 内置 xmlContent 的 MD5 缓存，相同内容不重复解析（线程安全）
- **Markdown 代码块提取**：`StringUtils.extractCodeBlocks`

## 技术栈

| 类别 | 详情 |
|------|------|
| **Java** | 17 (temurin) |
| **构建** | Maven |
| **依赖** | `emp-script-utils`, `emp-script`, `slf4j-api` |
| **测试** | JUnit Jupiter 5.7.1 |
| **许可证** | MIT |

## 构建与运行

```bash
# 构建（跳过测试）
mvn -DskipTests package

# 运行测试
mvn -q -DskipTests=false test

# 发布构建（含 GPG 签名，用于 Maven Central 部署）
mvn -B package -Prelease
```

构建产物在 `target/` 目录（含 jar、sources、javadoc）。

## 架构概览

### 核心包结构

```
com.gdxsoft.ai/
├── providers/          # 各 AI 服务商实现
│   ├── qwen/          # 通义千问（OpenAI 兼容接口）
│   ├── openai/        # OpenAI
│   ├── gemini/        # Google Gemini（已有模型类，待接入）
│   ├── doubao/        # 豆包
│   ├── grok/          # Grok
│   ├── tencent/       # 腾讯
│   ├── deepseek/      # DeepSeek（OpenAI 兼容接口）
│   ├── openrouter/    # OpenRouter（OpenAI 兼容接口）
│   ├── anthropic/     # Anthropic Claude（自有 Messages API）
│   ├── openai_compat/ # 通用 OpenAI 兼容模式（自定义 URL）
│   └── anthropic_compat/ # 通用 Anthropic 兼容模式（自定义 URL）
├── modes/             # XML Mode 解析
│   ├── Modes          # 入口，MD5 缓存管理
│   ├── Mode           # 模式定义，包含 Steps、Prompts、Sqls、Apis
│   ├── Step           # 步骤定义，含 stream/actionSqlRef 属性
│   ├── Prompt         # 提示词定义
│   ├── SqlQuery       # SQL 查询定义
│   ├── Api            # API 工具定义
│   └── ParamCheck     # 参数校验定义
├── request/           # 请求/响应抽象接口
│   ├── IRequestAI     # AI 请求接口
│   ├── IRequestData   # 请求数据接口
│   └── IOutEvents     # 输出事件接口
├── ChatManagerBase    # 核心聊天管理（会话、消息、步骤执行）
├── AiStreamOrPost     # SSE 流式/POST 请求处理入口
├── HttpUtils          # HTTP 工具
└── export/            # 动作导出接口 (IAction)
```

### 关键入口点

| 类 | 职责 |
|----|------|
| `Modes` | XML 解析 + MD5 缓存，`loadModes(xml)` 返回 `List<Mode>` |
| `RequestDataFactory.createRequestData(provider)` | 创建 provider 对应的请求数据对象 |
| `RequestAIFactory.createRequestAI(provider)` | 创建 provider 对应的 AI 请求实例 |
| `ChatManagerBase` | 聊天会话管理、参数校验、提示词组装、数据库持久化 |
| `AiStreamOrPost` | Web 层入口，协调 ChatManager + AI 请求 + SSE 输出 |

### 重要模式

- **MD5 缓存**：`Modes` 以 xmlContent 的 MD5 作为键缓存解析结果，同一进程内相同内容直接复用
- **线程安全**：`Modes.loadModes()` 使用双检锁避免并发重复解析
- **Provider 接口**：所有 provider 实现遵循 OpenAI 兼容接口（`IRequestAI`, `IRequestData`）
- **Step 属性**：`<step stream="true|false" actionSqlRef="sql_name">` 控制流式输出和 SQL 引用

## 关键接口说明

### IRequestAI

AI 请求的核心接口，provider 实现需实现以下方法：

| 方法 | 说明 |
|------|------|
| `doPost(IRequestData)` | 非流式 POST 请求 |
| `doStream(IRequestData, PrintWriter)` | SSE 流式请求 |
| `curl(IRequestData)` | 生成 curl 命令（用于调试日志） |
| `extraceJson(String, boolean)` | 从响应中提取 JSON |
| `initUrlAndKey(String, String)` | 初始化 API URL 和密钥 |
| `getTokensUsage()` | 获取令牌使用统计 |

### IRequestData

请求数据构建器接口：

| 方法 | 说明 |
|------|------|
| `model(String)` | 设置模型名称 |
| `stream(boolean)` | 是否流式 |
| `thinking(boolean)` | 是否思考模式 |
| `temperature(double)` / `topP(double)` | 生成参数 |
| `systemMessage(String)` / `userMessage(String)` | 设置消息 |
| `addMessage(String, String)` | 追加消息（content, role） |
| `buildJson()` | 构建 JSON 请求体 |

## 数据库表依赖

项目依赖以下数据库表（通过 `AI_CHAT_MSG`、`AI_CHAT_PARAMS` 等表持久化聊天记录和参数）：

- `AI_CHAT_MSG` — 聊天消息记录（aim_msg, aim_role, aim_step, aim_action 等）
- `AI_CHAT_PARAMS` — 聊天参数存储（aip_name, aip_val）
- 表结构由 `emp-script` / `emp-script-utils` 依赖管理

## 开发约定

- **编码**：UTF-8
- **日志**：使用 SLF4J，日志输出需脱敏（API Key 等敏感信息）
- **SQL**：使用参数化查询防 SQL 注入
- **线程安全**：AI 请求缓存使用 `ConcurrentHashMap`
- **国际化**：`ChatManagerI18nConstants` 提供中英文消息常量

## CI/CD

- GitHub Actions 工作流：`.github/workflows/maven-publish.yml`
- 触发条件：release 事件
- 构建环境：Ubuntu + JDK 17
