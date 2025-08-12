# emp-script-ai

统一调用多家大模型服务商的 Java 工具库与示例，提供统一请求体构建、流式输出（SSE）Servlet、基于 XML 的“模式（Mode）”解析器，以及 Markdown 代码块提取工具，便于在 Web 系统中快速集成 AI 能力。

## 特性
- 统一请求体构建器：目前内置通义千问（Qwen，OpenAI 兼容接口）请求构建 `RequestData`
- 流式输出（Server-Sent Events, SSE）Servlet 示例：`StreamServlet`
- “模式（Mode）”解析：从 XML 中解析步骤（Step）、提示词（Prompt）、SQL 片段（SqlQuery）
- Markdown 代码块提取工具：`StringUtils.extractCodeBlocks`
- Java 17 + Maven，轻依赖，易扩展

## 支持/内置适配
- Qwen（通义千问）兼容模式 Chat Completions（已接入）
- Google Gemini 请求/响应模型类（已定义数据结构，便于扩展调用）

如需适配新的服务商，可在 `com.gdxsoft.ai.providers` 下扩展请求/响应模型与调用逻辑。

## 快速开始
### 引入方式
- 推荐：直接克隆本仓库并本地构建
- 若已发布到 Maven 中央仓库，可使用如下依赖（以实际发布版本为准）：

```xml
<dependency>
  <groupId>com.gdxsoft.easyweb</groupId>
  <artifactId>emp-script-ai</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 本地构建
```bash
# 克隆
git clone https://github.com/gdx1231/emp-script-ai.git
cd emp-script-ai

# 构建（跳过测试）
mvn -DskipTests package
```
构建产物位于 `target/` 目录（含源码/文档 jar）。

## 使用示例
### 1) 构建通义千问（Qwen）请求
使用 `com.gdxsoft.ai.providers.qwen.request.RequestData` 构建 OpenAI 兼容格式请求体：

```java
import com.gdxsoft.ai.providers.qwen.request.RequestData;

RequestData req = new RequestData()
        .model("qwen-plus")
        .stream(true)                // 是否启用流式
        .systemMessage("你是一个AI助手，回答用户的问题。")
        .userMessage("你好，帮我写一段欢迎词")
        .temperature(0.7)
        .topP(0.9);

String jsonPayload = req.buildJson();
// 将 jsonPayload 通过 HttpClient/OkHttp 等发送至 Qwen 兼容接口
```

### 2) 流式输出 Servlet（SSE）
`com.gdxsoft.ai.servlets.StreamServlet` 演示了如何以 SSE 方式将大模型响应流式返回给前端。

- web.xml 示例映射：
```xml
<servlet>
  <servlet-name>ai-stream</servlet-name>
  <servlet-class>com.gdxsoft.ai.servlets.StreamServlet</servlet-class>
</servlet>
<servlet-mapping>
  <servlet-name>ai-stream</servlet-name>
  <url-pattern>/ai/stream</url-pattern>
</servlet-mapping>
```

- 前端示例（EventSource）：
```html
<script>
  const es = new EventSource('/ai/stream?model=qwen&prompt=' + encodeURIComponent('给我写一个节日祝福'));
  es.onmessage = (e) => {
    if (e.data === '[DONE]') es.close();
    else console.log('chunk:', e.data);
  };
  es.onerror = () => es.close();
</script>
```

提示：示例中的 API Key 请使用安全方式注入（环境变量/配置中心），不要在代码中硬编码。

### 3) “模式（Mode）”XML 解析
通过 `com.gdxsoft.ai.modes.Modes` 解析 XML，生成包含 Step、Prompt、SqlQuery 的结构化对象，便于按场景组织多轮提示词与数据：

最小示例 XML：
```xml
<modes>
  <mode name="demo" description="演示">
    <step name="init" description="初始化">
      <prompts>
        <prompt name="system" role="system"><![CDATA[你是专业的Java助手]]></prompt>
        <prompt name="user" role="user">请用一句话介绍本项目</prompt>
      </prompts>
    </step>
    <sqls>
      <sql name="citys" description="城市数据"><![CDATA[SELECT * FROM CITY]]></sql>
    </sqls>
  </mode>
</modes>
```

解析与使用：
```java
import com.gdxsoft.ai.modes.*;
import java.util.*;

String xml = "...上述XML...";
Modes modes = new Modes(xml);
List<Mode> list = modes.loadModes();
Mode mode = list.get(0);
for (Step s : mode.getSteps()) {
    for (Prompt p : s.getPrompts()) {
        System.out.println(p.getRole() + ": " + p.getContent());
    }
}
```

### 4) Markdown 代码块提取
```java
import com.gdxsoft.ai.StringUtils;
import java.util.*;

String md = "```java\nSystem.out.println(\"hi\");\n```";
List<Map<String, String>> blocks = StringUtils.extractCodeBlocks(md);
// blocks.get(0).get("language") == "java"
// blocks.get(0).get("code") == "System.out.println(\"hi\");"
```

## 配置与安全
- 强烈建议通过环境变量或外部配置注入各云厂商的 API Key。例如：`QWEN_API_KEY`
- 生产环境请开启日志脱敏，避免在日志中打印完整请求体与密钥
- 前端使用 SSE 时注意断线重连与超时处理

## 运行测试
```bash
mvn -q -DskipTests=false test
```

## 许可证
本项目使用 MIT License，详见 `LICENSE`。
