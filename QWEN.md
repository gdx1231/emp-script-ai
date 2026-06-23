# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

`emp-script-ai` 是 `com.gdxsoft.easyweb` 生态下的 Java 17 工具库，统一封装多家大模型服务商（OpenAI / Anthropic / Gemini / 通义千问 Qwen / 豆包 Doubao / Grok / 腾讯 Tencent / DeepSeek / OpenRouter）以及通用 OpenAI / Anthropic 兼容模式（自定义 URL）。通过 `RequestDataFactory` / `RequestAIFactory` 创建 provider-agnostic 请求体；通过 `AiStreamOrPost` 提供 SSE 流式与非流式 POST 两种调用方式。

核心包路径 `com.gdxsoft.ai`（注意：是 `ai` 不是 `AI`，目录名按大小写匹配）。

## 构建与运行

```bash
# 构建（跳过测试）
mvn -DskipTests package

# 运行单元测试
mvn -q -DskipTests=false test

# 运行单个测试类
mvn -q test -Dtest=IntegrationTest

# 运行单个测试方法
mvn -q test -Dtest=IntegrationTest#testSseStream

# 发布构建（含 GPG 签名，部署到 Maven Central）
mvn -B package -Prelease

# 仅重新生成 sources/javadoc
mvn -B package -DskipTests
```

构建产物：`target/${artifactId}-${version}.jar`、`*-sources.jar`、`*-javadoc.jar`，以及一个固定的 `target/emp-script-ai-last.jar`（`copy-rename-maven-plugin` 复制，方便其它本地项目引用）。注意 `package` 阶段还会把 jar 复制到 `../../workspace.newVersion/allclass/lib/`（`maven-antrun-plugin`），构建前确认该路径存在或忽略失败。

## 架构概览

### 目录结构（仅列出关键包）

```
com.gdxsoft.ai/
├── ChatManagerBase        # 聊天会话管理（消息、步骤、参数提取、DB 持久化）
├── AiStreamOrPost         # Web 层入口（SSE 流式 + 非流式 POST）
├── AiChatParamsManager    # 参数提取辅助
├── AiMessageUtils         # 消息工具
├── HttpUtils              # HTTP 工具
├── ChatManagerI18nConstants# 中英文错误消息常量
├── console/               # 命令行工具（TranslateSql）
├── export/                # 动作导出接口（IAction）
├── modes/                 # XML Mode 解析
│   ├── Modes              # 入口 + xmlContent MD5 缓存
│   ├── Mode               # 模式定义（Steps/Sqls/Apis/Actions/ParamChecks/UiWelcome/UiComplete）
│   ├── ModeParser         # 纯静态 XML 解析（不持有状态）
│   ├── Step / Prompt / SqlQuery / Api / ApiField / ApiHeader / Action / ParamCheck
│   └── ApiUsageExample
├── providers/             # 具体 provider 实现
│   ├── openai/ openrouter/ qwen/ doubao/ deepseek/ grok/ tencent/    # OpenAI 兼容
│   ├── openaiCompat/ anthropicCompat/                                # 用户自定义 URL 的兼容模式
│   └── anthropic/ gemini/                                            # 各自原生 API 格式
└── request/               # 抽象接口与共享基础类
    ├── IRequestAI / IRequestData / IOutEvents    # provider 接口
    ├── ProviderType                              # provider 枚举（10 个值）
    ├── RequestAIFactory / RequestDataFactory     # 工厂
    ├── RequestAIBase / RequestDataBase           # 公共基类
    ├── style/                                    # ★ 三种 API 风格的抽象基类
    │   ├── OpenAiRequestAI / OpenAiRequestData   #   - OpenAI Chat Completions 风格（Bearer + data:[DONE]）
    │   ├── AnthropicRequestAI / AnthropicRequestData # - Anthropic Messages 风格
    │   └── GeminiRequestAI / GeminiRequestData   #   - Google Gemini 风格
    └── 各种消息 / 工具 / 多模态类型（AiContent/AiTool/McpClient 等）
```

### 关键架构：按 API 风格分层（2025-12 重构后）

每个 provider 实现由两层构成：

1. **风格基类**（`request/style/`）— 封装一种 API 协议：URL 构造、认证头、SSE 帧格式、流结束标记。
2. **Provider 实现**（`providers/<name>/`）— 仅设置 `providerType`、默认 URL，继承风格基类的 HTTP/JSON/SSE 处理逻辑。

新增 provider 时通常只需要新建 `providers/<name>/RequestAI.java` + `RequestData.java` 两个类，继承合适的风格基类。参见 `.qwen/skills/add-ai-provider/SKILL.md`。

### Mode / Step / Prompt XML 模式

`<mode>` 元素通过 `ModeParser.parseMode(Element)`（静态方法）解析为 `Mode` 对象，`Modes.loadModes(xmlContent)` 提供 MD5 缓存：同一进程内 XML 未变化时直接复用 `List<Mode>`，线程安全（`Modes` 内部用同步 + 双检）。

`<step>` 上的关键属性（`Step.java` 字段）：

| 属性 | 默认 | 说明 |
|------|------|------|
| `stream` | true | 是否启用 SSE 流式输出 |
| `innerCall` | false | 内部调用：AI 响应不直接返回给用户（用于参数校验等前置步骤） |
| `multiOnlyUserMsg` | false | 多轮对话模式下，仅提取父 chat 中的用户消息作为历史 |
| `action` | — | 关联的 `Action`（Java 类名，由 `export/IAction` 执行） |
| `actionSqlRef` | — | Action 关联的 `SqlQuery` 名 |
| `api` | — | 关联的 `Api` 名（外部 HTTP 调用） |
| `validateParams` | — | 逗号分隔的参数名列表（`ParamCheck` + AI 自动提取） |

`<prompt>` 三种动态数据源：`sqlRef`（SQL 查询结果）、`action`（Java 类生成）、`api`（外部 HTTP）。

### 关键入口点

| 类 | 用途 |
|----|------|
| `Modes(xml)` / `Modes.loadModes()` | 解析 + MD5 缓存 |
| `Modes.getMode(name)` | 按名取 Mode（返回克隆体防外部修改） |
| `RequestDataFactory.createRequestData(provider)` | 按 `ProviderType` 创建请求体 |
| `RequestAIFactory.createRequestAI(provider)` | 按 `ProviderType` 创建请求实例 |
| `ChatManagerBase` | 会话生命周期、参数校验、提示词组装、DB 持久化、参数提取 |
| `AiStreamOrPost.init(rv, "test", w)` + `processRequest()` | Web 层 SSE 入口 |

## 数据库

依赖表：`AI_CHAT`、`AI_CHAT_MSG`、`AI_CHAT_PARAMS`、`AI_PROVIDER`、`AI_PROVIDER_MODEL`、`AI_PROVIDER_URL`。Schema 由 `emp-script` / `emp-script-utils` 依赖管理。

集成测试使用 HSQLDB 内存库（`src/test/java/com/gdxsoft/ai/test/TestDatabase.java` 启动 + 建表 + 插入 provider 配置），配合 `ewa_conf.xml.example` 与 `ai_settings.json.example`。`ai_settings.json` 与 `ewa_conf.xml` 已被 `.gitignore` 排除（不要提交真实 key）。

API Key 优先从环境变量读取（参见 `skills/ai-mode-xml-authoring/SKILL.md` 与 `.qwen/skills/auto-skill-integration-test-hsqldb/SKILL.md`），fallback 到 `ai_settings.json`。

## 测试

- 单元测试：`src/test/java/` 下若干 `TestXxx.java`，可独立运行（`mvn test -Dtest=TestAllProviders`）。
- 集成测试：`com.gdxsoft.ai.test.IntegrationTest`（HSQLDB + 真实 AI API），需要 `ai_settings.json` 含有效 key 才可通过。运行前：
  ```bash
  cp src/test/resources/ewa_conf.xml.example src/test/resources/ewa_conf.xml
  cp src/test/resources/ai_settings.json.example src/test/resources/ai_settings.json
  # 编辑 ai_settings.json 填入 API Key
  mvn test -Dtest=IntegrationTest
  ```
- 从生产 DB 导出 provider 配置：`mvn exec:java -Dexec.mainClass="com.gdxsoft.ai.test.GenerateAiSettings" -Dexec.classpathScope=test`，输出 `ai_settings.json`（默认脱敏 key）。详见 `GENERATE_AI_SETTINGS.md`。

## 开发约定

- Java 17（`temurin`），UTF-8。日志走 SLF4J，生产环境务必脱敏 API Key。
- 线程安全：`Modes` 与 `ChatManagerBase.REQUEST_AIS` 均使用 `ConcurrentHashMap`。
- i18n：用户可见消息使用 `ChatManagerI18nConstants`（中英文键）。
- 国际化文档：`INTERNATIONALIZATION.md`、`INTERNATIONALIZATION_IMPROVEMENT.md`。

## 项目级 Agent Skills

两个目录：

- `skills/` — 手写合成的高频参考 skill（如 `ai-mode-xml-authoring/`，AI Mode XML schema 速查）。
- `.qwen/skills/` — 自动提取的 agent skill，遇到对应场景应优先查阅：
  - `add-ai-provider/` — 接入新 LLM provider 的标准流程
  - `auto-skill-integration-test-hsqldb/` — 集成测试 HSQLDB 框架配置
  - `auto-skill-sqlserver-schema-sync/` — SQL Server schema 对比与同步
  - `auto-skill-debug-sse-refactoring/` — SSE 相关重构调试

## CI/CD

- `.github/workflows/maven-publish.yml`：触发于 GitHub `release`，Ubuntu + JDK 17 (temurin)，构建后 `mvn deploy` 发布到 GitHub Packages。
- `release` profile 在 `package` 阶段启用 GPG 签名，部署到 Sonatype OSS。

## 相关文档

- `README.md` — 用户视角的功能介绍与快速开始示例（中文）
- `ChatManagerBase.md` — `ChatManagerBase` 详细说明
- `MODE_REFACTOR_SUMMARY.md` — Mode 解析重构记录
- `API_ATTRIBUTE_ADDITION_SUMMARY.md` / `API_IMPLEMENTATION_SUMMARY.md` — provider API 属性演进
- `LOGGER_IMPROVEMENT.md` — 日志脱敏改进
- `INTERNATIONALIZATION*.md` — i18n 实现
- `GENERATE_AI_SETTINGS.md` — provider 配置导出工具
- `INTEGRATION_TEST_DESIGN.md` — 集成测试架构