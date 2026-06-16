# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`emp-script-ai` 是一个 Java 工具库，用于统一调用多家大模型服务商（通义千问、OpenAI、Gemini、豆包、Grok、腾讯、DeepSeek、OpenRouter、Anthropic 等）。同时支持通用的 OpenAI 兼容模式（`openai_compat`）和 Anthropic 兼容模式（`anthropic_compat`），可自定义 URL 对接任意兼容端点。核心能力包括：

- **统一请求构建**：通过 `RequestDataFactory` / `RequestAIFactory` 创建 provider-agnostic 的请求体
- **流式输出（SSE）**：`AiStreamOrPost` 支持 SSE 流式和非流式 POST 两种模式
- **XML 场景模式（Mode）解析**：`Modes` 解析 XML 中的 Mode/Step/Prompt/SqlQuery/Api，支持 `stream`、`actionSqlRef`、`innerCall`、`multiOnlyUserMsg` 等属性
- **MD5 缓存**：`Modes` 内置 xmlContent 的 MD5 缓存，相同内容不重复解析（线程安全）
- **多轮对话历史控制**：`ChatManagerBase` 支持配置最大历史消息条数和 token 上限，自动截断旧消息
- **参数提取与校验**：通过 `ParamCheck` + AI 自动从对话上下文中提取结构化参数
- **UI HTML 事件**：`uiWelcome`/`uiComplete` 支持向 SSE 前端推送 HTML 片段
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
│   ├── gemini/        # Google Gemini
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
│   ├── Mode           # 模式定义，含 Steps/Prompts/Sqls/Apis/ParamChecks
│   ├── ModeParser     # XML 解析实现
│   ├── Step           # 步骤定义，含 stream/innerCall/multiOnlyUserMsg/actionSqlRef
│   ├── Prompt         # 提示词定义，支持 sqlRef/action/api/apisCheck
│   ├── SqlQuery       # SQL 查询定义
│   ├── Api            # 外部 API 工具定义
│   ├── ParamCheck     # 输入参数校验定义（int/enum 类型）
│   └── Action         # 动作定义（Java 类引用）
├── request/           # 请求/响应抽象接口
│   ├── IRequestAI     # AI 请求接口（doPost/doStream/curl/extraceJson）
│   ├── IRequestData   # 请求数据接口（流式构建器）
│   ├── IOutEvents     # 输出事件接口
│   ├── ProviderType   # 提供商枚举
│   └── RequestAIFactory / RequestDataFactory  # 工厂类
├── ChatManagerBase    # 核心聊天管理（会话、消息、步骤执行、参数提取）
├── AiStreamOrPost     # Web 层入口，协调 ChatManager + AI 请求 + SSE 输出
├── HttpUtils          # HTTP 工具
├── ChatManagerI18nConstants  # 国际化消息常量
└── export/            # 动作导出接口 (IAction)
```

### 关键入口点

| 类 | 职责 |
|----|------|
| `Modes` | XML 解析 + MD5 缓存，`loadModes(xml)` 返回 `List<Mode>` |
| `Modes.getMode(name)` | 从缓存获取指定 Mode（返回克隆体防外部修改） |
| `RequestDataFactory.createRequestData(provider)` | 创建 provider 对应的请求数据对象 |
| `RequestAIFactory.createRequestAI(provider)` | 创建 provider 对应的 AI 请求实例 |
| `ChatManagerBase` | 聊天会话管理、参数校验、提示词组装、数据库持久化、参数提取 |
| `AiStreamOrPost` | Web 层入口，`init()` 初始化 + `processRequest()` 执行请求 |

### 重要模式

- **MD5 缓存**：`Modes` 以 xmlContent 的 MD5 作为键缓存解析结果，同一进程内相同内容直接复用
- **线程安全**：`Modes.loadModes()` 使用 `ConcurrentHashMap`，`ChatManagerBase.REQUEST_AIS` 使用 `ConcurrentHashMap`
- **Provider 接口**：所有 provider 实现遵循 `IRequestAI` / `IRequestData` 接口
- **Step 属性**：
  - `<step stream="true|false">` — 是否启用流式输出
  - `<step actionSqlRef="sql_name">` — 关联 SQL 片段
  - `<step innerCall="true">` — 内部调用步骤，AI 响应不直接返回给用户，用于参数校验等前置处理
  - `<step validateParams="param1,param2">` — 指定需要校验的参数名列表
  - `<step multiOnlyUserMsg="true">` — 多轮对话时仅提取父级 chat 中的用户消息
- **Prompt 数据源**：`Prompt` 可通过 `sqlRef`（SQL 查询结果）、`action`（Java 类生成）、`api`（外部 HTTP 调用）三种方式动态填充内容
- **ParamCheck 系统**：在 `<mode>` 中定义 `<paramChecks>`，支持 `int`/`enum` 类型校验；`ChatManagerBase.checkInputParams()` 校验 + `saveInputParams()` 通过 AI 从对话上下文提取参数并保存到 `AI_CHAT_PARAMS` 表

### UI HTML 事件系统

`Mode` 支持配置 `uiWelcome`（新会话欢迎 HTML）和 `uiComplete`（完成响应后的 HTML），通过 `uiCompleteTest` SQL 表达式判断是否发送。前端通过 SSE 事件中的 `ui_html` 和 `ui_type` 字段渲染。

### 多轮对话（AI_PID 关联）

`ChatManagerBase` 通过 `AI_CHAT` 表的 `AI_PID` 字段实现父子 chat 关联。`getResolvedPrompt()` 在多轮对话模式下（`multiOnlyUserMsg=true`）会提取父 chat 及所有子 chat 中的用户消息，组装成历史对话上下文。

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
| `thinking(boolean)` | 是否思考模式 |
| `stream(boolean)` | 是否流式 |
| `temperature(double)` / `topP(double)` | 生成参数 |
| `responseFormat(String)` | 响应格式（text/json_object） |
| `systemMessage(String)` / `userMessage(String)` / `assistantMessage(String)` | 设置消息 |
| `addMessage(String, String)` | 追加消息（content, role） |
| `buildJson()` | 构建 JSON 请求体 |

## 数据库表依赖

项目依赖以下数据库表：

| 表名 | 用途 |
|------|------|
| `AI_CHAT` | 聊天会话记录（AI_ID, AI_UID, AI_PID, AI_PROVIDER, AI_MODEL, AI_CUR_STEP 等） |
| `AI_CHAT_MSG` | 聊天消息记录（AIM_MSG, AIM_ROLE, AIM_STEP, AIM_ACTION, AIM_SKIP_APPEND 等） |
| `AI_CHAT_PARAMS` | 聊天参数存储（AIP_NAME, AIP_VAL, AIP_TYPE） |
| `AI_PROVIDER` | AI 服务商配置（AP_CODE, AP_STATUS） |
| `AI_PROVIDER_MODEL` | 服务商模型配置（APM_CODE, APM_STATUS） |
| `AI_PROVIDER_URL` | 服务商 API 地址和密钥（APU_URL, APU_KEY） |

表结构由 `emp-script` / `emp-script-utils` 依赖管理。

## 开发约定

- **编码**：UTF-8
- **日志**：使用 SLF4J，日志输出需脱敏（API Key 等敏感信息）
- **SQL**：使用参数化查询防 SQL 注入
- **线程安全**：AI 请求缓存和 Mode 缓存均使用 `ConcurrentHashMap`
- **国际化**：`ChatManagerI18nConstants` 提供中英文消息常量

## CI/CD

- GitHub Actions 工作流：`.github/workflows/maven-publish.yml`
- 触发条件：release 事件
- 构建环境：Ubuntu + JDK 17 (temurin)
- 发布到 GitHub Packages
