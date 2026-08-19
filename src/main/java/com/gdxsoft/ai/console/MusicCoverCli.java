package com.gdxsoft.ai.console;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.gdxsoft.ai.music.MusicClient;
import com.gdxsoft.ai.music.MusicCoverComposition;
import com.gdxsoft.ai.music.MusicCoverPreprocessRequest;
import com.gdxsoft.ai.music.MusicOptions;

/**
 * 两步翻唱直接调用程序。
 *
 * <pre>
 * 用法:
 *   MusicCoverCli [选项] <audioUrl|audioBase64> <prompt> [输出文件]
 *
 * 选项:
 *   --api-key <key>     MiniMax API Key（默认取环境变量 MINIMAX_API_KEY）
 *   --audio-file <path> 从本地文件读取音频并 Base64 后作为翻唱参考
 *   --lyrics <path>     使用指定歌词文件覆盖 ASR 识别结果
 *   --model <name>      翻唱模型（默认 music-cover）
 *   --format <fmt>      音频编码 mp3/wav/pcm（默认 mp3）
 * </pre>
 */
public final class MusicCoverCli {
    private MusicCoverCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = new HashMap<>();
        int positional = 0;
        String[] positionalArgs = new String[3];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--api-key", "--audio-file", "--lyrics", "--model", "--format" -> {
                    if (i + 1 >= args.length) usage("选项 " + arg + " 缺少参数");
                    opts.put(arg.substring(2), args[++i]);
                }
                case "-h", "--help" -> { printUsage(); return; }
                default -> {
                    if (arg.startsWith("--")) usage("未知选项: " + arg);
                    if (positional >= 3) usage("参数过多");
                    positionalArgs[positional++] = arg;
                }
            }
        }
        if (positional < 2) usage("必须提供参考音频与生成提示词");

        String apiKey = opts.getOrDefault("api-key", System.getenv("MINIMAX_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) usage("未提供 API Key（--api-key 或 MINIMAX_API_KEY）");

        String reference = positionalArgs[0];
        String prompt = positionalArgs[1];
        String output = positional >= 3 ? positionalArgs[2] : "cover-output." + opts.getOrDefault("format", "mp3");

        MusicCoverPreprocessRequest request;
        if (opts.containsKey("audio-file")) {
            byte[] bytes = Files.readAllBytes(Path.of(opts.get("audio-file")));
            request = MusicCoverPreprocessRequest.audioBase64(java.util.Base64.getEncoder().encodeToString(bytes));
        } else if (reference.startsWith("http://") || reference.startsWith("https://")) {
            request = MusicCoverPreprocessRequest.audioUrl(reference);
        } else {
            request = MusicCoverPreprocessRequest.audioBase64(reference);
        }

        String revisedLyrics = opts.containsKey("lyrics")
                ? Files.readString(Path.of(opts.get("lyrics")), StandardCharsets.UTF_8) : null;

        MusicClient client = MusicClient.of("minimax_music").apiKey(apiKey);
        MusicCoverComposition result = client.cover(request, prompt,
                new MusicOptions().model(opts.getOrDefault("model", "music-cover"))
                        .format(opts.getOrDefault("format", "mp3")),
                revisedLyrics);

        System.out.println("cover_feature_id: " + result.getPreprocess().getCoverFeatureId());
        System.out.println("audio_duration:   " + result.getPreprocess().getAudioDuration());
        if (result.getMusic().hasAudioBytes()) {
            result.getMusic().save(Path.of(output));
            System.out.println("saved:            " + Path.of(output).toAbsolutePath());
        } else if (result.getMusic().getAudioUrl() != null) {
            System.out.println("audio_url:        " + result.getMusic().getAudioUrl());
        } else {
            System.err.println("警告: 响应中没有音频数据");
            System.exit(1);
        }
    }

    private static void usage(String message) {
        System.err.println("错误: " + message);
        printUsage();
        System.exit(2);
    }

    private static void printUsage() {
        System.err.println("""
                用法: MusicCoverCli [选项] <audioUrl|audioBase64> <prompt> [输出文件]
                选项:
                  --api-key <key>     MiniMax API Key（默认取环境变量 MINIMAX_API_KEY）
                  --audio-file <path> 从本地文件读取音频并 Base64 后作为翻唱参考
                  --lyrics <path>     使用指定歌词文件覆盖 ASR 识别结果
                  --model <name>      翻唱模型（默认 music-cover）
                  --format <fmt>      音频编码 mp3/wav/pcm（默认 mp3）
                """);
    }
}
