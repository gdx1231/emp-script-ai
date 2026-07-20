# AI API Switch / 代理模块开发说明

## 1. 模块概述

`com.gdxsoft.ai.switchproxy` 是 `emp-script-ai` 内的 AI API 代理子模块，基于 JDK 内置 `com.sun.net.httpserver.HttpServer`，对外暴露 OpenAI / Anthropic 兼容接口，内部转发到真实 AI 服务商。

### 核心功能

| # | 功能 | 说明 |
|---|------|------|
| 1 | 直接转发 | 请求原样转发到目标 provider，不做格式转换 |
| 2 | Chat2Anthropic | 接收 OpenAI Chat 格式请求，转换为 Anthropic Messages API 格式转发，响应再转回 OpenAI 格式 |
| 3 | Chat2Responses | 接收 OpenAI Chat Completions 格式，转换为 Responses API（`/v1/responses`，Codex 格式）转发，响应再转回 Chat Completions SSE 格式 |
| 4 | 请求日志 | SSE 结束后记录完整请求/响应 XML 日志（输入、输出、思考以 CDATA 包裹） |
| 5 | CLI 管理 | 命令行工具：启动服务、添加供应商、添加模型、切换模型 |

### URL 规则

客户端请求 URL 格式：`http://localhost:{port}/{供应商}/{目标后端格式}/v1/...`

- `{供应商}`：profile 名称（如 `qwen`、`claude`、`codex`、`opencode`）
- `{目标后端格式}`：目标 API 的格式类型（`openai` / `anthropic` / `responses`）
- `/v1/...`：实际的 API 路径（如 `/v1/chat/completions`、`/v1/messages`）

示例：
- `http://localhost:8180/qwen/openai/v1/chat/completions` → 转发到 qwen 的 OpenAI 兼容接口
- `http://localhost:8180/claude/anthropic/v1/messages` → 转发到 claude 的 Anthropic Messages API
- `http://localhost:8180/codex/responses/v1/responses` → 转发到 codex 的 Responses API

### 配置文件

```
~/.emp-script-ai/switch.settings.xml
```

---

## 2. 配置文件 Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<switch>
  <!-- 监听配置 -->
  <server host="0.0.0.0" port="8180" />

  <!-- 日志配置 -->
  <log dir="~/.emp-script-ai/logs" />

  <!-- Profile 预置配置（可被多个 route 引用） -->
  <profiles>
    <profile name="codex"
             api-url="https://api.openai.com/v1/responses"
             api-key="sk-xxx"
             model="codex-mini-latest" />

    <profile name="claude"
             api-url="https://api.anthropic.com/v1/messages"
             api-key="sk-ant-xxx"
             model="claude-sonnet-4-20250514"
             max-tokens="4096" />

    <profile name="qwen"
             api-url="https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
             api-key="sk-xxx"
             model="qwen-max" />

    <profile name="opencode"
             api-url="https://api.opencode.ai/v1/responses"
             api-key="sk-xxx"
             model="opencode-1" />
  </profiles>

  <!-- 路由规则（按 path 前缀匹配，顺序优先） -->
  <routes>
    <!-- 直接转发：引用 profile，model 替换为 profile 中配置的值 -->
    <route path="/qwen/openai/v1"
           mode="passthrough"
           profile="qwen" />

    <!-- Chat2Anthropic：引用 claude profile，目标后端为 Anthropic 格式 -->
    <route path="/claude/anthropic/v1"
           mode="chat2anthropic"
           profile="claude" />

    <!-- Chat2Responses：引用 codex profile，目标后端为 Responses API 格式 -->
    <route path="/codex/responses/v1"
           mode="chat2responses"
           profile="codex" />

    <!-- 内联配置（不引用 profile，直接写参数） -->
    <route path="/custom/openai/v1"
           mode="passthrough"
           target="custom"
           api-url="https://custom-api.example.com/v1/chat/completions"
           api-key="sk-custom"
           model="custom-model" />
  </routes>
</switch>
```

### Profile 属性说明

| 属性 | 必填 | 说明 |
|------|------|------|
| `name` | ✅ | Profile 唯一标识，供 route 引用 |
| `api-url` | ✅ | 真实 AI API 的 URL |
| `api-key` | ✅ | 认证密钥 |
| `model` | ✅ | 转发时替换的模型名称（路由级固定替换） |
| `max-tokens` | — | Anthropic 必填参数，默认 4096 |

### Route 属性说明

| 属性 | 必填 | 说明 |
|------|------|------|
| `path` | ✅ | 客户端请求的 URL path，格式：`/{供应商}/{目标后端格式}/v1`，前缀匹配 |
| `mode` | ✅ | `passthrough` / `chat2anthropic` / `chat2responses` |
| `profile` | 二选一 | 引用 profile name，继承其 api-url/api-key/model 等配置 |
| `target` | 二选一 | 目标 provider 名称（仅日志标识），内联配置时使用 |
| `api-url` | 内联必填 | 真实 AI API 的 URL（内联配置时必填） |
| `api-key` | 内联必填 | 认证密钥（内联配置时必填） |
| `model` | — | 覆盖 profile 中的 model（可选） |

### Model 替换规则

- 当 route 配置了 `model`（或引用的 profile 有 `model`）时，**客户端请求中的 `model` 字段将被替换为配置值**
- 优先级：route 的 model > profile 的 model > 客户端原始 model
- 适用于所有 mode：passthrough 也会替换 model 后转发

---

## 3. 代理模式详解

### 3.1 直接转发 (passthrough)

```
Client ──[OpenAI/Anthropic格式]──▶ Switch ──[原样]──▶ Target API
  ◀──[原样 SSE]───────────────────  ◀──────────────
```

- 请求 body 原样转发，仅替换 `Authorization` / `x-api-key` 头
- SSE 响应逐行透传，同时缓存每行内容用于日志
- 适用于：已有 OpenAI 兼容客户端 → 任意 OpenAI 兼容后端

### 3.2 Chat2Anthropic (chat2anthropic)

```
Client ──[OpenAI Chat格式]──▶ Switch ──[转换]──▶ Anthropic Messages API
  ◀──[转回OpenAI SSE]─────────────  ◀──────────────
```

**请求转换（OpenAI → Anthropic）：**

| OpenAI 字段 | Anthropic 字段 | 转换规则 |
|-------------|----------------|----------|
| `messages[{role:"system"}]` | `system` | 提取为独立顶层字段 |
| `messages[{role:"user/assistant"}]` | `messages[{role:"user/assistant"}]` | 直接映射 |
| `model` | `model` | 使用 route 配置的 model 覆盖 |
| — | `max_tokens` | 使用 route 配置的 max-tokens |
| `temperature` | `temperature` | 直接映射 |
| `stream` | `stream` | 直接映射 |
| `tools[{type:"function",function:{...}}]` | `tools[{name,description,input_schema}]` | 格式转换 |

**响应转换（Anthropic SSE → OpenAI SSE）：**

| Anthropic 事件 | OpenAI SSE 输出 |
|----------------|-----------------|
| `content_block_delta` → `delta.text` | `data: {"choices":[{"delta":{"content":"..."}}]}` |
| `message_delta` → `usage` | `data: {"choices":[{"delta":{}}],"usage":{...}}` |
| `message_start` | 忽略（或提取 usage） |
| 流结束 | `data: [DONE]` |

**思考内容（Extended Thinking）：**
- Anthropic `thinking` content block → OpenAI 格式中映射为 `reasoning_content` 字段
- 日志中单独记录 `<thinking><![CDATA[...]]></thinking>`

### 3.3 Chat2Responses (chat2responses)

```
Client ──[OpenAI Chat格式]──▶ Switch ──[转换]──▶ Responses API (/v1/responses)
  ◀──[转回OpenAI SSE]─────────────  ◀──────────────
```

**请求转换（OpenAI Chat Completions → Responses API）：**

| Chat Completions 字段 | Responses API 字段 | 转换规则 |
|----------------------|-------------------|----------|
| `model` | `model` | 使用 route 配置的 model 覆盖 |
| `messages[{role:"system"}]` | `instructions` | 提取并拼接为单个字符串 |
| `messages[{role:"user"}]` | `input[{role:"user", content:...}]` | 文本 → `input_text`，图片 → `input_image` |
| `messages[{role:"assistant"}]` | `input[{role:"assistant", content:...}]` | 文本 → `output_text`，tool_calls → `function_call` output item |
| `messages[{role:"tool"}]` | `input[{type:"function_call_output", call_id, output}]` | 映射为 tool 输出项 |
| `temperature` | `temperature` | 直接映射 |
| `max_tokens` | `max_output_tokens` | 字段名不同 |
| `stream` | `stream` | 直接映射 |
| `tools[{type:"function", function:{...}}]` | `tools[{type:"function", name, description, parameters}]` | `function.parameters` → `parameters`，`function.description` → `description` |
| `tool_choice` | `tool_choice` | 格式差异：`{type:"function", function:{name:"..."}}` → `{type:"function", name:"..."}` |
| — | `previous_response_id` | 可选，用于多轮对话关联（从上一轮响应中提取 `id`） |

**响应转换（Responses API SSE → OpenAI Chat Completions SSE）：**

| Responses API 事件 | OpenAI Chat Completions SSE 输出 |
|-------------------|--------------------------------|
| `response.created` | 忽略（或映射为 `{"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":...,"model":...,"choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}`） |
| `response.output_text.delta` → `delta` | `data: {"id":"chatcmpl-xxx","choices":[{"delta":{"content":"..."}}]}` |
| `response.output_text.done` | 忽略（文本已完成标记） |
| `response.function_call_arguments.delta` → `delta` | 累积到 tool_call 的 arguments 缓冲，不立即输出 |
| `response.function_call_arguments.done` | `data: {"choices":[{"delta":{"tool_calls":[{"index":N,"id":"...","function":{"name":"...","arguments":"..."}}]}}]}` |
| `response.completed` → `usage` | `data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":...,"completion_tokens":...,"total_tokens":...}}` |
| `response.completed` → `output` 中的 `reasoning` items | 映射为 `reasoning_content` 字段（如有） |
| 流结束 | `data: [DONE]` |

**关键差异说明：**

| 维度 | Chat Completions | Responses API |
|------|-----------------|---------------|
| 对话历史 | `messages[]` 数组，包含完整对话 | `input[]` 数组，包含结构化 items（text/function_call/function_call_output） |
| 系统提示 | `messages[{role:"system"}]` | 独立顶层字段 `instructions` |
| 工具调用输出 | `messages[{role:"tool", tool_call_id, content}]` | `input[{type:"function_call_output", call_id, output}]` |
| Token 限制 | `max_tokens` | `max_output_tokens` |
| 工具定义 | `tools[].function.parameters` | `tools[].parameters`（扁平化） |
| 工具选择 | `tool_choice.function.name` 嵌套 | `tool_choice.name` 扁平 |
| 多轮关联 | 客户端维护完整 `messages[]` | 可用 `previous_response_id` 服务端关联 |
| 输出结构 | `choices[].message` | `output[]` 数组（含 text/reasoning/function_call 等多种 item） |
| 结束原因 | `choices[].finish_reason`（`stop`/`tool_calls`/`length`） | `response.completed` 中 `output[].status` + 整体 `status` |

**思考内容（Reasoning）：**
- Responses API 的 `reasoning` output item（如 o1/o3 模型）→ 映射为 OpenAI 格式的 `reasoning_content` 字段
- 日志中单独记录 `<thinking><![CDATA[...]]></thinking>`

### 3.4 Chat2Requirement (chat2requirement)

预留扩展点。实现 `IFormatConverter` 接口，按需求自定义请求/响应转换逻辑。

---

## 4. 请求日志

### 4.1 日志触发时机

SSE 流结束后（收到 `[DONE]` 或连接关闭），异步写入 XML 日志文件。

### 4.2 日志文件路径

```
{log.dir}/{yyyy-MM-dd}/{request-id}.xml
```

### 4.3 设计要点：两阶段构建 + DOM 对象输出

日志构建分两阶段：

**阶段一：流式累积（SSE 进行中）**

`ProxyHandler` 在每个上游 SSE 回调里调用 `RequestLogEntry.appendXxx()`：
- `appendOutput(chunk)` / `appendThinking(chunk)` → 直接追加到对应文本缓冲
- `appendRawSseLine(line)` → 累积原始 SSE 行（**仅供阶段二使用，不写入日志**）

**阶段二：结构化提取（SSE 结束后）**

收到结束标记后调用 `RequestLogEntry.finalize()`，按目标格式解析累积的 SSE 行，填充结构化字段（`finish-reason`、`response-id`、`tool-calls`、`usage` 等）。

最后 `RequestLogger.log(entry)` 把整个 `Document` 序列化输出。**所有原始 JSON / 文本内容通过 `Document.createCDATASection()` 写入**，由序列化器负责转义（含 `]]>` 自动拆段），业务代码不参与字符串拼接。

| 节点 / 属性 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `<input>` / `<converted-input>` | CDATA | 阶段一 | 原始 / 转换后的请求 body |
| `<output>` / `<thinking>` | CDATA | 阶段一（流式追加） | 完整文本输出 / 思考 |
| `<tool-calls>` | 子元素列表 | 阶段二（提取） | 结构化工具调用，零个时省略 |
| `<usage>` | 属性元素 | 阶段二（提取） | token 用量 |
| `finish-reason` / `response-id` | 根 `<request>` 属性 | 阶段二（提取） | 结束原因 / 上游响应 ID |
| `<headers>` / `<header>` | 普通元素 | 阶段一 | 请求头（`setTextContent`，库负责 XML 转义） |

**日志构建是单线程顺序写**，避免并发 append 同一缓冲。

### 4.4 CDATA 安全性

- 业务侧只调用 `Document.createCDATASection(text)`，**不接触字符串拼接**。
- JDK 默认 Xalan serializer 在遇到 `]]>` 时自动拆分为 `]]]]><![CDATA[>`，文档保持 well-formed。
- `DocumentBuilderFactory` 禁用 DTD / 外部实体（`disallow-doctype-decl=true`），防 XXE。
- 单元测试覆盖 `]]>` 拆段行为（见 9.4）。

### 4.5 日志 XML 格式

```xml
<?xml version="1.0" encoding="UTF-8"?>
<request id="req-20260623-001"
         timestamp="2026-06-23T14:30:00"
         mode="chat2anthropic"
         target="anthropic"
         model="claude-sonnet-4-20250514"
         response-id="msg_01XYZ..."
         finish-reason="end_turn"
         duration-ms="3521">

  <!-- 请求头 -->
  <headers>
    <header name="Content-Type">application/json</header>
  </headers>

  <!-- 输入（原始请求 body） -->
  <input><![CDATA[
    {"model":"gpt-4o","messages":[{"role":"user","content":"你好"}],"stream":true}
  ]]></input>

  <!-- 转换后的请求 body（passthrough 模式省略此节点） -->
  <converted-input><![CDATA[
    {"model":"claude-sonnet-4-20250514","system":"","messages":[{"role":"user","content":"你好"}],"max_tokens":4096,"stream":true}
  ]]></converted-input>

  <!-- 思考内容（如有） -->
  <thinking><![CDATA[
    用户问了个好问题...
  ]]></thinking>

  <!-- 完整输出（所有 SSE content 拼接） -->
  <output><![CDATA[
    你好！有什么可以帮助你的吗？
  ]]></output>

  <!-- 结构化工具调用（如有，零个时整段省略） -->
  <tool-calls>
    <tool-call id="toolu_01ABC" name="get_weather">
      <arguments><![CDATA[{"city":"北京"}]]></arguments>
      <result><![CDATA[{"temperature":22,"condition":"晴"}]]></result>
    </tool-call>
  </tool-calls>

  <!-- Token 用量 -->
  <usage prompt-tokens="15" completion-tokens="42" total-tokens="57" />
</request>
```

---

## 5. 核心类设计

### 5.1 包结构

```
com.gdxsoft.ai.switchproxy/
├── SwitchServer.java          # JDK HttpServer 启动/停止入口
├── SwitchCli.java             # CLI 子命令入口（start / add-provider / add-model / use-model）
├── SwitchConfig.java          # 配置解析（解析 switch.settings.xml）
├── ProfileConfig.java         # Profile 配置 POJO
├── RouteConfig.java           # 单条路由配置 POJO
├── handler/
│   ├── ProxyHandler.java      # HttpHandler 基类（公共逻辑：读 body、日志、错误处理）
│   ├── PassthroughHandler.java    # 直接转发
│   ├── Chat2AnthropicHandler.java # OpenAI → Anthropic 转换
│   └── Chat2ResponsesHandler.java # Chat Completions → Responses API 转换
├── converter/
│   ├── IFormatConverter.java      # 格式转换接口
│   ├── OpenAiToAnthropic.java     # OpenAI → Anthropic 请求转换
│   ├── AnthropicToOpenAi.java     # Anthropic → OpenAI 响应转换
│   ├── ChatToResponses.java       # Chat Completions → Responses 请求转换
│   └── ResponsesToChat.java       # Responses → Chat Completions 响应转换
├── entry/
│   └── RequestLogEntry.java   # 流式构建日志条目（append-only builder）
└── logger/
    └── RequestLogger.java     # 基于 DOM 的 XML 日志写入
```

### 5.2 类职责

#### SwitchServer

```java
public class SwitchServer {
    public static void main(String[] args) throws Exception;
    public void start();   // 解析配置 → 创建 HttpServer → 注册 Handler → 启动
    public void stop();    // 优雅关闭
}
```

- 读取 `~/.emp-script-ai/switch.settings.xml`
- 为每个 `<route>` 注册一个 `HttpHandler`
- 支持 `SIGTERM` 优雅关闭

#### SwitchConfig

```java
public class SwitchConfig {
    public static SwitchConfig load();              // 从默认路径加载
    public static SwitchConfig load(Path xmlPath);  // 从指定路径加载
    public List<RouteConfig> getRoutes();
    public String getHost();
    public int getPort();
    public String getLogDir();
}
```

- 使用 JDK 内置 `javax.xml.parsers.DocumentBuilder` 解析 XML
- 支持配置文件热加载（可选，后续迭代）

#### ProxyHandler（基类）

```java
public abstract class ProxyHandler implements HttpHandler {
    protected RouteConfig route;
    protected RequestLogger logger;

    @Override
    public void handle(HttpExchange exchange) throws IOException;
    // 公共流程：
    // 1. 读取请求 body
    // 2. 调用 abstract method 构建转发请求
    // 3. 发起 HTTP 请求到目标 API
    // 4. 处理 SSE 响应 → 写回客户端
    // 5. SSE 结束后写日志

    protected abstract byte[] buildForwardBody(byte[] originalBody);
    protected abstract Map<String, String> buildForwardHeaders(byte[] originalBody);
    protected abstract void handleSseLine(String line, OutputStream clientOut, StringBuilder logBuf);
}
```

#### PassthroughHandler

- `buildForwardBody()` → 返回原始 body
- `buildForwardHeaders()` → 复制原始 headers，替换认证头
- `handleSseLine()` → 原样写入客户端 + 追加到日志缓冲

#### Chat2AnthropicHandler

- `buildForwardBody()` → 调用 `OpenAiToAnthropic.convert()` 转换请求 JSON
- `buildForwardHeaders()` → 设置 `x-api-key` + `anthropic-version`
- `handleSseLine()` → 调用 `AnthropicToOpenAi.convertLine()` 转换 SSE 行 → 写入客户端

#### RequestLogger

```java
public class RequestLogger {
    public void log(RequestLogEntry entry);  // 把 entry 结构化为 DOM 并写文件
}
```

- 基于 `org.w3c.dom.Document` 构建日志对象，`Transformer` 序列化输出
- 所有原始 JSON / SSE 内容走 `Document.createCDATASection()`，由序列化器保证 CDATA 边界合法（含 `]]>` 自动拆段）
- `DocumentBuilderFactory` 禁用 DTD / 外部实体（`disallow-doctype-decl=true`），防 XXE
- 写文件：`CREATE + TRUNCATE_EXISTING`；若需更强原子性可先写 `.tmp` 再 `Files.move(..., ATOMIC_MOVE)`
- 按日期分目录：`{log.dir}/{yyyy-MM-dd}/{request-id}.xml`

#### RequestLogEntry

```java
public class RequestLogEntry {
    // === 阶段一：流式累积（SSE 进行中） ===
    public void appendOutput(String chunk);          // 追加到 <output> 缓冲
    public void appendThinking(String chunk);        // 追加到 <thinking> 缓冲
    public void appendRawSseLine(String line);       // 累积原始 SSE 行（不写入日志，仅供 finalize 用）

    // === 阶段二：结构化提取（SSE 结束后调用一次） ===
    public void finalize();                           // 解析累积的 SSE 行，填充 finishReason / responseId / toolCalls / usage

    // === 一次性设置的字段 ===
    public void setId(String id);
    public void setMode(String mode);
    public void setTarget(String target);
    public void setModel(String model);
    public void setInput(String input);
    public void setConvertedInput(String convertedInput);
    public void setHeaders(Map<String, String> headers);
    // ...
}
```

- 两阶段生命周期：阶段一由 `ProxyHandler` 在 SSE 回调里调用 append 方法；阶段二在 SSE 结束后调用一次 `finalize()`，由各 mode 对应的 extractor（如 `OpenAiExtractor` / `AnthropicExtractor`）按上游协议解析累积数据
- `appendRawSseLine` 仅供 `finalize()` 使用，**不会出现在最终日志里**
- 由对应 `ProxyHandler` 单线程持有，无需线程安全

### 5.3 格式转换接口

```java
public interface IFormatConverter {
    /** 将客户端请求 body 转换为目标 API 格式 */
    JSONObject convertRequest(JSONObject clientRequest, RouteConfig route);

    /** 将目标 API 的 SSE 行转换为客户端期望的格式 */
    String convertSseLine(String rawLine);
}
```

---

## 6. 复用现有代码

| 现有类 | 路径 | 复用方式 |
|--------|------|----------|
| `HttpUtils` | `com.gdxsoft.ai.HttpUtils` | 复用 `createHttpClient()` 创建信任所有证书的 HttpClient |
| `OpenAiRequestAI.extraceJson()` | `request/style/OpenAiRequestAI.java` | 参考其 OpenAI SSE 解析逻辑，提取 `choices[0].delta.content` |
| `AnthropicRequestAI.extraceJson()` | `request/style/AnthropicRequestAI.java` | 参考其 Anthropic SSE 解析逻辑，提取 `content_block_delta` / `message_delta` |
| `OpenAiRequestData.build()` | `request/style/OpenAiRequestData.java` | 参考 OpenAI 请求体构建格式 |
| `AnthropicRequestData.build()` | `request/style/AnthropicRequestData.java` | 参考 Anthropic 请求体构建格式（system 独立、max_tokens 必填） |
| `DefaultOutEvents` | `request/DefaultOutEvents.java` | 参考 SSE `data: {json}\n\n` 输出格式 |

> **注意**：代理模块是独立的 HTTP 中转层，不直接继承 `RequestAIBase`，而是用 `java.net.http.HttpClient` 直接发起转发请求。格式转换逻辑参考但不依赖风格基类的内部实现。

---

## 7. SSE 流式代理核心流程

```
1. 客户端 POST → Switch HttpHandler
2. 读取请求 body（byte[]）
3. 根据 mode 转换请求格式
4. 用 HttpClient 发起转发请求（HttpResponse.BodyHandlers.ofLines()）
5. 设置响应头 Content-Type: text/event-stream
6. 逐行处理上游 SSE：
   a. passthrough → 原样写入客户端 OutputStream
   b. chat2anthropic → 解析 Anthropic 事件 → 转为 OpenAI SSE → 写入客户端
   c. 写入客户端的同时，按需调用 `entry.appendOutput(chunk)` / `entry.appendThinking(chunk)` / `entry.appendRawSseLine(line)`
7. 上游流结束 → 发送 data: [DONE]（如需要）
8. 调用 `entry.finalize()` 提取 `finish-reason` / `response-id` / `tool-calls` / `usage`
9. `RequestLogger.log(entry)` 写入 XML 日志文件
```

---

## 8. 构建与运行

```bash
# 构建
mvn -DskipTests package

# 运行（需要 classpath 包含依赖）
java -cp "target/emp-script-ai-${version}.jar:lib/*" com.gdxsoft.ai.switchproxy.SwitchServer

# 或直接运行
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) \
  com.gdxsoft.ai.switchproxy.SwitchServer
```

---

## 9. 测试方案

### 9.1 直接转发测试

```bash
curl -X POST http://localhost:8180/qwen/openai/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }'
```

### 9.2 Chat2Anthropic 测试

```bash
# 以 OpenAI 格式发送，代理自动转为 Anthropic 后端
curl -X POST http://localhost:8180/claude/anthropic/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Hello"}
    ],
    "stream": true
  }'
```

预期：
- 代理收到 OpenAI 格式请求
- 转换为 Anthropic Messages 格式转发
- 收到 Anthropic SSE 响应
- 转回 OpenAI SSE 格式返回客户端
- 日志文件记录完整输入/输出/思考

### 9.3 日志验证

```bash
# 检查日志文件
cat ~/.emp-script-ai/logs/2026-06-23/req-*.xml
```

验证：
- XML 格式正确
- `<input>` 包含原始请求 JSON（CDATA）
- `<output>` 包含完整回答文本（CDATA）
- `<thinking>` 包含思考内容（如有）
- `<tool-calls>` 包含结构化工具调用列表（如有）
- 根属性 `finish-reason` / `response-id` 正确
- `<usage>` 包含 token 用量
- **不包含 `<raw-sse>` 节点**

### 9.4 RequestLogger 单元测试

`src/test/java/com/gdxsoft/ai/switchproxy/logger/RequestLoggerTest.java`：

- **CDATA 拆段**：构造含 `]]>` 的输入，断言序列化结果包含 `]]]]><![CDATA[>`（即 JDK serializer 自动拆段）
- **XXE 防护**：用 `<!DOCTYPE ... [<!ENTITY xxe SYSTEM "file:///...">]>` 注入，确认被 `disallow-doctype-decl` 拒绝
- **完整文档结构**：构造一个 mock `RequestLogEntry`，读取写入文件并解析回来，断言所有节点 / 属性 / CDATA 内容完整还原
- **finalize 提取**：模拟累积的 OpenAI / Anthropic SSE 行，调用 `finalize()` 后断言 `finishReason` / `responseId` / `toolCalls` / `usage` 正确填充
- **raw-sse 不落盘**：构造 entry 显式调用 `appendRawSseLine`，`finalize()` 后写入文件，断言文件中**不存在** `<raw-sse>` 节点
- **并发安全**（可选）：多个线程同时 `log()` 不同 entry，确认互不覆盖

---

## 10. CLI 命令

统一入口：`java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli <command> [args]`

### 10.1 start — 启动代理服务

```bash
java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli start
```

读取 `~/.emp-script-ai/switch.settings.xml`，启动 HttpServer。

### 10.2 add-provider — 添加供应商

```bash
java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli add-provider \
  --name qwen \
  --api-url "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" \
  --api-key "sk-xxx" \
  --format openai \
  --model "qwen-max"
```

参数说明：
- `--name`：供应商名称（profile name）
- `--api-url`：API 地址
- `--api-key`：API 密钥
- `--format`：目标后端格式（`openai` / `anthropic` / `responses`）
- `--model`：默认模型

效果：在 XML 的 `<profiles>` 中添加 `<profile>`，并在 `<routes>` 中自动生成对应路由：
```xml
<route path="/{name}/{format}/v1" mode="{根据format推断}" profile="{name}" />
```

### 10.3 add-model — 给供应商添加模型

```bash
java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli add-model \
  --provider qwen \
  --model "qwen-turbo" \
  --api-url "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
```

效果：在指定 profile 下添加一个额外的模型配置（可选覆盖 api-url）。

### 10.4 use-model — 切换供应商的当前模型

```bash
java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli use-model \
  --provider qwen \
  --model "qwen-turbo"
```

效果：修改指定 profile 的 `model` 属性，后续请求将使用新模型。

### 10.5 list — 列出所有供应商和模型

```bash
java -cp ... com.gdxsoft.ai.switchproxy.SwitchCli list
```

输出示例：
```
Profiles:
  qwen      model=qwen-max       format=openai     url=https://dashscope...
  claude    model=claude-sonnet-4 format=anthropic  url=https://api.anthropic...
  codex     model=codex-mini      format=responses  url=https://api.openai...

Routes:
  /qwen/openai/v1     → passthrough     profile=qwen
  /claude/anthropic/v1 → chat2anthropic  profile=claude
  /codex/responses/v1  → chat2responses  profile=codex
```

---

## 11. 开发顺序

| 阶段 | 内容 | 文件 |
|------|------|------|
| 1 | 配置解析 | `SwitchConfig.java`, `ProfileConfig.java`, `RouteConfig.java` |
| 2 | CLI 框架 | `SwitchCli.java`（start / add-provider / add-model / use-model / list） |
| 3 | HttpServer 骨架 | `SwitchServer.java`, `ProxyHandler.java` |
| 4 | 直接转发 | `PassthroughHandler.java` |
| 5 | 格式转换 | `IFormatConverter.java`, `OpenAiToAnthropic.java`, `AnthropicToOpenAi.java` |
| 6 | Chat2Anthropic | `Chat2AnthropicHandler.java` |
| 7 | Chat2Responses | `ChatToResponses.java`, `ResponsesToChat.java`, `Chat2ResponsesHandler.java` |
| 8 | 请求日志 | `RequestLogger.java`, `RequestLogEntry.java` |
| 9 | 集成测试 | curl 端到端验证 + `RequestLoggerTest` 单元测试 |
