# 视频创建工作流 — 实现计划（v2）

> 独立工作流管理进程 · workflow.json 配置驱动 · 最大并发限制 · 数据库表设计
>
> 流程：文本 → 创建图片素材（人物/环境） → 创建分镜（≤15s） → 创建分镜视频 → 合成视频

---

## 一、现有能力盘点

| 能力 | 包路径 | 状态 | 工作流角色 |
|------|--------|------|-----------|
| LLM 对话 / Mode XML 多步 | `com.gdxsoft.ai.modes` + `ChatManagerBase` | ✅ | 分镜拆解 |
| 图像生成（批量并发） | `com.gdxsoft.ai.img` | ✅ | 素材生成 |
| 视频生成（异步 submit/poll） | `com.gdxsoft.ai.video` (`VideoTaskRunner`) | ✅ | 分镜视频生成 |
| 尾帧返回续接 | `VideoOptions.returnLastFrame()` / `VideoResponse.getLastFrameUrl()` | ✅ | 分镜间画面连续 |
| TTS 语音合成 | `com.gdxsoft.ai.tts` | ✅ | 旁白/台词配音（可选） |
| 独立进程 + CLI | `switchproxy/` (`SwitchServer` / `SwitchCli`) | ✅ | 进程模式参考 |
| DB 操作 | `ChatManagerDb` (`@param` SQL / `DataConnection` / `DTTable`) | ✅ | 状态持久化 |
| 视频合成（ffmpeg） | — | ❌ | 最终合成（新建） |

---

## 二、整体架构

### 2.1 独立进程架构

```
                ┌──────────────────────────────────┐
  Web/Chat ──── │  提交工作流 (INSERT AI_WF_INSTANCE) │
  用户请求      │  状态=pendin                      │
                └──────────┬───────────────────────┘
                           │ DB 轮询
                ┌──────────▼───────────────────────┐
                │     WorkflowEngine (独立进程)      │
                │                                    │
                │  ┌──────────────────────────────┐ │
                │  │  调度器 Scheduler              │ │
                │  │  - 轮询 pending 实例           │ │
                │  │  - 全局并发信号量              │ │
                │  │  - 按 priority 优先级调度      │ │
                │  └──────────┬───────────────────┘ │
                │             │                      │
                │  ┌──────────▼───────────────────┐ │
                │  │  执行器 Executor (虚拟线程)    │ │
                │  │                                │ │
                │  │  Phase1: planning   (LLM)     │ │
                │  │  Phase2: materials  (Img)     │ │
                │  │  Phase3: generating (Video)   │ │
                │  │  Phase4: compositing(ffmpeg)  │ │
                │  └──────────┬───────────────────┘ │
                │             │                      │
                │  ┌──────────▼───────────────────┐ │
                │  │  DB 状态更新 (每个 task)       │ │
                │  │  AI_WF_INSTANCE / AI_WF_TASK  │ │
                │  └──────────────────────────────┘ │
                └──────────────────────────────────┘
```

**核心设计**：
- **DB 驱动**：Web 层只负责往 DB 插入 `pending` 实例，引擎进程轮询 DB 取任务执行
- **进程隔离**：引擎是独立 JVM 进程（参考 `SwitchCli` 模式），崩溃不影响 Web 服务
- **可恢复**：所有状态在 DB，引擎重启后从断点继续（扫描 `running` 的 task 重新执行或标记失败重试）
- **HTTP 状态查询**：引擎内嵌轻量 HTTP 端点（参考 `SwitchServer`），供前端轮询进度

### 2.2 并发限制模型

```
全局限制 (workflow.json → limits)
├── maxConcurrentWorkflows: 3      ← 同时执行的工作流实例数
├── maxConcurrentImageGen: 3       ← 同时并发的图片生成请求数
├── maxConcurrentVideoGen: 1       ← 同时并发的视频生成请求数(尾帧续接需串行)
├── maxRetries: 2                  ← 单 task 最大重试次数
└── retryDelayMs: 5000             ← 重试间隔

引擎内部信号量：
  Semaphore workflowSemaphore  = new Semaphore(maxConcurrentWorkflows)
  Semaphore imageSemaphore     = new Semaphore(maxConcurrentImageGen)
  Semaphore videoSemaphore     = new Semaphore(maxConcurrentVideoGen)
```

---

## 三、workflow.json 配置设计

### 3.1 完整结构

```json
{
  "name": "text-to-video",
  "description": "文本创建视频工作流",
  "version": "1.0",

  "limits": {
    "maxConcurrentWorkflows": 3,
    "maxConcurrentImageGen": 3,
    "maxConcurrentVideoGen": 1,
    "maxRetries": 2,
    "retryDelayMs": 5000,
    "taskTimeoutMs": 600000
  },

  "output": {
    "baseDir": "/data/video-workflow",
    "dbConfigName": "default",
    "ffmpegPath": "ffmpeg"
  },

  "phases": [
    {
      "name": "planning",
      "type": "llm_storyboard",
      "provider": "qwen",
      "model": "qwen-plus",
      "promptTemplate": "你是视频导演。根据故事输出分镜JSON：{title,characters:[{name,description,imgPrompt}],environments:[{name,description,imgPrompt}],shots:[{index,description,videoPrompt,duration,cameraMovement,characterRefs,environmentRef,dialogue,narration}]}。约束：duration≤15。故事：@{input}",
      "responseFormat": "json_object"
    },
    {
      "name": "materials",
      "type": "image_generation",
      "dependsOn": "planning",
      "provider": "doubao_img",
      "size": "1024x1024",
      "concurrency": 3
    },
    {
      "name": "generating",
      "type": "video_generation",
      "dependsOn": "materials",
      "provider": "doubao",
      "model": "doubao-seedance-2-0-260128",
      "chainShots": true,
      "returnLastFrame": true,
      "maxDuration": 15,
      "defaultAspectRatio": "16:9",
      "defaultResolution": "720p",
      "generateAudio": true,
      "concurrency": 1
    },
    {
      "name": "compositing",
      "type": "video_compose",
      "dependsOn": "generating",
      "ffmpeg": {
        "resolution": "1280x720",
        "fps": 30,
        "videoCodec": "libx264",
        "audioCodec": "aac",
        "transitions": false,
        "bgmPath": null,
        "bgmVolume": 0.3
      }
    }
  ]
}
```

### 3.2 Phase 类型枚举

| type | 说明 | 依赖 |
|------|------|------|
| `llm_storyboard` | 调用 LLM 拆分分镜，输出 Storyboard JSON | — |
| `image_generation` | 批量生成人物/环境图片 | planning |
| `video_generation` | 逐分镜生成视频，支持尾帧续接 | materials |
| `video_compose` | ffmpeg 拼接合成最终视频 | generating |
| `tts` | TTS 语音合成（可选，在 compose 前混入） | planning |

### 3.3 配置加载

```
WorkflowConfig.load(Path jsonFile) → WorkflowConfig
```

- 从 JSON 文件加载，缓存到 `AI_WF_DEF` 表（MD5 去重，参考 `Modes` 的 xmlContent 缓存模式）
- 支持多份 workflow.json（不同工作流定义）

---

## 四、数据库表设计

> 遵循现有约定：`AI_` 前缀、`*_ID` 自增主键、`*_STATUS` 状态字段、`*_CDATE/*_MDATE` 时间戳、
> `ADM_ID/USR_ID/SUP_ID` 用户归属、`@param` 占位符 SQL、`isnull()` / `top N`（SQL Server 兼容）

### 4.1 ER 关系总览

```
AI_WF_DEF (工作流定义)
  │  1
  │
  │  N
AI_WF_INSTANCE (工作流实例)
  │  1
  │
  │  N
AI_WF_TASK (任务) ──── AI_WF_ASSET (资产)
```

### 4.2 AI_WF_DEF — 工作流定义

> 从 workflow.json 加载，MD5 去重缓存

```sql
CREATE TABLE AI_WF_DEF (
    WFD_ID          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    WFD_NAME        VARCHAR(100)  NOT NULL,          -- 工作流名称 (对应 json.name)
    WFD_DESC        VARCHAR(500),                    -- 描述
    WFD_VERSION     VARCHAR(20),                     -- 版本
    WFD_JSON        CLOB,                            -- 完整 workflow.json 内容
    WFD_MD5         VARCHAR(32),                     -- JSON 内容 MD5（去重用）
    WFD_STATUS      VARCHAR(20)  DEFAULT 'USED',     -- USED / DISABLED
    WFD_CDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    WFD_MDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

### 4.3 AI_WF_INSTANCE — 工作流实例

> 每次用户提交一个视频创建请求 = 一条实例记录

```sql
CREATE TABLE AI_WF_INSTANCE (
    WFI_ID          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    WFD_ID          BIGINT       NOT NULL,           -- FK → AI_WF_DEF
    WFI_UID         VARCHAR(100) NOT NULL,           -- 唯一标识 (UUID)
    WFI_STATUS      VARCHAR(20)  DEFAULT 'pending',  -- pending/planning/materials/
                                                     -- generating/compositing/
                                                     -- done/failed/cancelled
    WFI_PRIORITY    INT          DEFAULT 5,          -- 优先级 (1最高, 9最低)
    WFI_INPUT       CLOB,                            -- 用户输入的故事文本
    WFI_STORYBOARD  CLOB,                            -- 分镜 JSON (Phase1 输出)
    WFI_RESULT      CLOB,                            -- 最终结果 JSON (含视频URL)
    WFI_FINAL_URL   VARCHAR(500),                    -- 最终视频 URL
    WFI_ERROR       CLOB,                            -- 失败原因
    WFI_PROGRESS    INT          DEFAULT 0,          -- 进度 0-100
    WFI_CUR_PHASE   VARCHAR(50),                     -- 当前执行阶段名
    WFI_RETRY_COUNT INT          DEFAULT 0,          -- 工作流级重试次数

    -- 用户归属 (与 AI_CHAT 一致)
    ADM_ID          INT,
    USR_ID          INT,
    SUP_ID          INT,

    WFI_CDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    WFI_MDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    WFI_SDATE       TIMESTAMP,                       -- 开始执行时间
    WFI_EDATE       TIMESTAMP,                       -- 完成时间
    WFI_ENGINE_ID   VARCHAR(100)                     -- 执行该实例的引擎进程标识
);
```

**状态流转**：
```
pending → planning → materials → generating → compositing → done
    │         │          │            │            │
    └─────────┴──────────┴────────────┴────────────┴──→ failed
                                                          ↓
                                                       cancelled
```

### 4.4 AI_WF_TASK — 任务

> 一个工作流实例下的每个具体任务（每张图、每个分镜视频、合成）

```sql
CREATE TABLE AI_WF_TASK (
    WFT_ID          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    WFI_ID          BIGINT       NOT NULL,           -- FK → AI_WF_INSTANCE
    WFT_TYPE        VARCHAR(50)  NOT NULL,           -- planning / material_img /
                                                     -- shot_video / video_compose / tts
    WFT_NAME        VARCHAR(200),                    -- 任务名称 (如 "人物图:小明" / "分镜3视频")
    WFT_SEQ         INT,                             -- 同类型任务内的序号 (0,1,2...)
    WFT_PHASE       VARCHAR(50),                     -- 所属阶段名

    WFT_STATUS      VARCHAR(20)  DEFAULT 'pending',  -- pending/running/succeeded/failed/skipped
    WFT_DEP_ID      BIGINT,                          -- 依赖的前置任务 ID (尾帧续接: shot N 依赖 shot N-1)

    WFT_INPUT       CLOB,                            -- 任务输入 JSON (prompt/params)
    WFT_OUTPUT      CLOB,                            -- 任务输出 JSON (url/metadata)

    WFT_PROVIDER    VARCHAR(50),                     -- 供应商标识
    WFT_MODEL       VARCHAR(100),                    -- 模型标识
    WFT_REMOTE_ID   VARCHAR(200),                    -- 远端异步任务 ID (视频生成的 taskId)
    WFT_REMOTE_STATUS VARCHAR(50),                   -- 远端任务状态 (processing/succeeded/failed)

    WFT_ERROR       CLOB,                            -- 失败原因
    WFT_RETRY_COUNT INT          DEFAULT 0,          -- 已重试次数

    WFT_CDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    WFT_MDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    WFT_SDATE       TIMESTAMP,                       -- 开始执行时间
    WFT_EDATE       TIMESTAMP,                       -- 完成时间

    CONSTRAINT FK_WFT_INSTANCE FOREIGN KEY (WFI_ID) REFERENCES AI_WF_INSTANCE(WFI_ID)
);
```

**关键字段说明**：
- `WFT_DEP_ID`：任务依赖。尾帧续接时，shot N 的 `WFT_DEP_ID` = shot N-1 的 `WFT_ID`，引擎确保 N-1 完成后才开始 N
- `WFT_REMOTE_ID`：视频生成异步任务 ID（`VideoTaskSubmit.getTaskId()`），引擎可轮询远端状态
- `WFT_REMOTE_STATUS`：远端 polling 状态，便于引擎崩溃恢复后判断是否需重新轮询

### 4.5 AI_WF_ASSET — 资产

> 生成的所有素材资产（图片、视频、音频、尾帧图）

```sql
CREATE TABLE AI_WF_ASSET (
    WFA_ID          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    WFI_ID          BIGINT       NOT NULL,           -- FK → AI_WF_INSTANCE
    WFT_ID          BIGINT,                          -- FK → AI_WF_TASK (生成该资产的任务)
    WFA_TYPE        VARCHAR(50)  NOT NULL,           -- character_img / environment_img /
                                                     -- shot_video / last_frame /
                                                     -- final_video / tts_audio
    WFA_REF_TYPE    VARCHAR(50),                     -- 引用类型: character/environment/shot
    WFA_REF_NAME    VARCHAR(200),                    -- 引用名 (如 "小明" / "教室" / "shot_2")
    WFA_NAME        VARCHAR(200),                    -- 资产名称
    WFA_URL         VARCHAR(1000),                   -- 远端 URL
    WFA_LOCAL_PATH  VARCHAR(1000),                   -- 本地文件路径
    WFA_METADATA    CLOB,                            -- 元数据 JSON (resolution/duration/size 等)
    WFA_CDATE       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
```

### 4.6 索引

```sql
CREATE INDEX IDX_WFI_STATUS   ON AI_WF_INSTANCE(WFI_STATUS, WFI_PRIORITY, WFI_CDATE);
CREATE INDEX IDX_WFI_UID      ON AI_WF_INSTANCE(WFI_UID);
CREATE INDEX IDX_WFT_INSTANCE ON AI_WF_TASK(WFI_ID, WFT_TYPE, WFT_SEQ);
CREATE INDEX IDX_WFT_STATUS   ON AI_WF_TASK(WFT_STATUS);
CREATE INDEX IDX_WFT_REMOTE   ON AI_WF_TASK(WFT_REMOTE_ID);
CREATE INDEX IDX_WFA_INSTANCE ON AI_WF_ASSET(WFI_ID, WFA_TYPE);
```

---

## 五、模块设计

### 5.1 包结构

```
src/main/java/com/gdxsoft/ai/video/workflow/
├── engine/
│   ├── WorkflowEngine.java          # 独立进程主类 (参考 SwitchServer)
│   ├── WorkflowCli.java             # CLI 入口 (参考 SwitchCli)
│   ├── WorkflowScheduler.java       # 调度器：轮询 DB + 信号量限流
│   ├── WorkflowExecutor.java        # 执行器：虚拟线程执行单个工作流
│   └── EngineStatusServer.java      # 内嵌 HTTP 状态查询 (参考 StatusHandler)
│
├── config/
│   ├── WorkflowConfig.java          # workflow.json 加载与解析
│   ├── WorkflowDef.java             # 工作流定义 (phases/limits/output)
│   ├── PhaseDef.java                # 单个阶段定义
│   └── LimitsConfig.java            # 并发限制配置
│
├── model/
│   ├── Storyboard.java              # 分镜脚本数据模型
│   ├── StoryboardParser.java        # LLM 输出 → Storyboard 解析+校验
│   ├── MaterialAsset.java           # 素材资产 (对应 AI_WF_ASSET)
│   ├── ShotVideo.java               # 分镜视频结果
│   └── WorkflowResult.java          # 工作流最终结果
│
├── phase/
│   ├── IPhaseHandler.java           # 阶段处理器接口
│   ├── PlanningPhase.java           # Phase1: LLM 分镜拆解
│   ├── MaterialsPhase.java          # Phase2: 批量图片素材生成
│   ├── GeneratingPhase.java         # Phase3: 分镜视频生成+尾帧续接
│   ├── CompositingPhase.java        # Phase4: ffmpeg 视频合成
│   └── TtsPhase.java                # 可选: TTS 语音合成
│
├── db/
│   └── WorkflowDb.java              # DB 操作 (参考 ChatManagerDb)
│
├── compose/
│   ├── VideoCompositor.java         # ffmpeg 拼接
│   └── ComposeOptions.java          # 合成选项
│
└── WorkflowSubmitter.java           # Web 层调用：提交工作流到 DB
```

### 5.2 核心类设计

#### WorkflowEngine（独立进程主类）

```java
/**
 * 工作流引擎独立进程。
 * 参考 SwitchServer 模式，通过 CLI 启动，轮询 DB 执行工作流。
 */
public class WorkflowEngine {
    private final WorkflowConfig config;       // workflow.json 配置
    private final WorkflowDb db;               // DB 操作
    private final WorkflowScheduler scheduler; // 调度器

    // 全局并发信号量（从 config.limits 初始化）
    private final Semaphore workflowSemaphore;
    private final Semaphore imageSemaphore;
    private final Semaphore videoSemaphore;

    private final ExecutorService executor;    // 虚拟线程池
    private volatile boolean running = true;
    private final String engineId;             // 引擎实例标识 (hostname+pid)

    public void start() {
        // 1. 加载 workflow.json → 注册到 AI_WF_DEF
        // 2. 启动调度轮询线程
        // 3. 启动 HTTP 状态服务 (可选)
        // 4. 注册 shutdown hook
        // 5. 阻塞主线程
    }

    public void stop() {
        running = false;
        executor.shutdown();
    }
}
```

#### WorkflowScheduler（调度器）

```java
/**
 * 轮询 DB 取 pending 工作流，按并发限制调度执行。
 */
public class WorkflowScheduler implements Runnable {
    @Override
    public void run() {
        while (running) {
            try {
                // 1. 尝试获取全局工作流信号量
                if (workflowSemaphore.tryAcquire(0, TimeUnit.SECONDS)) {
                    // 2. 查询 pending 工作流 (按 priority, cdate 排序)
                    //    SQL: SELECT TOP 1 * FROM AI_WF_INSTANCE
                    //         WHERE WFI_STATUS='pending'
                    //         ORDER BY WFI_PRIORITY, WFI_CDATE
                    Long instanceId = db.pollPendingInstance(engineId);
                    if (instanceId != null) {
                        // 3. 标记为 running，提交到虚拟线程执行
                        executor.submit(() -> executeWorkflow(instanceId));
                    } else {
                        workflowSemaphore.release(); // 没有任务，释放
                        Thread.sleep(pollIntervalMs); // 等待
                    }
                } else {
                    Thread.sleep(pollIntervalMs);
                }
            } catch (Exception e) {
                LOG.error("调度异常", e);
            }
        }
    }
}
```

#### WorkflowExecutor（执行器）

```java
/**
 * 执行单个工作流实例的四个阶段。
 * 每个阶段完成后更新 AI_WF_INSTANCE.WFI_STATUS 和 AI_WF_TASK 记录。
 */
public class WorkflowExecutor {

    public void execute(long instanceId) {
        // 加载实例 + 配置
        WorkflowConfig cfg = loadConfig(instanceId);
        Storyboard storyboard = null;
        List<MaterialAsset> materials = null;
        List<ShotVideo> shots = null;

        try {
            // Phase 1: planning
            updateInstanceStatus(instanceId, "planning");
            storyboard = runPlanning(instanceId, cfg);
            db.saveStoryboard(instanceId, storyboard);

            // Phase 2: materials
            updateInstanceStatus(instanceId, "materials");
            materials = runMaterials(instanceId, cfg, storyboard, imageSemaphore);

            // Phase 3: generating
            updateInstanceStatus(instanceId, "generating");
            shots = runGenerating(instanceId, cfg, storyboard, materials, videoSemaphore);

            // Phase 4: compositing
            updateInstanceStatus(instanceId, "compositing");
            String finalUrl = runCompositing(instanceId, cfg, shots);

            // Done
            db.updateInstanceResult(instanceId, finalUrl, "done");
            db.updateInstanceProgress(instanceId, 100);

        } catch (Exception e) {
            db.updateInstanceError(instanceId, e);
        } finally {
            workflowSemaphore.release();
        }
    }
}
```

#### IPhaseHandler（阶段处理器接口）

```java
/**
 * 阶段处理器接口，每个 Phase 实现此接口。
 * 由 PhaseDef.type 决定使用哪个实现（工厂模式）。
 */
public interface IPhaseHandler {
    /**
     * 执行该阶段。
     * @param ctx  工作流执行上下文（含 instanceId, config, storyboard, 已生成资产等）
     * @return 阶段输出（更新到 ctx）
     */
    void execute(WorkflowContext ctx) throws Exception;
}
```

#### PlanningPhase（Phase1: LLM 分镜拆解）

```java
public class PlanningPhase implements IPhaseHandler {
    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        // 1. 创建 AI_WF_TASK (type=planning, status=running)
        // 2. 用 ChatManagerBase / IRequestAI 调用 LLM
        //    - prompt 来自 PhaseDef.promptTemplate，替换 @{input}
        //    - responseFormat = json_object
        // 3. StoryboardParser.parse(llmOutput) → Storyboard
        // 4. 校验：每个 shot duration ≤ 15, 角色引用存在
        // 5. 保存 storyboard 到 AI_WF_INSTANCE.WFI_STORYBOARD
        // 6. 更新 task status=succeeded, output=storyboard JSON
        ctx.setStoryboard(storyboard);
    }
}
```

#### MaterialsPhase（Phase2: 批量图片生成）

```java
public class MaterialsPhase implements IPhaseHandler {
    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        Storyboard sb = ctx.getStoryboard();
        List<ImgRequest> requests = new ArrayList<>();

        // 为每个 character + environment 创建图片生成请求
        for (Character c : sb.getCharacters()) {
            requests.add(new ImgRequest(new ImgOptions(c.getImgPrompt())
                .size(ctx.getPhase().getSize()).n(1).responseFormat("url")));
        }
        for (Environment e : sb.getEnvironments()) {
            requests.add(new ImgRequest(new ImgOptions(e.getImgPrompt())
                .size(ctx.getPhase().getSize()).n(1).responseFormat("url")));
        }

        // 并发生成（信号量限流），复用 ImgConcurrency 模式
        // 每个请求对应一个 AI_WF_TASK (type=material_img)
        // 生成结果存入 AI_WF_ASSET (type=character_img/environment_img)
        // 回填 imageUrl 到 Storyboard

        ctx.setMaterials(materials);
    }
}
```

#### GeneratingPhase（Phase3: 分镜视频生成 + 尾帧续接）

```java
public class GeneratingPhase implements IPhaseHandler {
    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        Storyboard sb = ctx.getStoryboard();
        boolean chain = ctx.getPhase().isChainShots();
        String lastFrameUrl = null;

        for (int i = 0; i < sb.getShots().size(); i++) {
            Shot shot = sb.getShots().get(i);

            // 创建 AI_WF_TASK (type=shot_video, seq=i)
            // 若 chain 且 i>0，设置 WFT_DEP_ID = 前一个 task 的 ID

            VideoOptions opts = new VideoOptions(shot.getVideoPrompt())
                .duration(Math.min(shot.getDuration(), maxDuration))
                .aspectRatio(shot.getAspectRatio())
                .returnLastFrame(chain);  // 续接模式需要尾帧

            if (chain && i == 0) {
                // 首镜：用人物图 + 环境图作参考
                opts.addRefImageUrl(getCharUrl(sb, shot));
                opts.addRefImageUrl(getEnvUrl(sb, shot));
            } else if (chain && lastFrameUrl != null) {
                // 后续镜：用前一镜尾帧作首帧约束
                opts.addRefImageUrl(lastFrameUrl);
            } else {
                // 非续接模式：各自独立用素材图
                opts.addRefImageUrl(getCharUrl(sb, shot));
                opts.addRefImageUrl(getEnvUrl(sb, shot));
            }

            if (shot.getDialogue() != null) {
                opts.generateAudio(true);
            }

            // 非阻塞提交
            VideoTaskRunner runner = new VideoTaskRunner(provider, logger);
            VideoTaskSubmit submit = runner.submit(new VideoRequest(opts));
            db.updateTaskRemoteId(taskId, submit.getTaskId());

            // 轮询完成
            VideoTaskStatus status;
            do {
                Thread.sleep(pollDelayMs);
                status = runner.poll(submit.getTaskId(), opts);
                db.updateTaskRemoteStatus(taskId, status.getStatus());
            } while (status.isProcessing());

            if (status.isFailed()) throw new IOException(status.getError());

            // 保存视频资产 + 尾帧资产
            db.saveAsset(instanceId, taskId, "shot_video", videoUrl);
            if (chain && status.getResponse().getLastFrameUrl() != null) {
                lastFrameUrl = status.getResponse().getLastFrameUrl();
                db.saveAsset(instanceId, taskId, "last_frame", lastFrameUrl);
            }
        }
    }
}
```

#### CompositingPhase（Phase4: ffmpeg 合成）

```java
public class CompositingPhase implements IPhaseHandler {
    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        List<ShotVideo> shots = ctx.getShotVideos();

        // 1. 下载所有分镜视频到本地
        // 2. 统一转码 (分辨率/帧率/编码)
        // 3. concat demuxer 拼接
        // 4. 可选: 混入 BGM / 烧录字幕
        // 5. 保存最终视频 → AI_WF_ASSET (type=final_video)
        //    更新 AI_WF_INSTANCE.WFI_FINAL_URL
    }
}
```

#### WorkflowDb（DB 操作）

```java
/**
 * 工作流 DB 操作类。
 * 参考 ChatManagerDb 的 @param SQL + DataConnection / DTTable 模式。
 */
public class WorkflowDb {
    private final RequestValue rv;
    private final String dbConfigName;

    // === AI_WF_INSTANCE ===

    /** 提交工作流（Web 层调用） */
    public long submitInstance(String workflowName, String input,
                               int priority, RequestValue rv);

    /** 轮询 pending 实例（调度器调用，原子更新为 running） */
    public Long pollPendingInstance(String engineId);

    /** 更新实例状态 */
    public void updateInstanceStatus(long instanceId, String status);

    /** 更新实例进度 */
    public void updateInstanceProgress(long instanceId, int progress);

    /** 更新实例结果 */
    public void updateInstanceResult(long instanceId, String finalUrl, String status);

    /** 更新实例错误 */
    public void updateInstanceError(long instanceId, Throwable error);

    /** 查询实例（状态查询） */
    public JSONObject queryInstance(String uid);

    // === AI_WF_TASK ===

    /** 创建任务 */
    public long createTask(long instanceId, String type, String name,
                           int seq, String phase, String inputJson);

    /** 更新任务状态 */
    public void updateTaskStatus(long taskId, String status, String outputJson);

    /** 更新远端任务 ID */
    public void updateTaskRemoteId(long taskId, String remoteId);

    /** 查询需恢复的任务（引擎重启后） */
    public List<JSONObject> queryRecoverableTasks(long instanceId);

    // === AI_WF_ASSET ===

    /** 保存资产 */
    public long saveAsset(long instanceId, long taskId, String type,
                          String refType, String refName,
                          String url, String localPath, String metadata);
}
```

#### WorkflowSubmitter（Web 层入口）

```java
/**
 * Web 层调用入口：提交工作流到 DB，立即返回。
 * 引擎进程会异步拾取执行。
 */
public class WorkflowSubmitter {
    /**
     * 提交视频创建工作流。
     * @return 工作流实例 UID（用于后续状态轮询）
     */
    public String submit(String workflowName, String storyText,
                         RequestValue rv, String dbConfigName) {
        WorkflowDb db = new WorkflowDb(rv, dbConfigName);
        String uid = UUID.randomUUID().toString();
        long instanceId = db.submitInstance(workflowName, storyText, 5, rv);
        return uid;
    }
}
```

#### WorkflowCli（CLI 入口）

```java
/**
 * CLI 入口，参考 SwitchCli 模式。
 *
 * 用法：
 *   java -cp emp-script-ai.jar com.gdxsoft.ai.video.workflow.engine.WorkflowCli start
 *   java ... submit --workflow text-to-video --input "故事文本"
 *   java ... status --uid <uuid>
 *   java ... list --status pending
 *   java ... cancel --uid <uuid>
 */
public class WorkflowCli {
    public static void main(String[] args) {
        String command = args[0];
        switch (command) {
            case "start"  -> cmdStart(args);     // 启动引擎进程
            case "submit" -> cmdSubmit(args);    // 提交工作流
            case "status" -> cmdStatus(args);    // 查询状态
            case "list"   -> cmdList(args);      // 列出实例
            case "cancel" -> cmdCancel(args);    // 取消实例
        }
    }
}
```

---

## 六、引擎恢复机制

引擎进程崩溃重启后，需要恢复中断的工作流：

```
启动时扫描：
  1. AI_WF_INSTANCE WHERE WFI_STATUS IN ('planning','materials','generating','compositing')
     AND WFI_ENGINE_ID = (崩溃前的 engineId)
     → 重新标记为 pending（从头重跑当前 phase）

  2. AI_WF_TASK WHERE WFT_STATUS = 'running'
     → 标记为 failed（需重试或跳过）

  3. AI_WF_TASK WHERE WFT_REMOTE_ID IS NOT NULL AND WFT_REMOTE_STATUS = 'processing'
     → 重新轮询远端视频任务状态
     → 若远端已 succeeded，直接取结果；若 failed，标记 task failed
```

---

## 七、实现里程碑

| 里程碑 | 内容 | 依赖 | 工作量 |
|--------|------|------|--------|
| **M1** | 数据库表 + WorkflowDb | 无 | 中 |
| **M2** | workflow.json 配置加载 + 数据模型 | M1 | 小 |
| **M3** | PlanningPhase（LLM 分镜） | M2 | 小 |
| **M4** | MaterialsPhase（图片生成） | M2 | 小 |
| **M5** | GeneratingPhase（视频生成+尾帧续接） | M4 | 中（核心） |
| **M6** | VideoCompositor（ffmpeg 合成） | ffmpeg | 中 |
| **M7** | WorkflowEngine + Scheduler + CLI | M3-M6 | 中 |
| **M8** | 引擎恢复机制 | M7 | 小 |
| **M9** | HTTP 状态查询端点 | M7 | 小 |
| **M10** | 可选增强：TTS / BGM / 字幕 / 转场 | M6 | 中 |

**最小闭环**：M1→M2→M3→M4→M5→M7（先用简单拼接替代 ffmpeg），再做 M6/M8。

---

## 八、与现有代码的复用

| 新建组件 | 复用的现有组件 |
|---------|---------------|
| PlanningPhase | `ChatManagerBase` / `IRequestAI` (LLM 调用) |
| MaterialsPhase | `ImgClient`, `ImgConcurrency`, `ComicImgActionExample.loadApiConfig()` |
| GeneratingPhase | `VideoTaskRunner`, `VideoTaskSubmit/Status`, `VideoOptions.returnLastFrame()`, `VideoResponse.getLastFrameUrl()` |
| CompositingPhase | `Tool.executeCommand()` 的 `ProcessBuilder` 模式 |
| WorkflowEngine | `SwitchServer` / `SwitchCli` 进程模式 |
| WorkflowConfig | `Modes` 的 MD5 缓存模式, `SwitchConfig.load()` 文件加载模式 |
| WorkflowDb | `ChatManagerDb` 的 `@param` SQL / `DataConnection` / `DTTable` 模式 |
| WorkflowSubmitter | `IAction` 接口（可作为 Mode XML action 接入） |
| 引擎 HTTP 状态 | `SwitchServer` 的 `HttpServer` + `StatusHandler` |

---

## 九、关键风险与对策

| 风险 | 对策 |
|------|------|
| 尾帧续接失败 | 降级为独立生成（chainShots=false），各 shot 用素材图作首帧 |
| ffmpeg 未安装 | 启动时 `which ffmpeg` 检测，明确报错 |
| 视频生成耗时极长 | 异步 submit/poll + DB 持久化 + 引擎恢复机制 |
| 引擎进程崩溃 | DB 状态恢复 + 远端任务重新轮询 |
| API 配额超限 | 多级信号量限流 + maxRetries 重试 |
| LLM JSON 格式不规范 | `StoryboardParser` 容忍 markdown 围栏 + 截取 |
| 多引擎竞争同一实例 | `pollPendingInstance` 原子更新 `WFI_ENGINE_ID`（乐观锁） |
