# 图片创作 AI 接口文档

## 概述

`com.gdxsoft.ai.img` 提供统一的图片生成 API，封装 **6 个供应商** 的差异。无论底层是 OpenAI、豆包还是通义万相，都返回相同的 `ImgResponse`。

## 供应商

| 标识 | 供应商 | 默认模型 | API 类型 |
|------|--------|----------|:---:|
| `openai_img` | OpenAI DALL-E | `dall-e-3` | 同步 JSON |
| `openai_compat_img` | OpenAI 兼容服务 | `dall-e-3` | 同步 JSON |
| `doubao_img` | 豆包 / 火山引擎 Ark | `doubao-seedream-5-0-260128` | 同步 JSON + SSE |
| `qwen_img` | 通义万相 (DashScope) | `wanx2.1-t2i-turbo` | 仅异步 (提交任务+轮询) |
| `stability_img` | Stability AI | `stable-diffusion-xl` | Multipart |
| `grok_img` | xAI Grok | `grok-3-mini` | 同步 JSON / Chat |

## 快速开始

```java
// 1. 选择供应商
ImgClient client = ImgClient.of("doubao_img")
    .apiKey("your-api-key");

// 2. 生成图片
ImgResponse resp = client.generate(
    new ImgOptions("一只可爱的橘猫坐在窗台上，阳光洒在它身上")
        .size("2K")
        .n(1));

// 3. 获取结果
System.out.println(resp.getFirstImage().getUrl());
```

## 统一参数 `ImgOptions`

所有供应商共用同一套参数，内部自动转换为各 API 所需格式。

| 参数 | 类型 | 说明 | 适用 |
|------|------|------|------|
| `prompt` | String | 图片描述（必填） | 全部 |
| `model` | String | 模型名称 | 全部 |
| `size` | String | `1024x1024`, `2K`, `4K` 等 | 全部 |
| `n` | Integer | 生成张数，1-4 | 全部 |
| `responseFormat` | String | `url`（默认）或 `b64_json` | OpenAI / 豆包 |
| `quality` | String | `standard` / `hd` | OpenAI |
| `style` | String | `vivid` / `natural` / `<anime>` 等 | OpenAI / Qwen |
| `negativePrompt` | String | 负向提示词 | Qwen / Stability |
| `seed` | Long | 随机种子 | Qwen / Stability |
| `steps` | Integer | 扩散步数 | Stability |
| `user` | String | 终端用户标识 | OpenAI / 豆包 |

## 统一响应 `ImgResponse`

```java
ImgResponse resp = client.generate(...);

// 图片列表
List<ImgResponse.GeneratedImage> images = resp.getImages();
ImgResponse.GeneratedImage img = resp.getFirstImage();

// 图片数据
img.getUrl();       // URL
img.getB64Json();   // Base64
img.isUrl();        // 是否 URL
img.isBase64();     // 是否 Base64

// 元数据
resp.getCreated();        // 创建时间戳
resp.getRevisedPrompt();  // 模型修正后的 prompt
resp.getModel();          // 实际使用的模型
resp.getUsage();          // Token / 图片用量
```

## 各供应商详解

### 豆包 (`doubao_img`)

```java
DoubaoImgProvider doubao = new DoubaoImgProvider();
doubao.setApiKey("your-ark-key");
doubao.setWatermark(false);                     // 去水印
doubao.setSequentialImageGeneration("auto");    // 组图风格一致
doubao.setStream(true);                         // SSE 流式 (防 OOM)

ImgResponse r = ImgClient.of(doubao)
    .generate(new ImgOptions("一幅山水画").size("2K").n(4));
```

- 端点: `https://ark.cn-beijing.volces.com/api/v3/images/generations`
- Seedream 5 最小要求 3686400 像素 (≈1920×1920)
- 支持 `2K`、`4K` 预设尺寸

### 通义万相 (`qwen_img`)

```java
// 仅异步模式，自动提交任务 + 轮询等待 (最长 120s)
ImgResponse r = ImgClient.of("qwen_img")
    .apiKey("sk-...")
    .generate(new ImgOptions("一幅山水画")
        .model("wanx2.1-t2i-turbo")
        .size("1024*1024")
        .negativePrompt("模糊, 低质量, 变形")
        .seed(42L));
```

- 端点: `https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis`
- 必须异步: `X-DashScope-Async: enable` (自动)
- 流程: 提交任务 → 轮询 `/api/v1/tasks/{taskId}` → 返回图片 URL
- 尺寸用 `*` 分隔 (`x` 自动转换)
- Style: `<auto>`, `<photography>`, `<anime>`, `<oil painting>`, `<watercolor>`, `<sketch>`, `<chinese painting>`, `<flat illustration>`, `<3d cartoon>`, `<portrait>`

### OpenAI (`openai_img`)

```java
ImgResponse r = ImgClient.of("openai_img")
    .apiKey("sk-...")
    .generate(new ImgOptions("A cyberpunk city at night")
        .model("dall-e-3").size("1024x1024").quality("hd").style("vivid"));
```

### Stability AI (`stability_img`)

```java
ImgResponse r = ImgClient.of("stability_img")
    .apiKey("sk-...")
    .generate(new ImgOptions("A landscape")
        .negativePrompt("blurry").steps(30).seed(100L));
// size 自动转为 aspect_ratio，响应流式写入临时文件
```

### Grok / xAI (`grok_img`)

```java
// images 模式 (默认)
ImgResponse r = ImgClient.of("grok_img").apiKey("xai-...").generate("A cat");

// chat 模式
GrokImgProvider g = (GrokImgProvider) ImgProviderFactory.create("grok_img");
g.setApiKey("xai-...");
g.setGenerationMode("chat");
ImgResponse r2 = ImgClient.of(g).generate("一幅水墨画");
```

### OpenAI 兼容 (`openai_compat_img`)

```java
ImgResponse r = ImgClient.of("openai_compat_img")
    .apiUrl("https://my-proxy.com/v1/images/generations")
    .apiKey("sk-...").generate("A sunset");
```

## OOM 防护

```
方案 1: URL 模式 (推荐)     → responseFormat("url")
方案 2: 直接存盘             → client.generateToFiles(prompt, Path.of("/tmp"))
方案 3: 用完释放             → img.writeToFileAndRelease(path) / img.release()
方案 4: Stability 流式        → 响应直接写入临时文件
方案 5: 豆包 SSE 流式        → doubao.setStream(true)
```

## 并发控制

```java
ImgConcurrency concurrency = ImgConcurrency.of("doubao_img")
    .apiKey("...")
    .maxConcurrency(3)        // 最多 3 并发
    .maxRetries(2)             // 429 自动重试

// 并行生成
List<ImgResponse> results = concurrency.generateAll(prompts);

// 带回调
concurrency.generateAll(prompts, (i, resp, err) -> { ... });

// 直接存盘
concurrency.generateAllToFiles(prompts, Path.of("/tmp/images"));
```

## 架构图

```
ImgClient (门面) + ImgConcurrency (并发控制)
  ↓
ImgProviderFactory → 按名称创建
  ↓
IImgProvider → ImgProviderBase (线程安全)
  ↓
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│ OpenAI   │ Doubao   │ Qwen     │ Stability│ Grok     │ Compat   │
│ JSON     │ JSON+SSE │ 异步轮询  │ Multipart│ JSON/Chat│ JSON     │
└──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
  ↓               ↓              ↓              ↓              ↓
        统一返回 ImgResponse (url / b64 / writeToFile / release)
```

## 测试

```bash
# 单元测试 (无需 Key)
mvn test -pl . -Dtest="QwenImgProviderTest#buildRequestBodyShape+parseResponseShape"
mvn test -pl . -Dtest="DoubaoImgProviderTest#buildRequestBodyShape+buildRequestBodyWithStream+parseResponseShape"

# 集成测试 (需 Key)
export DOUBAO_API_KEY=xxx  && mvn test -pl . -Dtest=DoubaoImgProviderTest
export DASHSCOPE_API_KEY=xxx && mvn test -pl . -Dtest=QwenImgProviderTest
```
