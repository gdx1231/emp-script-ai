# AGENTS.md

## 项目概述

`emp-script-ai` 是 Easy Web Application Builder（EWA/emp-script 体系）的 AI 请求组件库，Java 17 + Maven 构建，发布为普通 JAR（`com.gdxsoft.easyweb:emp-script-ai:1.0.0`，MIT License，仓库 https://github.com/gdx1231/emp-script-ai）。

核心功能：

- **统一调用多家大模型服务商**：通过工厂类构建 OpenAI / Anthropic 兼容的请求并处理响应（含流式 SSE）。
- **场景模式（Mode）XML 解析**：从 XML 解析 `<mode>` / `<step>` / `<prompt>` / `<sql>` / `<api>` / `<action>` 等定义，驱动多步骤 AI 对话流程。
- **AI 聊天管理**：会话、消息持久化（数据库）、多轮历史截断（条数 + token 上限）、国际化消息。
- **多模态子模块**：文本对话之外，还有图像生成（img）、视频生成（video）、语音转文字（stt）、文字转语音（tts）、音乐生成（music）五套独立的 Provider 体系。
- **视频创作工作流（video/workflow）**：独立进程的多阶段视频生产管线（分镜规划 → 素材生成 → TTS → 分镜视频 → ffmpeg 合成），workflow.json 配置驱动、数据库表持久化状态、CLI 入口。
- **AI API Switch 代理（switchproxy）**：基于 JDK 内置 `com.sun.net.httpserver.HttpServer` 的本地代理，对外暴露 OpenAI / Anthropic 兼容接口，内部转发/转换到真实服务商，带 CLI 管理工具。

## 构建与测试命令

```bash
# 编译
mvn -q -DskipTests compile

# 构建（跳过测试），产物在 target/（jar、sources、javadoc、emp-script-ai-last.jar）
mvn -DskipTests package

# 运行测试
mvn -q -DskipTests=false test

# 发布构建（release profile，含 GPG 签名，部署到 Sonatype OSS）
mvn -B package -Prelease
```

注意：`package` 阶段的 antrun 插件会把产物复制到 `${basedir}/../../workspace.newVersion/allclass/lib/`（本机特定路径），目录不存在时不影响构建成败，但会有提示。`pomjdk17.xml` 是 pom 的备份变体，构建以 `pom.xml` 为准。

## 技术栈与依赖

- **Java 17**（maven-compiler-plugin 3.11.0，source/target 均为 17），编码 UTF-8。工作流执行器使用虚拟线程。
- 核心依赖：`com.gdxsoft.easyweb:emp-script-utils`、`com.gdxsoft.easyweb:emp-script`（版本范围 `[1.1.10, 1.99.99)`，提供 `RequestValue`、`DTTable`、`DataConnection`、`UXml`、`Utils` 等框架能力）、`org.slf4j:slf4j-api` 2.0.16。
- `javax.servlet-api` 4.0.1 与 `javax.websocket-api` 1.1 为 `provided` 作用域（默认激活 `javax` profile，面向 Tomcat 8/9）。
- HTTP 调用使用 JDK 自带 `java.net.http.HttpClient`，无 OkHttp 等第三方 HTTP 客户端。
- 测试依赖：JUnit Jupiter 5.11.3、logback-classic（测试期日志实现）、HSQLDB 2.7.4（集成测试内存库）、mssql-jdbc（测试用）。

## 代码结构（src/main/java/com/gdxsoft/ai/）

根包：

- `AiStreamOrPost.java` — AI 聊天请求的流式（SSE）/非流式（POST）处理入口，协调聊天管理器、请求构建、响应输出、token 用量记录与错误处理。
- `ChatManagerBase.java` / `ChatManagerDb.java` — 聊天管理器：会话生命周期、消息持久化（`@param` 参数化 SQL + `DataConnection`/`DTTable`）、Mode/Step 执行、提示词组织、API/Action 调用、多轮历史截断（`maxHistoryMessages` 默认 30、`maxHistoryTokensK` 默认 100，超出后从最早消息开始删并打 WARN 日志）、国际化（`ChatManagerI18nConstants`）。
- `AiChatParamsManager.java`、`AiMessageUtils.java`（含 Markdown 代码块提取 `extractCodeBlocks`）、`ApiConfig.java`、`HttpUtils.java`（含信任所有证书的开发用 HttpClient 缓存）。

子包：

- `modes/` — Mode XML 解析：`Modes`（按 xmlContent 的 MD5 缓存解析结果，`ConcurrentHashMap` 线程安全；`getMode(name)` 返回克隆体防止外部修改缓存原件）、`Mode`、`Step`、`Prompt`、`SqlQuery`、`Api`、`ApiField`、`ApiHeader`、`Tool`（继承 `Api`，支持 `command` 本地程序执行与 CDATA 调用说明）、`Action`、`ParamCheck`、`ModeParser`。
- `request/` — 请求层抽象：`IRequestData` / `IRequestAI` / `IOutEvents` 接口及 `RequestDataBase` / `RequestAIBase` 基类，`RequestDataFactory` / `RequestAIFactory` 工厂，`ProviderType` 枚举，工具调用与多模态内容模型（`AiTool`、`AiToolCall`、`AiToolResult`、`AiImageContent`、`AiAudioContent`、`AiVideoContent`、`AiWebSearch` 等）、`McpClient` / `McpTool`、`style/`（Anthropic 风格请求）。
- `providers/` — 各服务商实现：`qwen`、`openai`、`anthropic`、`gemini`、`grok`、`doubao`、`tencent`、`deepseek`、`openrouter`、`openaiCompat`（OpenAI 兼容）、`anthropicCompat`（Anthropic 兼容）。多数为 OpenAI 兼容接口风格。新增服务商时在对应包下实现 `RequestData` 等并注册到 `ProviderType` 与两个工厂类。
- `img/` — 图像生成：`IImgProvider`、`ImgClient`、`ImgProviderFactory`、`ImgProviderType`、`ImgOptions`（含 doubao 组图生成 `sequentialImageGeneration`）、`ImgTaskRunner`（统一异步 submit/poll + 自动落库日志，配合 `ImgTaskSubmit`/`ImgTaskStatus`；`ImgProviderBase` 内置本地异步回退——虚拟线程执行 generate + 内存注册表，taskId 为 `local-` 前缀仅进程内有效；Qwen Wanx 覆盖为 DashScope 原生异步 task_id），providers：qwen/doubao/grok/openai/openaiCompat/stability。
- `video/` — 视频生成：`IVideoProvider`、`VideoClient`、`VideoProviderFactory`、`VideoTaskRunner`（异步 submit/poll）、`VideoOptions`（`returnLastFrame()` 尾帧续接），providers：qwen/doubao/jimeng/kling/minimax/openaiCompat。
- `video/workflow/` — 视频创作工作流（独立进程）：
  - `engine/` — `WorkflowCli`（main 入口：start/submit/status 等子命令）、`WorkflowEngine`、`WorkflowScheduler`（DB 轮询 pending 实例 + 全局并发信号量 + 优先级调度）、`WorkflowExecutor`（虚拟线程执行各阶段）。
  - `phase/` — 阶段处理器：`PlanningPhase`（LLM 分镜拆解）、`MaterialsPhase`（图片素材）、`TtsPhase`（配音，可选）、`GeneratingPhase`（分镜视频）、`CompositingPhase`（ffmpeg 合成）。
  - `config/` — `WorkflowConfig`（workflow.json）、`PhaseDef`、`LimitsConfig`；`model/` — `Storyboard` 分镜模型与解析；`db/` — `WorkflowDb`。
  - 状态持久化到 `AI_WF_DEF` / `AI_WF_INSTANCE` / `AI_WF_TASK` / `AI_WF_ASSET` 等 11 张表，workflow.json 按 MD5 去重。设计文档见 `docs/VIDEO_WORKFLOW_PLAN.md`。
- `stt/` — 语音转文字：openai/openaiCompat/azure/google/qwen/doubao/local（qwen、doubao 均走各自 OpenAI 兼容模式 chat/completions + input_audio，qwen 默认模型 qwen3-asr-flash，doubao 用方舟音频理解模型 + 转写提示词）。
- `tts/` — 文字转语音：qwen/doubao——qwen 走 DashScope multimodal-generation 端点（默认 qwen3-tts-flash，响应 output.audio.data/url，url 自动下载），doubao 按模型自动选接口：seed-audio-* 走 tts/create 非流式（默认 seed-audio-1.0，成功无 code 字段），seed-tts-*（如 seed-tts-2.0）走 unidirectional HTTP Chunked 流式（X-Api-Resource-Id 头=模型名，逐行 JSON 分片聚合，speaker 默认 zh_female_xiaohe_uranus_bigtts）。
- `music/` — 音乐生成：`IMusicProvider`、`MusicClient`、`MusicProviderFactory`、`MusicProviderType`、`MusicOptions`（默认 model `music-3.0`，支持 `lyricsOptimizer` 自动歌词、`instrumental` 纯音乐、`audioUrl`/`audioBase64`/`coverFeatureId` 翻唱参考音频）、`MusicComposition`（先歌词后生成的一站式创作），providers：minimax（Music 3.0，含 `music_cover_preprocess` 翻唱预处理与 `lyrics_generation` 歌词生成，hex 音频经 `MusicResponse.save` 落盘）。
- `switchproxy/` — AI API Switch 代理：`SwitchServer`（多地址监听）、`SwitchCli`（`main` 入口：start/list/add-provider/add-model/use-model/add-key 等子命令）、`SwitchConfig`/`ProfileConfig`/`RouteConfig`/`AccessKeyConfig`（XML 配置）、`IpAccessController`（IP 白名单）、`handler/`（Passthrough、Chat2Anthropic、Chat2Responses、Responses2Anthropic、Admin、Status）、`converter/`（格式转换）、`logger/` + `entry/`（请求/响应 XML 日志）。配置默认在 `~/.emp-script-ai/switch.settings.xml`，详细设计见 `docs/SWITCH_DEV_GUIDE.md`。
- `export/IAction.java` — 自定义动作扩展接口；`console/TranslateSql.java` — 控制台工具。

资源文件：`src/main/resources/db/alter_table_ai_chat_add_ref.sql`（AI_CHAT 表结构变更）、`src/main/resources/db/create_workflow_tables.sql`（工作流表 DDL，SQL Server 语法）；`docs/sql/DDL_{HSQL,MYSQL,PG,SQLSERVER}.sql` 为工作流全部 11 张表的多数据库完整建库模板。聊天持久化依赖 EWA 框架的数据库配置（`ewa_conf.xml`）与 AI_CHAT / AI_CHAT_MSG 等表。

## 关键约定与模式

- **Mode XML 是核心配置形态**：`<step stream="true|false" action="..." actionSqlRef="...">` 控制流式与动作 SQL 关联；`<prompt>` 可通过 `sqlRef` / `api`（别名 `tool`）/ `action` / CDATA 提供内容；`<mode>` 上的 `temperature`、`topP`、`thinking`、`responseFormat`、`enableSearch`（联网搜索，请求体附加 `enable_search: true`）、`maxHistoryMessages`、`maxHistoryTokensK` 等属性由 `ChatManagerBase` 消费。`<tools>/<tool>` 是 `<apis>/<api>` 的增强别名：同名（忽略大小写）时 tool 整体覆盖 api；tool 内 CDATA 为调用说明，构建 `apisCheck`（别名 `toolsCheck`）prompt 时自动附加全部工具说明；tool 的 `command` 属性定义本地程序命令模板（`@占位符` 由 LLM args 替换，ProcessBuilder 直起不经 shell，timeout 强杀），非空时执行本地程序而非 URL 调用；tool 的 `useMode` 属性指定子 mode 名，非空时进程内调用该 mode（非流式单次请求，args 作为用户输入并注入 `@占位符`，采样参数取子 mode 定义，调用记录到 AI_PID=父 chat 的新建子 chat）。`<modes>` 根下可放 `<common><apis>/<tools>` 定义跨 mode 共享定义，加载时合并进每个 mode，mode 本地同名定义优先、common 中的被抛弃。完整 schema 参考 `skills/ai-mode-xml-authoring/SKILL.md`。
- **MD5 缓存**：`Modes` 以 xmlContent 的 MD5 为键缓存最近一次解析结果，内容不变直接复用；变更后自动重新解析，无显式清缓存 API。工作流定义（`AI_WF_DEF`）同样以 workflow.json 的 MD5 去重。
- **mode=auto 自动路由**：XML 中用 `<routerMode name="..." default="...">` 显式声明候选（`<route>mode名</route>` 子元素，不声明的 mode 不参与路由），请求参数 `mode=<routerMode name>`（如 `mode=auto`）命中 routerMode 时，`ChatManagerBase.checkParams()` 会先用一次轻量 LLM 分类调用（非流式、temperature 0.3、json_object，复用 apisCheck 的调用方式），根据本轮用户输入 `prompt` 从候选 mode 中选出最合适的并真正替换本次请求的 mode（step/action/采样参数随之切换），每轮都分类。路由结果以 `mode_route` 隐藏消息落库并记录 token 用量；已有会话命中不同 mode 时更新 `AI_CHAT.AI_MODE`。分类失败/无效时按顺序兜底：routerMode 的 `default` 指定的 mode → 已有会话沿用 DB 中的 AI_MODE → 新会话报错（`<reminder>` 子元素配置的提醒词优先，未配置则用 i18n 文案 `ERROR_MODE_ROUTE_FAILED`）。`<reminder>` 支持 `api`（别名 `tool`）属性引用 `<common>` 下的共享 API 并把调用结果作为提醒词，最终内容统一做 `@para` 占位符替换。候选 mode 的 `description` 是路由语义来源，须写清适用场景。
- **代码注释/Javadoc 主要使用中文**，部分类附中英双语注释；新代码请沿用中文注释风格。
- **日志统一走 slf4j**（`LoggerFactory`），不要引入其他日志门面；`Modes` 等少数老类用 `java.util.logging`。
- 数据库操作必须使用参数化查询（沿用 `DataConnection` / `DTTable` 的既有写法），防止 SQL 注入。
- README 中 `com.gdxsoft.ai.servlets.StreamServlet` 与 `StringUtils.extractCodeBlocks` 示例已过时：源码中不存在 `StreamServlet`（SSE 能力现由 `AiStreamOrPost` 提供），`extractCodeBlocks` 已移至 `AiMessageUtils`。引用文档时注意甄别。

## 测试说明

- 测试框架为 JUnit 5（Jupiter）。单元测试在各包下（如 `switchproxy/`、`stt/`、`tts/`、`img/`、`video/`、`music/`、`modes/`、`request/` 的解析与配置测试），不依赖外部服务。
- **集成测试** `com.gdxsoft.ai.test.IntegrationTest` 使用真实 AI API + HSQLDB 内存库，流程为 SSE 流式输出 → AI 回答落库 → 多轮对话验证；未配置 provider 时通过 `assumeTrue` 自动跳过，不会导致构建失败。
- 运行集成测试前需要：
  - `src/test/resources/ewa_conf.xml`：由 `ewa_conf.xml.example` 复制并配置（`TestDatabase` 使用其中名为 `test_hsqldb` 的 HSQLDB 配置）；
  - `src/test/resources/ai_settings.json`：由 `ai_settings.json.example` 复制并填入真实 API Key（含 qwen/openai/anthropic/gemini/deepseek/doubao/grok/openrouter/tencent 等 provider 的 api_url/api_key/model）。该文件已被 `.gitignore` 忽略。
- `com.gdxsoft.ai.test.GenerateAiSettings` 是生成测试配置的辅助工具（见 `docs/GENERATE_AI_SETTINGS.md`）。
- `src/test/java` 根包下的 `TestAllProviders`、`TestListModels`、`ListModelsTest`、`TestDeepSeek` 等是手动联调用的测试，同样依赖真实 API Key。
- 设计文档见 `docs/INTEGRATION_TEST_DESIGN.md`。

## 安全注意事项

- **API Key 一律通过配置文件（`ai_settings.json`、`switch.settings.xml`）或环境变量注入，严禁硬编码进源码**；`*.example` 文件仅作模板。生产环境注意日志脱敏。
- `HttpUtils` 提供信任所有证书的 HttpClient，仅供开发/内网联调，生产慎用。
- switchproxy 暴露网络端口，使用 `AccessKeyConfig`（访问密钥）与 `IpAccessController`（IP 白名单）控制访问，不要将无保护的代理暴露到公网。
- switchproxy 的请求日志会完整记录请求/响应内容（含 prompt），注意日志目录（默认 `~/.emp-script-ai/logs/`）的访问权限。
- Mode XML tool 的 `command` 会在本机执行外部程序，虽经 ProcessBuilder 直起（不经 shell）并有 timeout 强杀，仍须只加载可信来源的 Mode XML。

## CI / 发布

- GitHub Actions：`.github/workflows/maven-publish.yml`，在创建 Release 时触发，Ubuntu + JDK 17（temurin）执行 `mvn -B package`，随后 `mvn deploy` 发布到 GitHub Packages。
- `release` profile 配置 GPG 签名并发布到 Sonatype OSS（Maven Central 渠道），本地勿随意执行。
- 无 pre-commit 钩子、无代码格式化/静态检查插件配置。

## 运行 switchproxy 代理

```bash
mvn -DskipTests package          # 先构建
bin/start.sh                     # 启动代理（等价于 start 子命令）
bin/start.sh list                # 列出供应商和路由
```

`bin/start.sh` / `bin/start.bat` 包装了 `SwitchServer` / `SwitchCli`，使用 `target/emp-script-ai-last.jar` 加上 `mvn dependency:build-classpath` 生成的运行时依赖 classpath。

## 运行视频创作工作流引擎

工作流引擎为独立进程，CLI 入口 `com.gdxsoft.ai.video.workflow.engine.WorkflowCli`（无现成包装脚本，需自行用 `target/emp-script-ai-last.jar` + 依赖 classpath 启动）。核心子命令：

- `start` — 启动引擎：每隔约 10s（可配）从数据库轮询一条 pending 实例占位，在虚拟线程中依次执行 planning → materials → tts（可选）→ generating → compositing 各阶段，每步更新 DB 状态；支持全局最大并发限制。
- `submit` — 提交工作流实例（写入 `AI_WF_INSTANCE`，状态 pending）。
- `status` — 按 uid 查询实例状态。

运行前需先按数据库类型执行 `docs/sql/DDL_*.sql` 建表，并准备 workflow.json 定义文件（示例见 `src/test/resources/test_workflow.json`）。视频合成阶段依赖本机安装 ffmpeg。

## 文档索引（docs/）

- `SWITCH_DEV_GUIDE.md` — switchproxy 代理模块设计与配置 schema
- `VIDEO_WORKFLOW_PLAN.md` — 视频创作工作流架构、表设计与阶段说明
- `ChatManagerBase.md` — 聊天管理器说明
- `INTEGRATION_TEST_DESIGN.md` — 集成测试设计
- `GENERATE_AI_SETTINGS.md` — 测试配置生成工具
- `IMG_API.md`、`DOUBAO_SEQUENTIAL_GENERATION.md` — 图像生成 API 与豆包组图生成
- `MUSIC_API.md`、`refs/minimax-music3.md` — MiniMax 音乐生成 API 与参考文档
- `INTERNATIONALIZATION.md` / `INTERNATIONALIZATION_IMPROVEMENT.md` — 国际化
- `LOGGER_IMPROVEMENT.md` — 请求日志改进说明
- `MODE_REFACTOR_SUMMARY.md`、`API_IMPLEMENTATION_SUMMARY.md` 等 — 历次重构记录
- `refs/` — 各服务商 API 参考文档（qwen/seedance/wan/minimax/happyhorse 等）
