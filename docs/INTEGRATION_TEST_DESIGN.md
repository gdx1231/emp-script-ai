# 集成测试设计说明

## 目标

验证完整 AI 聊天流程：SSE 流式输出 → 前端收到事件 → AI 回答落库 → 多轮对话历史正确加载。
使用真实 AI API + HSQLDB 内存数据库，不依赖外部数据库或 Mock 服务。

## 架构

```
┌─────────────────────────────────────────┐
│           IntegrationTest               │
│                                         │
│  @BeforeAll                             │
│    ├─ 启动 HSQLDB 内存数据库            │
│    ├─ 建 6 张表                         │
│    └─ 从 test_ai_settings.json 插入配置  │
│                                         │
│  testSseStream()                        │
│    ├─ AiStreamOrPost.init(rv, "test", w)│
│    ├─ processRequest()                  │
│    ├─ 断言: SSE 输出非空                │
│    └─ 断言: AI_CHAT_MSG 有记录          │
│                                         │
│  testMultiRound()                       │
│    ├─ 第1轮: prompt="我叫小明"           │
│    ├─ 第2轮: prompt="我叫什么名字?"      │
│    └─ 断言: AI 能回忆上下文             │
│                                         │
│  @AfterAll                              │
│    └─ 关闭 HSQLDB                       │
└─────────────────────────────────────────┘
```

## 文件清单

| 文件 | 说明 |
|------|------|
| `src/test/resources/test_ai_settings.json` | provider 配置模板（API URL、Key、Model） |
| `pom.xml` | 添加 HSQLDB 测试依赖 |
| `src/test/java/com/gdxsoft/ai/test/TestDatabase.java` | HSQLDB 初始化、建表、插数据 |
| `src/test/java/com/gdxsoft/ai/test/IntegrationTest.java` | JUnit 5 集成测试 |

## TestDatabase.java 职责

1. **启动 HSQLDB**：`jdbc:hsqldb:mem:testdb`，用户名 `sa`，密码空
2. **建表**（HSQLDB 语法，IDENTITY 自增）：
   - `AI_CHAT` — 聊天会话（AI_ID 自增）
   - `AI_CHAT_MSG` — 消息记录（AIM_ID 自增）
   - `AI_CHAT_PARAMS` — 参数存储（AIP_ID 自增）
   - `AI_PROVIDER` — 提供商
   - `AI_PROVIDER_MODEL` — 模型
   - `AI_PROVIDER_URL` — API 地址和密钥
3. **读取配置**：加载 `test_ai_settings.json`，API Key 优先从环境变量读（如 `QWEN_API_KEY`），fallback 到 `ai_settings.json` 中的值
4. **插入数据**：为每个配置的 provider 插入 AI_PROVIDER、AI_PROVIDER_MODEL、AI_PROVIDER_URL 三行
5. **注册数据源**：将 HSQLDB 连接注册到 emp-script 的 DataConnection 体系中，`dbConfigName = "test"`
6. **提供查询工具方法**：按 AI_ID 查询消息记录数、内容等，供测试断言用

## IntegrationTest.java 测试用例

### testSseStream — SSE 流式 + 落库验证

```
输入: ai_provider=qwen, mode=test_mode, prompt="你好，请用一句话回答"
流程:
  1. 构造 RequestValue(rv)，设置所有必要参数
  2. new AiStreamOrPost().init(rv, "test", stringWriter)
  3. processRequest()
  4. 捕获 stringWriter 输出
断言:
  - stringWriter 中包含 "data:" 前缀的 SSE 事件
  - AI_CHAT_MSG 表中有 role=user 和 role=assistant 的记录
  - assistant 记录的 AIM_MSG 非空
```

### testMultiRound — 多轮对话

```
第1轮:
  输入: prompt="我的名字是小明，请记住"
  断言: AI_CHAT_MSG 有 user+assistant 各一条

第2轮:
  输入: 同一 request_id, prompt="我叫什么名字？"
  断言:
    - AI_CHAT_MSG 共有 4 条记录（2轮 × 2角色）
    - 第2轮 assistant 回复中包含 "小明"
```

### testDeepSeekProvider — DeepSeek 也走通

```
与 testSseStream 相同流程，但 ai_provider=deepseek
验证 OpenAiRequestAI 修复对 DeepSeek 同样生效
```

## 配置加载优先级

```
test_ai_settings.json 中 api_key 为空?
  → 尝试读取环境变量 (QWEN_API_KEY / DEEPSEEK_API_KEY)
  → 尝试从 ai_settings.json 读取同名 key
  → 都没有则跳过该 provider 的测试
```

## 运行方式

```bash
# 方式1: 环境变量传 Key
export QWEN_API_KEY=sk-xxx
mvn test -Dtest=IntegrationTest

# 方式2: 先在 ai_settings.json 中填好 Key（已有机制）
mvn test -Dtest=IntegrationTest
```

## 关键设计决策

1. **不 Mock AI API** — 直接访问真实服务，测试真实的 SSE 解析和落库
2. **HSQLDB 替代 SQL Server** — 内存数据库，无需外部依赖，测试完自动销毁
3. **每个测试方法独立 session** — 通过不同 request_id 隔离，避免互相干扰
4. **Skip 而非 Fail** — 如果某个 provider 没有配置 Key，打印 SKIP 而不是报错
