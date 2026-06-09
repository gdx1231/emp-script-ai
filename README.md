# emp-script-ai

统一调用多家大模型服务商的 Java 工具库与示例，聚焦"统一请求构建 + 流式输出（SSE）+ XML 场景模式（Mode）解析 + Markdown 代码块提取"。

## 功能特性
- 统一请求体构建器：内置通义千问（Qwen，OpenAI 兼容接口）请求构建 `RequestData`
- 流式输出（SSE）Servlet 示例：`StreamServlet`
- 场景模式（Mode）XML 解析：从 XML 解析 Step、Prompt、SqlQuery，支持 step 的 `stream` 与 `actionSqlRef` 属性
- **多轮对话历史控制**：`ChatManagerBase` 支持配置最大历史消息条数和 token 上限，自动截断旧消息
- XML 解析性能优化：`Modes` 内置 xmlContent 的 MD5 缓存，相同内容不重复解析（线程安全）
- Markdown 代码块提取：`StringUtils.extractCodeBlocks`
- Java 17 + Maven，轻依赖、易扩展

## 适配与扩展
- 已接入：Qwen（通义千问）Chat Completions 兼容模式
- 已提供：Google Gemini 的请求/响应模型类（便于后续接入）
- 扩展指南：在 `com.gdxsoft.ai.providers` 下新增服务商的请求/响应模型与调用逻辑

## 安装与构建
```bash
# 克隆
git clone https://github.com/gdx1231/emp-script-ai.git
cd emp-script-ai

# 构建（跳过测试）
mvn -DskipTests package
```
构建产物在 `target/`（含 jar、sources、javadoc）。

如使用依赖（示例，版本以实际发布为准）：
```xml
<dependency>
  <groupId>com.gdxsoft.easyweb</groupId>
  <artifactId>emp-script-ai</artifactId>
  <version>1.0.0</version>
  </dependency>
```

## 快速开始
### 1) 使用模式（Mode）XML 解析
通过 `com.gdxsoft.ai.modes.Modes` 解析 XML，获取 `Mode`、`Step`、`Prompt`、`SqlQuery`：

示例 XML（含 step 的 `stream` 与 `actionSqlRef` 属性）：
```xml
<modes>
  <mode name="demo" description="演示">
    <step name="init" description="初始化" stream="true" action="createEnqJny" actionSqlRef="create_enq_jny">
      <prompts>
        <prompt name="system" role="system"><![CDATA[你是专业的Java助手]]></prompt>
        <prompt name="user" role="user">请用一句话介绍本项目</prompt>
      </prompts>
    </step>
    <sqls>
      <sql name="citys" description="城市数据"><![CDATA[SELECT * FROM CITY]]></sql>
      <sql name="create_enq_jny" description="创建行程SQL"><![CDATA[/* your sql */]]></sql>
    </sqls>
  </mode>
 </modes>
```

解析与访问：
```java
import com.gdxsoft.ai.modes.*;
import java.util.*;

String xml = "...上述XML...";
Modes modes = new Modes(xml);            // 会计算 xml 的 MD5，用于缓存
List<Mode> list = modes.loadModes();     // 若 MD5 未变化，则复用上次解析结果

Mode mode = list.get(0);
for (Step s : mode.getSteps()) {
    System.out.println(s.getName() + ", stream=" + s.isStream() + ", actionSqlRef=" + s.getActionSqlRef());
    for (Prompt p : s.getPrompts()) {
        System.out.println(p.getRole() + ": " + p.getContent());
    }
}
```

关键点：
- `Step.stream`：是否启用流式，默认 true；可通过 `<step stream="false">` 关闭
- `Step.actionSqlRef`：动作相关 SQL 的引用名（可选），用于后续执行的 SQL 片段关联
- 解析实现：`Mode.parseMode(Element)` 已迁移至 `Mode` 类中，`Modes` 负责组织与缓存
- MD5 缓存：`Modes` 会缓存上一次解析的 `List<Mode>`，当新传入的 xmlContent MD5 未变化时，直接返回缓存结果；变更后自动重新解析

### 2) 控制多轮对话历史（防 Token 膨胀）

在 `<mode>` 元素上配置历史限制属性，`ChatManagerBase.appendPreviousMessages()` 会自动生效：

```xml
<mode name="chat" description="通用聊天"
      maxHistoryMessages="20"    <!-- 最多传递最近 20 条消息 -->
      maxHistoryTokensK="80">    <!-- 历史消息 token 上限 80K -->
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxHistoryMessages` | int | 30 | 最多传递的历史消息条数 |
| `maxHistoryTokensK` | int | 100 | 历史消息 token 上限（单位：K），100 = 100K tokens |

**工作机制**：
1. 按 `maxHistoryMessages` 取最近 N 条消息（使用 DTTable 分页，兼容所有数据库）
2. 估算总 token 数（CJK 字符 / 2 + 其他字符 / 4）
3. 如果超过 `maxHistoryTokensK * 1000`，从最早的消息开始删除，直到低于限制
4. 截断时会在日志中输出 WARN 信息

**Java API**（如通过代码设置）：
```java
mode.setMaxHistoryMessages(20);
mode.setMaxHistoryTokensK(80);  // 80K tokens
```

### 3) 构建通义千问（Qwen）请求
```java
import com.gdxsoft.ai.providers.qwen.request.RequestData;

RequestData req = new RequestData()
        .model("qwen-plus")
        .stream(true)                // 启用流式
        .systemMessage("你是一个AI助手，回答用户的问题。")
        .userMessage("你好，帮我写一段欢迎词")
        .temperature(0.7)
        .topP(0.9);

String jsonPayload = req.buildJson();
// 使用 HttpClient/OkHttp 调用对应的 OpenAI 兼容接口
```

### 4) 流式输出（SSE）Servlet
`com.gdxsoft.ai.servlets.StreamServlet` 演示将大模型响应以 SSE 推送给前端。

web.xml 映射：
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

前端（EventSource）：
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

提示：API Key 请用环境变量或配置中心注入，避免硬编码；生产环境注意日志脱敏。

### 5) Markdown 代码块提取
```java
import com.gdxsoft.ai.StringUtils;
import java.util.*;

String md = "```java\nSystem.out.println(\"hi\");\n```";
List<Map<String, String>> blocks = StringUtils.extractCodeBlocks(md);
// blocks.get(0).get("language") == "java"
// blocks.get(0).get("code") == "System.out.println(\"hi\");"
```

## 进阶说明
- MD5 缓存策略：`Modes` 以 xmlContent 的 MD5 作为键缓存“最近一次解析结果”。同一进程内，若多次传入相同 xmlContent，将直接复用缓存；内容变更后自动重新解析并刷新缓存。
- 线程安全：`Modes.loadModes()` 在重建缓存时使用同步与双检，避免并发重复解析。
- 强制刷新：目前通过“修改 xmlContent”触发刷新（无显式清缓存 API）。

## 运行测试
```bash
mvn -q -DskipTests=false test
```

## 许可证
本项目使用 MIT License，详见 `LICENSE`。
