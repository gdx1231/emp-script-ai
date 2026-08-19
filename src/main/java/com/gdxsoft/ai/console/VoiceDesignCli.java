package com.gdxsoft.ai.console;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.gdxsoft.ai.voicedesign.VoiceDesignClient;
import com.gdxsoft.ai.voicedesign.VoiceDesignOptions;
import com.gdxsoft.ai.voicedesign.VoiceDesignResponse;
import com.gdxsoft.ai.voicedesign.providers.qwen.QwenVoiceDesignProvider;

/**
 * 声音设计命令行工具。
 *
 * <pre>
 * 用法:
 *   VoiceDesignCli [选项] <voicePrompt> [预览文本]
 *
 * 选项:
 *   --api-key <key>      DashScope API Key（默认取环境变量 DASHSCOPE_API_KEY）
 *   --model <name>       声音设计模型（默认 qwen-voice-design；CosyVoice 用 voice-enrollment）
 *   --target-model <m>   目标合成模型（默认 qwen3-tts-vd-2026-01-26）
 *   --prefix <name>      音色名前缀（Qwen-TTS 默认 custom_voice）
 *   --workspace-id <id>  CosyVoice 系列必需的业务空间 ID
 *   --region <region>    CosyVoice 系列地域（默认 cn-beijing）
 *   --preview <path>     预览音频输出文件（默认 voice-design-preview.wav）
 *   --list               列出已创建的音色（可选 --prefix 过滤）
 *   --delete <voiceId>   删除指定音色
 * </pre>
 */
public final class VoiceDesignCli {
    private VoiceDesignCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = new HashMap<>();
        int positional = 0;
        String[] positionalArgs = new String[2];
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--api-key", "--model", "--target-model", "--prefix",
                        "--workspace-id", "--region", "--preview", "--delete" -> {
                    if (i + 1 >= args.length) usage("选项 " + arg + " 缺少参数");
                    opts.put(arg.substring(2), args[++i]);
                }
                case "--list" -> opts.put("list", "true");
                case "-h", "--help" -> { printUsage(); return; }
                default -> {
                    if (arg.startsWith("--")) usage("未知选项: " + arg);
                    if (positional >= 2) usage("参数过多");
                    positionalArgs[positional++] = arg;
                }
            }
        }

        String apiKey = opts.getOrDefault("api-key", System.getenv("DASHSCOPE_API_KEY"));
        if (apiKey == null || apiKey.isBlank()) usage("未提供 API Key（--api-key 或 DASHSCOPE_API_KEY）");

        VoiceDesignClient client = VoiceDesignClient.of("qwen_voice_design")
                .setApiKey(apiKey);
        if (opts.containsKey("model")) client.setConfig("model", opts.get("model"));
        if (opts.containsKey("target-model")) client.setConfig("targetModel", opts.get("target-model"));
        if (opts.containsKey("prefix")) client.setConfig("prefix", opts.get("prefix"));
        if (opts.containsKey("workspace-id")) client.setConfig("workspaceId", opts.get("workspace-id"));
        if (opts.containsKey("region")) client.setConfig("region", opts.get("region"));

        if ("true".equals(opts.get("list"))) {
            QwenVoiceDesignProvider p = (QwenVoiceDesignProvider) client.getProvider();
            VoiceDesignResponse r = p.list(opts.get("prefix"), 0, 20);
            System.out.println("list: " + r.getRaw());
            return;
        }
        if (opts.containsKey("delete")) {
            QwenVoiceDesignProvider p = (QwenVoiceDesignProvider) client.getProvider();
            VoiceDesignResponse r = p.delete(opts.get("delete"));
            System.out.println("deleted: " + r.getVoiceId());
            return;
        }

        if (positional < 1) usage("必须提供声音描述 voicePrompt");

        VoiceDesignOptions options = new VoiceDesignOptions();
        if (opts.containsKey("target-model")) options.setTargetModel(opts.get("target-model"));
        if (opts.containsKey("prefix")) options.setPrefix(opts.get("prefix"));

        String previewText = positional >= 2 ? positionalArgs[1] : null;
        VoiceDesignResponse r = client.create(
                new com.gdxsoft.ai.voicedesign.VoiceDesignRequest(positionalArgs[0], options)
                        .setPreviewText(previewText));

        System.out.println("voice_id:    " + r.getVoiceId());
        if (r.getPreviewAudio() != null) {
            String output = opts.getOrDefault("preview", "voice-design-preview.wav");
            r.savePreview(Path.of(output));
            System.out.println("preview:     " + Path.of(output).toAbsolutePath()
                    + " (" + r.getPreviewAudio().length + " bytes)");
        } else if (!r.isSuccess()) {
            System.err.println("错误: " + r.getMessage());
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
                用法: VoiceDesignCli [选项] <voicePrompt> [预览文本]
                选项:
                  --api-key <key>      DashScope API Key（默认取环境变量 DASHSCOPE_API_KEY）
                  --model <name>       声音设计模型（默认 qwen-voice-design；CosyVoice 用 voice-enrollment）
                  --target-model <m>   目标合成模型（默认 qwen3-tts-vd-2026-01-26）
                  --prefix <name>      音色名前缀（Qwen-TTS 默认 custom_voice）
                  --workspace-id <id>  CosyVoice 系列必需的业务空间 ID
                  --region <region>    CosyVoice 系列地域（默认 cn-beijing）
                  --preview <path>     预览音频输出文件（默认 voice-design-preview.wav）
                  --list               列出已创建的音色（可选 --prefix 过滤）
                  --delete <voiceId>   删除指定音色
                """);
    }
}
