# AI 音乐创作 API

`com.gdxsoft.ai.music` 提供歌词生成、歌曲生成与翻唱前处理能力，当前内置 MiniMax Music Provider。

## 依赖

- MiniMax API Key：通过 `MINIMAX_API_KEY` 环境变量或应用配置注入
- 模型：默认 `music-3.0`，可切换 `music-3.0-free`、`music-2.6`、`music-cover` 等

## 创作日志

调用 `chatLogger(...)` 后，歌词生成、翻唱前处理和音乐生成都会写入 `AI_CHAT` / `AI_CHAT_MSG`，并记录一条脱敏后的 curl 消息：

```java
MusicClient client = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .chatLogger("test_hsqldb"); // 或 chatLogger(rv, dbConfigName)
```

日志内容包括：用户提示词与参数、curl 请求、成功结果或失败原因。hex 音频不会整段入库，仅记录长度，避免日志膨胀。

## 生成歌曲

```java
MusicOptions options = new MusicOptions()
        .lyrics("[Verse]\n街灯微亮晚风轻抚\n[Chorus]\n熟悉的角落")
        .sampleRate(44100)
        .bitrate(256000)
        .format("mp3");

Path output = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .generateToFile("独立民谣,忧郁,咖啡馆", options, Path.of("song.mp3"));
```

## 自动歌词与纯音乐

```java
// 根据风格描述自动生成歌词
MusicOptions autoLyrics = new MusicOptions().lyricsOptimizer(true);

// 纯音乐：无人声，prompt 必填，lyrics 可省略
MusicOptions instrumental = new MusicOptions().instrumental(true);
```

### 一句话完整创作

```java
MusicComposition composition = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .compose("一首关于夏日海边的轻快情歌", new MusicOptions().format("mp3"));

System.out.println(composition.getLyrics().getSongTitle());
System.out.println(composition.getLyrics().getLyrics());
composition.getMusic().save(Path.of("song.mp3"));
```

`compose` 会先调用歌词生成接口，再用返回的 `style_tags` 作为音乐 prompt、返回歌词作为 lyrics 调用音乐生成接口。

### 歌词生成与编辑

```java
MusicLyricsResponse lyrics = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .generateLyrics(MusicLyricsRequest.writeFullSong("一首关于夏日海边的轻快情歌"));

MusicLyricsResponse edited = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .generateLyrics(MusicLyricsRequest.edit("让副歌更明亮", lyrics.getLyrics()));
```

## 翻唱

```java
MusicOptions cover = new MusicOptions()
        .model("music-cover")
        .audioUrl("https://example.com/reference.mp3");

MusicResponse response = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"))
        .generate("流行摇滚，明亮有力", cover);
```

`audioUrl`、`audioBase64` 与 `coverFeatureId` 三者必须且只能提供一个。

### 两步翻唱

```java
MusicClient client = MusicClient.of("minimax_music")
        .apiKey(System.getenv("MINIMAX_API_KEY"));

MusicCoverPreprocessResponse preprocessed = client.preprocessCover(
        MusicCoverPreprocessRequest.audioUrl("https://example.com/reference.mp3"));

String revisedLyrics = preprocessed.getFormattedLyrics(); // 可按需修改
MusicResponse response = client.generate("流行摇滚，保持原曲情绪", new MusicOptions()
        .model("music-cover")
        .coverFeatureId(preprocessed.getCoverFeatureId())
        .lyrics(revisedLyrics));
```

预处理返回的 `coverFeatureId` 有效期 24 小时；两步流程可在生成前修改 ASR 提取出的格式化歌词。

## 输出

默认 `outputFormat=hex`，`MusicResponse.save(Path)` 会把 hex 解码后写入音频文件。URL 输出可通过 `outputFormat("url")` 开启；URL 有效期 24 小时，应及时下载。当前 Java Provider 使用非流式同步调用，`stream(true)` 会被拒绝。

## Provider 扩展

1. 在 `com.gdxsoft.ai.music` 实现 `IMusicProvider`
2. 在 `MusicProviderType` 添加类型
3. 在 `MusicProviderFactory` 注册实现
