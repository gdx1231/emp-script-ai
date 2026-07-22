---
name: ai-mode-xml-authoring
description: emp-script-ai 及其下游项目（travelagent、pf2023）所用 AI Mode XML schema 的撰写参考。覆盖 `<mode>` / `<step>` / `<prompt>` / `<apis>` / `<tools>` / `<common>` / `<sqls>` / `<actions>` / `<paramChecks>` / `<ui>` 各块结构、prompt 数据源（sqlRef / api / tool / action / CDATA）、函数调用 prompt（`apisCheck` / `toolsCheck`，工具调用说明自动附加）、URL / 请求占位符、本地程序工具（command）、输出自定义标签（`<day>`、`<rq>`、`<cid>`、`<enj>`、`<id>`、`<num>`、`<sersday>`、`<prices>`、`<gn>`）以及 step 控制属性（`innerCall`、`validateParams`、`multiOnlyUserMsg`、`action`、`actionSqlRef`）。
source: synthesized
extracted_at: '2026-06-22'
synthesized_from:
  - /Users/admin/java/gyap.org/travelagent/src/main/resources/define/travelAgent.xml
  - /Users/admin/java/gyap.org/pf2023/src/main/java/ai_mode.xml
---

# AI Mode XML 撰写参考

Mode XML 文件由 `emp-script-ai` 的 `Modes.loadModes(xml)` 解析。它定义一个或多个命名 `<mode>` 流程，把系统提示、用户提示、SQL 注入数据、HTTP 注入数据、Java 动作、参数校验、HTML UI 事件组合起来。每个解析出的 mode 由 `ChatManagerBase` 消费，通过 `AiStreamOrPost` 发送给 LLM。

## 文件骨架

```xml
<?xml version="1.0" encoding="UTF-8"?>
<modes>
    <mode name="..." description="..." temperature="0.3" topP="0.7" thinking="false">
        <step name="..." description="..." stream="true">
            <prompts>
                <prompt name="..." role="system"><![CDATA[ ... ]]></prompt>
                <prompt name="..." role="user" sqlRef="..." api="..." action="..." prefix="..." />
                ...
            </prompts>
        </step>
        <step name="export" stream="false" action="saveLead" actionSqlRef="getLastResponse" />
        <apis>     <api name="..." url="..." method="get|post" refRequest="true" /> ... </apis>
        <sqls>     <sql name="...">SELECT ...</sql> ... </sqls>
        <actions>  <action name="..." class="pkg.ClassName" /> ... </actions>
        <paramChecks> <paramCheck name="..." type="int|enum|date" promptRule="..." /> ... </paramChecks>
        <ui>
            <welcome><![CDATA[<div>...</div>]]></welcome>
            <complete test="@fullText like '%&lt;day&gt;%'"><![CDATA[<div>...</div>]]></complete>
        </ui>
    </mode>
    ...
</modes>
```

`<mode>` 内多个 `<step>` 按文档顺序串行执行；每个 step 的 prompts 拼接到对话末尾再调用 LLM。运行期状态（chat id、ai_id、request_id、ai_provider、ai_model）由框架自动串联。

## `<mode>` 属性

| 属性 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 查找键，`Modes.getMode(name)` 返回克隆体。 |
| `description` | 是 | 中文描述。 |
| `temperature` | 否 | 小数；结构化抽取建议 `0.1`，对话建议 `0.7`。 |
| `topP` | 否 | 小数。 |
| `thinking` | 否 | `"true"` 启用推理模式（provider 支持时）。 |
| `responseFormat` | 否 | `"json_object"` 强制 JSON 输出。 |
| `maxHistoryMessages` | 否 | 多轮历史消息数上限（默认 30）。 |
| `maxHistoryTokensK` | 否 | 历史 token 上限，单位 K（默认 100），超限会截断并打 WARN。 |
| `debugOutput` | 否 | 回显 LLM 原始输出，便于调试。 |

## `<step>` 属性

| 属性 | 作用 |
|------|------|
| `name` | step 标识。 |
| `description` | 中文描述。 |
| `stream` | `"true"` 走 SSE 流式；`"false"` 走完整 POST。默认 `true`。 |
| `innerCall` | `"true"` 表示本步骤对用户隐藏（用于预校验、参数抽取等），LLM 响应留在对话历史但不发给客户端。 |
| `multiOnlyUserMsg` | `"true"` 时，多轮对话中只把父 chat 的用户消息拉入历史。 |
| `validateParams` | 逗号分隔的 `<paramCheck>` 名，step 运行前先校验这些参数。 |
| `action` | `<action>` 中定义的 Java 类名，在 LLM 响应**之后**调用。 |
| `actionSqlRef` | `<sql>` 名，先执行该 SQL（通常取最后一条 assistant 消息），把结果传给 `action`。 |
| `api` | 在调用 LLM 之前，先调 `<api>` 取数注入到 prompt。 |

典型三段式：`<step1 validate innerCall="true">` 校验参数 → `<step createEnqJny stream="true">` 生成内容 → `<step export stream="false" action="...">` 落库。

## `<prompt>` 的 4 种数据源

每个 prompt 必须有 `name` + `role`（`system` 或 `user`）。内容来自以下**唯一一种**来源：

| 属性 | 来源 | 说明 |
|------|------|------|
| （无） | 内联 `<![CDATA[...]]>` | 纯文本或 JSON schema 示例。 |
| `sqlRef="name"` | `<sqls>/<sql name="name">` | 结果按 `dataType="json"` 或 `dataGroupField="..."` 渲染成 JSON / CSV。 |
| `api="name"` | `<apis>/<api name="name">` 或 `<tools>/<tool name="name">` | 每次请求调一次 API，把响应渲染进 prompt。`tool="name"` 是等价别名写法。 |
| `action="name"` | `<actions>/<action name="name">` | Java 类（实现 `IAction.execute`）生成内容。 |

可选 prompt 属性：

- `prefix="..."` — 在内容前固定拼接的文本（常见 `"城市数据如下："`）。
- `description="..."` — 中文描述，仅给阅读者看，不发给 LLM。
- `dataType="json"` — 告诉渲染器把 SQL 结果按 JSON 输出。
- `dataGroupField="SER_NAME"` — 把 SQL 结果 JSON 数组按指定字段重组成 `{field: [rows]}` 形式。
- `showInChat="false"` — 在聊天 UI 中隐藏该 prompt 文本，但仍发给 LLM（用于合成预提示）。
- `apisCheck="true"`（别名 `toolsCheck="true"`）— 该 prompt 是**函数调用指令**：LLM 只能输出 `[{"tool": "name", "args": {...}}]` 或 `[{"tool": "none"}]`，不得输出其他文字。框架随后按 `<api>/<tool name="...">` 解析工具，把结果以系统消息回灌。典型用法：路由到 `getparametersapi`、`exportapi`、`createenqjnyapi`、`checkparamsapi` 等。**工具清单无需手写**：框架会把本 mode 所有带 CDATA 调用说明的 `<tool>` 说明自动附加到该 prompt 后面（见下文 `<tools>`）。

### 函数调用 prompt 模板

```xml
<prompt name="checkUsingXxx" role="user" apisCheck="true"><![CDATA[
你是一个智能助手，能够根据用户需求调用工具回答问题。

任务：根据用户输入，以 JSON 格式输出工具调用指令：
[{"tool": "工具名", "args": {"参数名": "参数值"}}] 或 [{"tool": "none"}]

严格规则：
1. 仅输出 JSON 工具调用指令，绝对不要输出任何其他文字。
2. 调用 xxxapi 时 args.xxx 必须传用户输入的完整原句，不得改写/截取。
]]></prompt>
```

「可用工具」清单**不需要手写**：只要各 `<tool>` 写了 CDATA 调用说明，框架会自动在该 prompt 后附加「可用工具：」及全部说明（旧配置中手写的清单仍然有效，只是不再必要）。

对应的 `<api>` 节点需要 `refRequest="true"`，框架自动把 `request_id` 拼到 URL 用于后端关联。

## `<apis>` — 外部 HTTP 工具

```xml
<apis>
    <api name="getparametersapi"
         description="从用户输入中提取城市/国家、人数和日期参数。"
         url="@EWA.HOST_BASE/back_admin/ai/get_parameters.jsp?p_request_id=@request_id&amp;ai_provider=@ai_provider&amp;ai_model=@ai_model"
         refRequest="true" timeout="30000" method="post">
        <form>
            <field name="q" value="@text" />
        </form>
    </api>
</apis>
```

| 属性 | 作用 |
|------|------|
| `name` | 工具名，被 `apisCheck` prompt 或 `api="..."` 引用。 |
| `url` | 端点 URL。支持占位符：`@EWA.HOST_BASE`、`@request_id`、`@ai_provider`、`@ai_model`、`@text`、`@camp_id`、`@camp_name`、`@export_type`、`@ref_grp_id`、`@enq_id`、`@city_ids` 等（任意 `@` 前缀的请求参数）。 |
| `parameters` | 内联 URL query 参数（`a=@x&amp;b=@y`）。 |
| `method` | `get`、`post`、`put`、`delete` 或 `patch`。默认 `get`。 |
| `refRequest="true"` | 自动附加当前 `request_id` 用于后端关联。 |
| `key` | 覆盖 API key header（极少用）。 |
| `timeout` | 毫秒。默认 5000。 |
| `<form><field>` | POST/PUT 等表单字段，`@` 前缀值取自请求参数。与 `<body>` 互斥，优先使用 `<body>`。 |
| `<body>` | 请求体 CDATA 内容，用于 POST/PUT/DELETE/PATCH 的 JSON 或自定义 body。与 `<form>` 互斥；当 `method` 为 POST 及以上且 `body` 非空时，框架以 body 模式发送请求。支持 `@` 占位符。 |
| `<headers><header>` | 自定义 HTTP header。 |

`@EWA.HOST_BASE` 是宿主 EWA 应用的运行期 base URL。后端 JSP 通常挂在 `/back_admin/ai/` 下。

## `<tools>` — 工具定义（URL 调用 / 本地程序）

`<tools>/<tool>` 是 `<apis>/<api>` 的增强别名写法，全部属性（`name`/`url`/`method`/`parameters`/`refRequest`/`key`/`timeout`/`form`/`body`/`headers`）与 `<api>` 相同，并额外支持：

```xml
<tools>
    <!-- URL 调用工具：与 <api> 等价，CDATA 是该工具的调用说明 -->
    <tool name="weatherapi" description="天气"
          url="@EWA.HOST_BASE/back_admin/apis/weather.jsp"
          parameters="location=@location&amp;from_date=@from_date&amp;to_date=@to_date"
          refRequest="true" timeout="5000" method="get">
        <![CDATA[weatherapi(location: str, from_date: YYYY-MM-DD, to_date: YYYY-MM-DD):查询指定城市、日期的天气]]>
    </tool>

    <!-- 本地程序工具：command 非空时执行本地程序而非 URL 调用 -->
    <tool name="translate" command="/opt/bin/trans --text @text --lang @lang" timeout="10000">
        <![CDATA[translate(text: str, lang: str):把文本翻译为指定语言]]>
    </tool>
</tools>
```

规则：

- **CDATA 调用说明（usage）**：构建 `apisCheck`/`toolsCheck` prompt 时，框架自动把本 mode 所有带说明的工具说明逐条附加到 prompt 末尾（带「可用工具：」标题），无需再在 prompt CDATA 里手写工具清单。
- **`command`**：本地程序命令模板，`@占位符` 由 LLM 给出的 args（经 RequestValue）替换。不经 shell，直接按参数数组启动进程（支持引号包裹含空格的参数），`timeout`（毫秒）到期强制结束；stdout/stderr 合并后作为工具结果，输出超过 100K 字符截断。`command` 为空时走 `url` 调用。**安全提示**：command 由 LLM 的 args 参与替换，务必把参数限制在占位符层面，不要让 LLM 输入直接拼成完整命令；不要把无保护的 mode 暴露给不可信用户。
- **同名覆盖**：同一 mode 内 `<tool>` 与 `<api>` 同名（忽略大小写）时 tool 整体覆盖 api；`<common>` 块内规则相同，合并进 mode 时仍是 mode 本地定义优先。
- prompt 引用属性 `tool="name"` 等价于 `api="name"`，`toolsCheck="true"` 等价于 `apisCheck="true"`；两者同时存在时旧属性优先。

### `<common><apis>` / `<common><tools>` — 跨 mode 共享的公共定义

`<modes>` 根下可放一个 `<common>` 块，定义所有 mode 共享的 `<api>` / `<tool>`：

```xml
<modes>
    <mode name="A">...</mode>
    <mode name="B">...</mode>
    <common>
        <tools>
            <tool name="weatherapi" url="..." method="get" refRequest="true">
                <![CDATA[weatherapi(location: str, ...):查询天气]]>
            </tool>
        </tools>
    </common>
</modes>
```

合并规则：mode 读取 apis 时先取本 mode 下的 `<apis>`/`<tools>`，再合并 common 中的定义；名称一致（忽略大小写）时 common 中的定义被抛弃，即 **mode 本地定义优先**；common 内部同名时 tool 覆盖 api。典型用法：多个 mode 都要调 `weatherapi`，但各自需要不同的 `getGroupInfo`（内部视角 / 客人视角）——把 `weatherapi` 放 common，把 `getGroupInfo` 分别放各 mode 里。

### `request_id` 必须是 UUID 格式

`request_id` 是整个对话链路的唯一标识符，由 `emp-script-ai` 的 `AiChatParamsManager` 通过 `java.util.UUID.randomUUID().toString()` 生成，并原样写入 `AI_CHAT` 表的 `AI_UID` 列。所有引用 `@request_id` 的下游链路（`<api url>` 占位符、`AI_CHAT_UID` 外键关联、`AI_CHAT_PARAMS.AIP_VAL` 反查）都依赖该值在数据库内是唯一且可索引的。

**硬性约束**：

- 格式：`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`（8-4-4-4-12，36 字符，含 4 个连字符，全小写十六进制）。
- 生成方式：调用方（前端 / 后端 JSP / 单元测试）必须用 `java.util.UUID.randomUUID().toString()`、`crypto.randomUUID()`（Node/Web）或等价标准库生成；**禁止**自造时间戳、`Math.random()`、`Long.toHexString(System.currentTimeMillis())` 等任何非标准 UUID 的伪 ID。
- 大小写：建议全小写（与框架生成一致），但大小写不敏感；DB 列若为 `varchar` 不区分大小写，若为带大小写敏感排序规则的 `nvarchar` 则必须保持调用方与生成方一致。
- 不要复用：每个 chat / 每次新对话都应生成新 UUID；把同一个 `request_id` 复用到第二个 step 会让 `AI_CHAT_UID` 外键串数据。
- 占位符 `@request_id` 出现在 `<api url>` / `<api parameters>` / `<sql>` 体里都会被替换为该 UUID 字符串；下游 JSP / Servlet 收到 URL 后再做解析。

错误用法举例（**禁止**）：

```xml
<!-- 错误：自定义前缀 + 时间戳，破坏了 AI_UID 的唯一性假设 -->
url="@EWA.HOST_BASE/...?p_request_id=req_@timestamp_@userid"

<!-- 错误：nanoId / snowflake，虽然唯一但格式不是 UUID，下游 SQL like 'uuid%' 等隐式约束会失效 -->
url="@EWA.HOST_BASE/...?p_request_id=@nanoid"

<!-- 正确：直接传 @request_id，由调用方保证注入的是 UUID -->
url="@EWA.HOST_BASE/back_admin/ai/get_parameters.jsp?p_request_id=@request_id"
```

## 运行时占位符（`@EWA.*` 与请求参数）

框架在解析 Mode XML 时，会把所有 `@xxx` 形式的占位符替换为运行期值（定义见 `emp-script` 框架的 `RequestValue` 常量）。**两类占位符**：

### 系统内置 `@EWA.*` 占位符

由框架在每次请求时填入，与业务无关。常用于让 LLM「知道当前日期 / 主机地址 / 路径」：

| 占位符 | 含义 | 示例值 |
|--------|------|--------|
| `@EWA.DATE.YEAR` | 当前年 | `2026` |
| `@EWA.DATE.MONTH` | 当前月（1-12） | `6` |
| `@EWA.DATE.DAY` | 当前日 | `22` |
| `@EWA.DATE.HOUR` | 当前小时（24h） | `14` |
| `@EWA.DATE.MINUTE` | 当前分钟 | `37` |
| `@EWA.DATE.SECOND` | 当前秒 | `45` |
| `@EWA.DATE.TIME` | `HH:MM:SS` | `14:37:45` |
| `@EWA.DATE.STR` | `YYYY-MM-DD` 短日期 | `2026-06-22` |
| `@EWA.DATE.STR1` / `@EWA.DATETIME.STR` | `YYYY-MM-DD HH:MM:SS` | `2026-06-22 14:37:45` |
| `@EWA.DATE.SEASON` | 当前季度 | `2` |
| `@EWA.HOST` / `@EWA.HTTP` | 站点完整 URL | `https://www.gdxsoft.com` |
| `@EWA.HOST_PROTOCOL` | 协议 | `https` |
| `@EWA.HOST_PORT` | 端口 | `443` |
| `@EWA.HOST_CONTEXT` | 项目 contextPath | `/users` |
| `@EWA.HOST_BASE` | `protocol://host:port/context` | `https://www.gdxsoft.com/users` |
| `@EWA.CP` | contextPath（不带 host） | `/users` |
| `@EWA.CPF` | 当前 servlet 路径 | `/users/userinfo.jsp` |
| `@EWA.CPF_ALL` | servlet 路径 + 查询串 | `/users/userinfo.jsp?user_id=1` |
| `@EWA_QUERY` / `@EWA_QUERY_ALL` | 查询串（不含 / 含 EWA 系统参数） | `user_id=1&type=2` |

来源：`emp-script/src/main/java/com/gdxsoft/easyweb/script/RequestValue.java:80-211`。

**典型用法**：

```xml
<!-- 在 system prompt 中告诉 LLM 当前日期，避免「明天指哪天」的歧义 -->
<prompt name="init_prompt" role="system">
    <![CDATA[你是 GYAP 旅游 AI 行程规划专家。当前日期：@EWA.DATE.STR
用户提到「明天」「下周三」时必须基于此日期换算为 YYYY-MM-DD，禁止再反问。]]>
</prompt>

<!-- 在 <api url> 中拼接宿主地址，确保部署到不同环境都正确 -->
<api name="getservicesapi" url="@EWA.HOST_BASE/back_admin/ai/get_services.jsp?request_id=@request_id" ... />

<!-- 在 <complete test="..."> 中按当前日期判断是否触发 -->
<complete test="@EWA.DATE.MONTH = 12 and @fullText like '%春节%'">...</complete>
```

### 请求参数占位符（`@xxx` 取自请求）

任何来自 URL 参数、`RequestValue`、上游 LLM 抽取的参数都可以用 `@name` 引用：

| 上下文 | 替换时机 | 典型示例 |
|--------|----------|----------|
| `<api url="...">` | 调用 API 前 | `url="@EWA.HOST_BASE/back_admin/ai/get_parameters.jsp?p_request_id=@request_id&amp;ai_provider=@ai_provider&amp;ai_model=@ai_model"` |
| `<api parameters="...">` | 同上 | `parameters="ENQ_ID=@enq_id&amp;EWA_AJAX=JSON_AI_PROMPT"` |
| `<api><form><field value="...">` | 同上 | `<field name="q" value="@text" />` |
| `<api><headers><header value="...">` | 同上 | `<header name="x-uid" value="@user_id" />` |
| `<sql>...</sql>` | 执行 SQL 前 | `where ai_id = @ai_id` |
| `<prompt><![CDATA[...]]>` | **不替换**——按字面量发给 LLM | 见下方注意 |

**注意**：占位符替换**不会发生在 prompt 的 CDATA 文本里**。如果你需要把请求参数注入到 prompt，必须借助 `<prompt sqlRef="...">` 或 `<prompt api="...">` 把动态数据作为单独的 prompt 节点提供，再由框架拼接到对话末尾。在 prompt CDATA 里写 `@xxx` 只会作为字面量发给 LLM，LLM 看到的就是 `@xxx` 这个字符串本身。

### 占位符替换的常见坑

- **`@` 前缀必须紧贴变量名**：`@ request_id`（中间有空格）不会被识别。
- **大小写敏感**：`@ewa.date.str` 不会匹配 `EWA.DATE.STR`。
- **未定义占位符** 不会被替换，原样保留 `@xxx` 字符串。LLM 收到后会看到 `@xxx` 字面量。
- **嵌套占位符**（`@@xxx` 或 `@EWA.DATE.@xxx`）不支持，只能单层。
- **`@` 出现在 JSON / SQL 字面量里**：`<![CDATA[...]]>` 中含 `@` 不替换（见上），但 URL/SQL/header 里的 `@` 仍按占位符处理。如果 URL 里要表示真正的「@」（如邮箱），请用 `%40`。

## `<sqls>` — 命名 SQL

```xml
<sqls>
    <sql name="getLastResponse" description="获取最后一次AI回复">
        <![CDATA[
        select top 1 AIM_MSG as full_text
        from AI_CHAT_MSG
        where AIM_ROLE = 'assistant' and ai_id = @ai_id
        order by aim_id desc
        ]]>
    </sql>
</sqls>
```

- `name` 被 `sqlRef`、`actionSqlRef` 或 `paramCheck` 引用。
- SQL 体里 `@param_name` 占位符在运行时绑定，值来自 URL 参数、`RequestValue`，或上游 LLM 抽取出的参数。
- 必须用 `<![CDATA[ ... ]]>` 包裹，内部 `<` / `>` 安全。
- 支持多语句、SQL Server `TOP n` 等方言特有语法；在项目配置的数据库上执行。

## `<actions>` — Java 代码动作

```xml
<actions>
    <action name="createEnqJny" description="创建询价单和行程及服务"
            class="pf2023.aiAction.ActionEnqJny" />
</actions>
```

- 当 `<step action="...">` 完成后被调用。
- 类必须实现 `com.gdxsoft.ai.export.IAction`（`execute(...)`）。
- `actionSqlRef` 先跑一条 SQL（通常取最后一条 assistant 消息），把结果（一般是 JSON 文本）传给 action，由 action 解析 JSON 后写库——这是把 LLM 输出落库的标准模式。

## `<paramChecks>` — 入参校验

```xml
<paramChecks>
    <paramCheck name="people_num" des="团员人数" type="int"
                promptRule="游客人数，不含领队；从'X人''X+Y'格式中的X等表达中提取，返回整数" />
    <paramCheck name="grp_type"   des="行程类型" type="enum" sqlRef="grp_type_options"
                sqlValueField="BAS_TAG" sqlLabelField="BAS_TAG_NAME"
                promptRule="根据行程内容判断最匹配的类型" />
    <paramCheck name="departure_date" des="出发日期" type="date"
                promptRule="从用户输入中提取出发日期（YYYY-MM-DD）" />
</paramChecks>
```

| 属性 | 作用 |
|------|------|
| `name` | 存入 `AI_CHAT_PARAMS` 的键。 |
| `des` | 中文描述。 |
| `type` | `int`、`enum`、`date`，或不填（自由文本）。 |
| `promptRule` | 当 `ChatManagerBase.saveInputParams()` 从对话上下文抽取该参数时，给 LLM 的指令。 |
| `default` | 缺失时的默认值。 |
| `options` | `type="enum"` 时的**静态**枚举值，格式 `KEY1=标签1,KEY2=标签2`。与 `sqlRef` 动态枚举互斥；若同时配置，`sqlRef` 优先（SQL 失败时回退到 `options`）。 |
| `sqlRef` + `sqlValueField` + `sqlLabelField` | `type="enum"` 时使用：从该 SQL 取候选值，强制 LLM 必须选 SQL 结果中的某项。 |

校验机制：含 `validateParams="people_num,departure_date"` 的 step 会阻塞流程，直到这些参数都齐备且合法（int 范围、enum 枚举命中、date 可解析）。缺值时会触发一个内部 step，由 LLM 调用 `saveInputParams()` 补齐。

## `<ui>` — HTML 事件

`<ui>` 块定义两条 HTML 片段，分别在新会话开始（welcome）和响应完成时（complete）下发到前端，作为 SSE 事件中的 `ui_html` 字段渲染。**前端实现参考**：`static/WebContent/js/ai/StreamChatBot.js`。

### `<welcome>` — 新会话欢迎 HTML

`<welcome>` 在页面加载时由前端 `StreamChatBot.fetchWelcome(modeName)` 调用后端 `stream.jsp?ui=welcome&mode=<mode>` 取回，包装成 SSE 消息后渲染。完整事件流：

```
前端                          后端
StreamChatBot.fetchWelcome()
  → GET /back_admin/ai/stream.jsp?ui=welcome&mode=chat
                              → 解析 mode，对应 Mode 取出 <ui><welcome>
                              → SSE 事件 {ui_html: "...", ui_type: "welcome"}
  → respJson.ui_html 注入 <div class="message ui-html ui-welcome">
                       → innerHTML = ui_html
```

前端渲染逻辑（StreamChatBot.js:458-465）：

```js
if (respJson.ui_html) {
    var uiMsg = document.createElement('div');
    uiMsg.className = 'message ui-html' + (respJson.ui_type ? ' ui-' + respJson.ui_type : '');
    uiMsg.innerHTML = respJson.ui_html;
    chatBox.appendChild(uiMsg);
}
```

`ui_type` 会被附加为 CSS class（`ui-welcome` / `ui-complete`），用于样式区分。

### welcome HTML 可以内嵌交互按钮

由于 `innerHTML` 直接注入到 DOM，HTML 片段里可以含：

- **示例卡片**：点击后调用 `StreamChatBot.sendExample(this)`，把卡片文本塞进输入框并自动发送（StreamChatBot.js:110-121）。
- **跳转链接**：普通 `<a href="..." target="_blank">` 直接可用。
- **JS 函数调用**：`<a onclick="StreamChatBot.xxx()">` 触发前端方法（如导出、刷新等）。

示例（旅游行程 mode 的真实写法）：

```xml
<welcome><![CDATA[<div class="welcome-banner">
<div class="welcome-header">
<h3>✈️ 欢迎使用 GYAP 旅游 AI 助手</h3>
<p>告诉我您的旅行计划，我可以帮您规划行程...</p>
</div>
<div class="welcome-examples">
<p class="examples-title">💡 试试这些示例：</p>
<div class="example-cards">
<div class="example-card" onclick="StreamChatBot.sendExample(this)">北京出发-美东名校参访12天游学，12月20日出发，20+2</div>
<div class="example-card" onclick="StreamChatBot.sendExample(this)">香港国际夏令营，7天，港大嘉道理中心，30+3</div>
<div class="example-card" onclick="StreamChatBot.sendExample(this)">中秋八达岭长城1日自由行，公交/地铁，3人</div>
</div>
</div>
</div>]]></welcome>
```

要点：

- `sendExample(this)` 中的 `this` 是被点击的 DOM 元素；前端会取 `card.textContent` 作为输入文本（StreamChatBot.js:114），所以示例文字必须直接写在卡片元素的文本里，不能藏在子节点。
- 多个示例卡用同一函数 `sendExample` 即可，无需为每张卡片写不同 JS。
- 注意 CDATA 里 `onclick` 的引号是合法的 XML 属性引号，不需要转义。

### `<complete test="...">` — 响应完成后追加的 HTML

`<complete test="...">` 在主响应（LLM 输出）的最后一条 assistant 消息上评估 SQL 表达式，命中时把 HTML 追加到响应末尾。`test` 是属性值（非 CDATA），内部 `<` `>` `&` 必须写成 `&lt;` `&gt;` `&amp;`。

```xml
<complete test="@fullText like '%&lt;day&gt;%'"><![CDATA[<div class="complete-banner">
<p>行程规划完成！您可以创建团或导出为以下格式：</p>
<p class="export-links">
<a href="@ewa.cp/back_admin/ai/create_enq_jny.jsp?_ui=1&amp;opengrp=1&request_id=@request_id" target="_blank">创建团</a>
<a onclick="StreamChatBot.exportItinerary('pdf')">PDF</a>
<a onclick="StreamChatBot.exportItinerary('excel')">Excel</a>
<a onclick="StreamChatBot.exportItinerary('html')">HTML</a>
<a onclick="StreamChatBot.exportItinerary('word')">Word</a>
</p>
</div>]]></complete>
```

完整的事件流：

```
主响应结束（LLM 输出 <day>...</day>...</prices> 后）
                          → 后端评估 complete.test
                          → 命中则作为 SSE 事件追加 {ui_html, ui_type: "complete"}
前端                      → 渲染为 <div class="message ui-html ui-complete">
```

`<complete>` HTML 通常用来提供「后续操作」入口：

- **导出按钮**：`StreamChatBot.exportItinerary('pdf')` 等触发 `create.jsp?request_id=<id>&export_type=<type>`（StreamChatBot.js:127-145），成功后 `window.open(url)` 下载文件。
- **创建团 / 跳转后台**：普通 `<a href="..." target="_blank">`。
- **继续调整提示**：调用 `sendExample` 或跳转到其它 mode。

### 前后端约定一览

| 后端（Mode XML） | 前端（StreamChatBot.js） |
|------------------|-------------------------|
| `<ui><welcome>` 内的 HTML | `respJson.ui_html` → 渲染成 `<div class="message ui-html ui-welcome">` |
| `<ui><complete test>` HTML | 同样渲染，CSS class 加 `ui-complete` |
| 示例卡片 `onclick="StreamChatBot.sendExample(this)"` | 取卡片文本塞输入框 + 触发发送 |
| 导出按钮 `onclick="StreamChatBot.exportItinerary('pdf')"` | GET `create.jsp?request_id=...&export_type=...`，成功后 `window.open(url)` |
| `test` 属性里引用 `@fullText` / `@request_id` | 由后端评估，前端无感 |

### 完整骨架

```xml
<ui>
    <welcome><![CDATA[... 示例卡片 + 跳转链接 ...]]></welcome>
    <complete test="@fullText like '%&lt;day&gt;%'"><![CDATA[... 创建团 / 导出按钮 ...]]></complete>
</ui>
```

## 输出自定义标签（用户语义锚点）

**这些标签不是 XML schema 元素**，schema 里没有任何关于 `<gn>` / `<day>` / `<rq>` / `<cid>` / `<enj>` / `<id>` / `<num>` / `<sersday>` / `<prices>` 的定义。它们是**项目作者在 system prompt 里约定**的语义锚点——LLM 在响应中按约定输出尖括号标签包裹关键字段，后端用 HTML 解析器（或正则）抽出来落库。

要点：

- **完全由 mode 作者决定**：每条 mode 的 system prompt 里写「请把 X 包在 `<xxx>` 标签里输出」，抽取端就认 `<xxx>`。换一条 mode 可以定义完全不同的标签集。
- **不属于固定词汇**：schema 不校验标签存在性、闭合性、嵌套关系。LLM 漏写、错写都属于业务问题，不是解析错误。
- **承载内容由 mode 决定**：可以是行程项、表格行、客户线索字段、价格条目、表格、JSON-like 块——任何能被结构化抽取的内容。
- **配套的后端 action 必须自己写**：每个 mode 想要落库，就要写一个 `IAction` 实现，负责 `Jsoup.parse(...)` → `select(...)` → 写表。schema 不会替你生成。

### 跨领域例子（同一 schema，不同 mode 各自的标签）

| 领域 / mode | 自定义标签 | 用途 |
|------------|-----------|------|
| 旅游行程（travelagent `chat` mode） | `<gn>` 项目名、`<day>` 单日、`<rq>` 日期、`<cid>` 城市 ID、`<enj>` 行程、`<id>` 服务 ID、`<num>` 数量、`<sersday>` 当日服务汇总、`<prices>` 价格表 | 抽取后写入 `GRP_MAIN` / `ENQ_MAIN` / `ENQ_JNY` / `ENQ_JNY_SER` |
| CRM 线索（pf2023 `crmLead` mode） | `<lead>` 单条线索（含 `<company>`/`<contact>`/`<phone>`/`<email>` 等嵌套字段） | 抽取后写入 CRM 线索表 |
| 机票查询（pf2023 `getairs` mode） | `<flight>` 单航班（含 `<no>`/`<dep>`/`<arr>`/`<duration>` 等） | 落库到航班字典或前端直接展示 |
| 景点 / 机场信息（pf2023 `getScenicInfo` / `getAirPortInfo` mode） | `<scenic>` / `<airport>` 顶层标签 | 落库到景点 / 机场字典 |

后端实现参考：`pf2023/aiAction/ActionEnqJny.java:43-105`（行程）、`ActionCrmLead.java`（线索），都用 jsoup 解析。

### 后端推荐用 jsoup 抽取（通用技术）

LLM 输出是带自定义标签的 Markdown / 自由文本，不是标准 XML；正则匹配脆弱。**推荐使用 jsoup** 把整段文本当 HTML 片段解析，再用 CSS 选择器取标签——与具体业务无关。

```java
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

// 1. 解析整段 LLM 输出为 Document（HTML 片段模式）
String llmOutput = "...完整的 <day>...</day> 输出...";
Document doc = Jsoup.parse(llmOutput);

// 2. 顶层标签：selectFirst 取第一个
String grpName = doc.selectFirst("gn") != null
        ? doc.selectFirst("gn").text().trim()
        : "";

// 3. 列表标签：select 取全部后遍历
Elements items = doc.select("lead");          // 例：抽 CRM 线索
for (Element item : items) {
    String company = item.selectFirst("company").text().trim();
    String phone   = item.selectFirst("phone").text().trim();
    String email   = item.selectFirst("email").text().trim();
    // ...写库
}

// 4. 嵌套结构：父标签内 selectFirst("child") 取子标签
Elements days = doc.select("day");
for (Element day : days) {
    String date    = day.selectFirst("rq").text().trim();
    Element enj    = day.selectFirst("enj");
    String content = enj != null ? enj.text().trim() : "";

    // 5. 同级成对标签：用 Elements 数组下标配对
    Elements idTags  = day.select("id");
    Elements numTags = day.select("num");
    for (int i = 0; i < idTags.size(); i++) {
        String serConnId = idTags.get(i).text();
        String num = numTags.size() > i ? numTags.get(i).text() : "1";
        // ...写服务清单行
    }
}
```

jsoup 的通用优点（与业务无关）：

- 不依赖严格的 XML 闭合顺序，LLM 即使混入 Markdown、表情、换行、注释也能正确解析。
- `select("xxx")` / `selectFirst("xxx")` 与 CSS 选择器一致，写起来直观。
- `Element.text()` 自动去掉标签内残留的 HTML 实体和多余空白。
- 对自定义标签完全透明——只要 LLM 输出了对应的尖括号标签，jsoup 就能解析。

> **不要**用 `javax.xml.parsers.DocumentBuilder` 之类的标准 XML 解析器：LLM 输出常含未转义的 `<`、`>`、HTML 实体、表情符号、注释等，会触发 `SAXException`；用 jsoup 的 HTML 解析模式可以容忍。

### 给 LLM 立规则的通用原则（具体规则随 mode 而定）

每个 mode 的 system prompt 都要给 LLM 立「自定义标签输出规则」，下面几条是跨领域通用的：

1. **必填锚点要写明**：哪些字段如果缺失就视为生成失败（例如行程 mode 的 `<rq>` 日期、CRM mode 的 `<phone>`），让 LLM 在缺数据时直接报错而不是跳过。
2. **ID 类标签必须字面存在于上游数据**：禁止编造。如服务 ID 必须来自提供的服务清单，景点 ID 必须来自提供的景点字典。
3. **数量、金额配对字段**：`<id>` 与 `<num>`、`<item>` 与 `<price>` 等成对出现的标签，提示 LLM 它们必须严格对齐。
4. **完整性**：列表型标签（`<day>`、`<lead>`、`<flight>`）必须一次输出全部条目，不允许中途停止。
5. **不暴露内部字段**：所有 ID、原始 JSON、数据库字段名都应包裹在标签里，标签外的中文叙述里只展示人类可读名称。
6. **结束标记**：列表结束后追加一个 `<prices>` / `<summary>` / `<total>` 之类的「收尾锚点」，让后端可以判定响应完整。

具体到某条 mode 时，把以上通用原则翻译成该 mode 的业务规则，写进对应的 system prompt。

## 撰写清单

新增或修改 mode 时：

1. 设定 `<mode>` 的 `temperature`（抽取类 `0.1`，对话类 `0.7`）；需要 JSON 输出时在 `<mode>` 上设 `responseFormat="json_object"`（注意：step 级别不支持覆盖该属性）。
2. 第一个 step 通常是 `<step validateParams="..." stream="false" innerCall="true">`，用来在主流程前把参数补齐。
3. 主 step 用 `stream="true"`，包含：
   - 一段 `<prompt role="system">` 设定人设 + 全部规则（占 prompt 主体）。
   - 一个 `apisCheck="true"` prompt，列出 LLM 可调的工具。
   - 每个动态数据源一条 `<prompt role="user" api="..." 或 sqlRef="...">`。
   - 一条 `<prompt role="user" prefix="...">` 描述输出格式（XML 自定义标签）。
4. 可选的末 step：`stream="false" action="..." actionSqlRef="getLastResponse"`，用来落库。
5. 所有 `api="..."` / `sqlRef="..."` / `action="..."` 都要在对应块里声明。
6. 在 step `validateParams` 中引用的每个参数，都要在 `<paramChecks>` 里定义。
7. 配 `<ui><welcome>`，必要时加 `<ui><complete test="...">`。
8. `mvn -DskipTests compile` 仅校验 Java 编译，**XML 解析在运行时进行**，写完务必在单元测试里调用 `Modes.loadModes(xml)` 自检。

## 常见坑

- **CDATA 不能写在属性里** — 含 `<` `>` 的内容必须放元素体（`<![CDATA[...]]>`），不能放属性。
- **`@request_id` 拼写** — 后端 JSP 有的用 `p_request_id`（带 `p_` 前缀），有的用 `@request_id`；与 JSP 一一对齐。
- **Step 顺序** — 由文档顺序决定，与 `name` 无关。`step1` 校验会先于 `step` 生成执行。
- **`apisCheck` 提示混入散文** — LLM 可能忽略「仅输出 JSON」的规则。system prompt 里也要强调，工具列表尽量短。
- **`thinking="true"` + `responseFormat="json_object"`** — 部分 provider 的 reasoning token 会破坏 JSON 解析，严格抽取 step 务必 `thinking="false"`。
- **`responseFormat` 只能设在 `<mode>` 上** — `<step>` 不支持 `responseFormat` 属性，写了也会被忽略。需要 JSON 输出的场景（如参数抽取 step），请在 `<mode>` 级别设置。
- **`<id>` 只出现在 `<sersday>` 而未出现在 `<enj>`** — 价格表无法绑定数量；每个付费服务必须至少挂一条 `<enj>` 日程行。
- **POST `<api>` 没写 `<form>` 也没写 `<body>`** — 请求体为空。query 风格的 POST 用 `parameters="..."`；JSON body 用 `<body><![CDATA[...]]></body>`。
- **`maxHistoryTokensK` 过低** — 多轮中途截断，LLM 丢上下文，关注 WARN 日志。
- **`<prompt>` 内容里写 `@xxx`** — `@` 前缀的占位符仅在 `<sql>` / `<api url>` / `<api parameters>` 中会替换；写在 prompt 的 `<![CDATA[...]]>` 文本里只是普通字符。需要引用请求参数请用 prompt 模板或运行时注入。
- **`request_id` 不是 UUID** — `AI_CHAT.AI_UID` 列与所有外键（`AI_CHAT_MSG.AI_ID`、`AI_CHAT_PARAMS.AI_ID`）隐含假设 `request_id` 是标准 UUID。调用方必须用 `UUID.randomUUID().toString()` 生成，框架内部已遵循此约定（`AiChatParamsManager.java:278`）。任何自造 ID 都会导致 `AI_UID` 唯一索引冲突或多会话串数据。详见上文「`request_id` 必须是 UUID 格式」一节。
- **用标准 XML 解析器抽自定义标签** — LLM 输出不是标准 XML（混入 Markdown、未转义字符、表情、HTML 实体），`DocumentBuilder` / `SAXParser` 会抛异常。统一用 jsoup HTML 模式解析，详见上文「后端推荐用 jsoup 抽取」。