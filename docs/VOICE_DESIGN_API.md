# AI 声音设计 API

`com.gdxsoft.ai.voicedesign` 提供声音设计（Voice Design）能力：仅通过自然语言声音描述（`voice_prompt`）即可创建定制化音色，无需音频样本。适合快速原型验证、创意内容生产、游戏角色配音等场景。

## 依赖

- DashScope API Key：通过 `DASHSCOPE_API_KEY` 环境变量或应用配置注入
- 支持的模型系列：
  - **Qwen-TTS**：model=`qwen-voice-design`（默认），端点 `https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization`
  - **CosyVoice / Qwen-Audio-TTS**：model=`voice-enrollment`，端点 `https://{workspaceId}.{region}.maas.aliyuncs.com/api/v1/services/audio/tts/customization`（需要 workspaceId）

## 基本流程

声音设计的基本流程为：描述 → 创建 → 使用。

```java
VoiceDesignResponse r = VoiceDesignClient.of("qwen_voice_design")
        .setApiKey(System.getenv("DASHSCOPE_API_KEY"))
        .create("沉稳的中年男性播音员，音色低沉浑厚，富有磁性，语速平稳，吐字清晰，适合用于新闻播报。");

System.out.println("音色 ID: " + r.getVoiceId());
r.savePreview(Path.of("preview.wav")); // 建议试听确认效果后再使用
```

返回的 `voice_id` 可用于后续语音合成（`com.gdxsoft.ai.tts`）：

```java
TtsResponse audio = TtsClient.of("qwen_tts")
        .setApiKey(System.getenv("DASHSCOPE_API_KEY"))
        .synthesize("今天天气怎么样？",
                new TtsOptions().withVoice(r.getVoiceId()).withModel("qwen3-tts-vd-2026-01-26"));
audio.save(Path.of("output.wav"));
```

> 声音设计、语音合成要使用相同的模型（target_model）。

## 创建音色

### 基础用法

```java
VoiceDesignClient client = VoiceDesignClient.of("qwen_voice_design")
        .setApiKey(System.getenv("DASHSCOPE_API_KEY"));

VoiceDesignResponse r = client.create("年轻活泼的女性声音，语速较快，带有明显的上扬语调，适合介绍时尚产品。");
```

### 带可选参数

```java
VoiceDesignOptions options = new VoiceDesignOptions()
        .setTargetModel("qwen3-tts-vd-2026-01-26") // 目标合成模型
        .setPrefix("custom_voice")                 // 音色名前缀
        .setSampleRate(24000)                      // 预览采样率
        .setResponseFormat("wav");                 // 预览编码格式

VoiceDesignRequest request = new VoiceDesignRequest(
        "温柔知性的女性，30 岁左右，语调平和，适合有声书朗读", options)
        .setPreviewText("欢迎收听本期的有声书。");

VoiceDesignResponse r = client.create(request);
```

### CosyVoice 系列

CosyVoice 系列声音设计需要设置 `workspaceId`（百炼业务空间 ID），走北京 maas 端点：

```java
VoiceDesignClient client = VoiceDesignClient.of("qwen_voice_design")
        .setApiKey(System.getenv("DASHSCOPE_API_KEY"))
        .setConfig("model", "voice-enrollment")          // CosyVoice 声音设计模型
        .setConfig("targetModel", "cosyvoice-v3.5-plus") // 目标合成模型
        .setConfig("workspaceId", "your-workspace-id")
        .setConfig("region", "cn-beijing");

VoiceDesignResponse r = client.create(
        new VoiceDesignRequest("沉稳的中年男性播音员，音色低沉浑厚，富有磁性。",
                new VoiceDesignOptions().setPrefix("announcer"))
                .setPreviewText("各位听众朋友，大家好。"));
```

## 管理自定义音色

```java
QwenVoiceDesignProvider provider = (QwenVoiceDesignProvider) client.getProvider();

// 查询单个音色详情（CosyVoice 系列）
VoiceDesignResponse detail = provider.query(voiceId);

// 列出音色（pageIndex 从 0 开始）
VoiceDesignResponse list = provider.list("custom_voice", 0, 20);

// 删除音色
VoiceDesignResponse deleted = provider.delete(voiceId);
```

## 命令行工具

```bash
java -cp target/emp-script-ai-last.jar:<deps> com.gdxsoft.ai.console.VoiceDesignCli \
    --api-key "$DASHSCOPE_API_KEY" \
    "沉稳的中年男性播音员，音色低沉浑厚，富有磁性" \
    "各位听众朋友，大家好。"
```

输出 `voice_id` 并保存预览音频到 `voice-design-preview.wav`。

## 编写声音描述

声音描述（`voice_prompt`）直接决定生成音色的效果，描述越清晰具体，生成结果越符合预期。

- **长度限制**：CosyVoice ≤500 字符，Qwen-TTS ≤2048 字符；仅支持中文和英文。
- **核心原则**：具体而非模糊、多维而非单一、客观而非主观、原创而非模仿、简洁而非冗余。

建议组合以下维度描述声音：

| 维度 | 描述示例 |
| --- | --- |
| 性别 | 男性、女性、中性 |
| 年龄 | 儿童、青少年、青年、中年、老年 |
| 音调 | 高音、中音、低音、偏高、偏低 |
| 语速 | 快速、中速、缓慢、偏快、偏慢 |
| 情感 | 开朗、沉稳、温柔、严肃、活泼、冷静、治愈 |
| 特点 | 有磁性、清脆、沙哑、圆润、甜美、浑厚、有力 |
| 用途 | 新闻播报、广告配音、有声书、动画角色、语音助手、纪录片解说 |

### 示例

- 标准播音风格：吐字清晰精准，字正腔圆
- 年轻活泼的女性声音，语速较快，带有明显的上扬语调，适合介绍时尚产品
- 沉稳的中年男性，语速缓慢，音色低沉有磁性，适合朗读新闻或纪录片解说
- 温柔知性的女性，30 岁左右，语调平和，适合有声书朗读
- 可爱的儿童声音，大约 8 岁女孩，说话略带稚气，适合动画角色配音

## 配额与计费

- **音色总数限制**：CosyVoice 与 Qwen-TTS 分别最多 1000 个自定义音色（两类配额独立）。
- **自动清理**：单个音色过去 1 年未被使用会被系统自动删除。
- **计费**：CosyVoice 创建音色免费；Qwen-TTS 按 0.2 元/个计费，创建失败不计费（北京地域开通后 90 天内可享 10 次免费创建）。

## 说明

- 相同描述文本设计的音色可能存在差异，建议多次生成后试听择优使用。
- 声音设计与声音复刻（`com.gdxsoft.ai.voiceclone`）的区别：前者通过文本描述从零创建音色，后者基于真实音频样本复制音色。
