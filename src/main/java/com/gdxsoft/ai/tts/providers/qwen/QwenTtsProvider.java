package com.gdxsoft.ai.tts.providers.qwen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerI18nConstants;
import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.tts.TtsOptions;
import com.gdxsoft.ai.tts.TtsProviderBase;
import com.gdxsoft.ai.tts.TtsProviderType;
import com.gdxsoft.ai.tts.TtsRequest;
import com.gdxsoft.ai.tts.TtsResponse;

/**
 * 阿里云通义（DashScope）语音合成 provider，支持三种模型系列：
 * <ul>
 *   <li><b>Qwen-TTS</b>（qwen3-tts-flash / qwen-tts 等）— multimodal-generation 端点，
 *       响应 {@code output.audio.data}（base64）或 {@code output.audio.url}</li>
 *   <li><b>Qwen-Audio-TTS</b>（qwen-audio-3.0-tts-*）— SpeechSynthesizer 端点（workspace-scoped），
 *       响应 {@code output.audio.url}，支持 {@code format}/{@code sample_rate}/{@code instruction}</li>
 *   <li><b>CosyVoice</b>（cosyvoice-v*）— 同 Qwen-Audio-TTS 端点，
 *       支持 {@code instruction}（注意参数名不同于 Qwen-TTS 的 {@code instructions}）</li>
 * </ul>
 * <p>
 * 必需配置：{@code apiKey}。<br>
 * Qwen-Audio-TTS / CosyVoice 还需 {@code workspaceId}（通过 {@link #setConfig} 设置）。<br>
 * 可选配置：{@code model}（默认 {@code qwen3-tts-flash}）、{@code voice}（默认 {@code Cherry}）、
 * {@code region}（默认 {@code cn-beijing}）。
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/qwen-tts-api
 */
public class QwenTtsProvider extends TtsProviderBase {

    /** 默认模型 */
    public static final String DEFAULT_MODEL = "qwen3-tts-flash";
    /** 默认音色 */
    public static final String DEFAULT_VOICE = "Cherry";
    /** 默认地域 */
    public static final String DEFAULT_REGION = "cn-beijing";

    /** Qwen-TTS 端点（multimodal-generation） */
    public static final String MULTIMODAL_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    /** Qwen-Audio-TTS / CosyVoice 端点模板（需替换 workspaceId 和 region） */
    public static final String SPEECH_SYNTHESIZER_URL_TEMPLATE =
            "https://{workspaceId}.{region}.maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer";

    public QwenTtsProvider() {
        this.apiUrl = MULTIMODAL_URL;
    }

    @Override
    public TtsProviderType getProviderType() { return TtsProviderType.QWEN; }

    /**
     * 判断模型属于哪种 API 模式。
     */
    static ApiPattern detectPattern(String model) {
        if (model == null) return ApiPattern.QWEN_TTS;
        String lower = model.toLowerCase();
        if (lower.startsWith("qwen-audio") || lower.startsWith("cosyvoice")) {
            return ApiPattern.SPEECH_SYNTHESIZER;
        }
        return ApiPattern.QWEN_TTS;
    }

    @Override
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_TTS_NO_API_KEY", false, "qwen_tts"));
        }
        String model = resolveModel(request.getOptions());
        ApiPattern pattern = detectPattern(model);

        String endpoint = resolveEndpoint(pattern);
        String body = pattern == ApiPattern.SPEECH_SYNTHESIZER
                ? buildSpeechSynthesizerBody(request, model)
                : buildJsonBody(request);

        LOGGER.debug("Qwen TTS: pattern={}, endpoint={}, model={}", pattern, endpoint, model);

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException(ChatManagerI18nConstants.getText(
                    "ERROR_TTS_HTTP_ERROR", false, String.valueOf(resp.statusCode()), resp.body()));
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    @Override
    public String curl(TtsRequest request) {
        String model = resolveModel(request.getOptions());
        ApiPattern pattern = detectPattern(model);
        String endpoint = resolveEndpoint(pattern);
        String body = pattern == ApiPattern.SPEECH_SYNTHESIZER
                ? buildSpeechSynthesizerBody(request, model)
                : buildJsonBody(request);

        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(endpoint).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ").append(apiKey).append("' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    /** 解析端点：SpeechSynthesizer 需要 workspaceId，Qwen-TTS 用固定端点。 */
    private String resolveEndpoint(ApiPattern pattern) {
        if (pattern == ApiPattern.QWEN_TTS) {
            // 用户显式改了 apiUrl 则尊重
            return (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : MULTIMODAL_URL;
        }
        // SpeechSynthesizer 端点
        if (apiUrl != null && !apiUrl.isEmpty() && !apiUrl.contains("/multimodal-generation/")) {
            return apiUrl;
        }
        String workspaceId = getConfig("workspaceId");
        if (workspaceId == null || workspaceId.isEmpty()) {
            throw new IllegalStateException(
                    "Qwen-Audio-TTS / CosyVoice 需要设置 workspaceId，" +
                    "请通过 setConfig(\"workspaceId\", \"...\") 配置");
        }
        String region = getConfig("region");
        if (region == null || region.isEmpty()) region = DEFAULT_REGION;
        return SPEECH_SYNTHESIZER_URL_TEMPLATE
                .replace("{workspaceId}", workspaceId)
                .replace("{region}", region);
    }

    /**
     * 构造 Qwen-Audio-TTS / CosyVoice 请求体（SpeechSynthesizer 端点）。
     * <pre>{@code
     * {
     *   "model": "cosyvoice-v3-flash",
     *   "input": {
     *     "text": "...",
     *     "voice": "longanyang",
     *     "format": "wav",
     *     "sample_rate": 24000,
     *     "instruction": "..."  // CosyVoice 用 instruction
     *   }
     * }
     * }</pre>
     */
    private String buildSpeechSynthesizerBody(TtsRequest req, String model) {
        TtsOptions opts = req.getOptions();
        JSONObject input = new JSONObject();
        input.put("text", req.getText());
        input.put("voice", resolveVoice(opts));

        String format = opts.getFormat();
        if (format != null && !format.isEmpty()) {
            input.put("format", format);
        }
        if (opts.getSampleRate() != null) {
            input.put("sample_rate", opts.getSampleRate());
        }
        // CosyVoice / Qwen-Audio-TTS 用 instruction
        String instruction = opts.getInstruction();
        if (instruction == null || instruction.isEmpty()) {
            instruction = getConfig("instruction");
        }
        if (instruction != null && !instruction.isEmpty()) {
            input.put("instruction", instruction);
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        return body.toString();
    }

    /** 解析模型名：options 优先，其次 config("model")，最后回退到 {@link #DEFAULT_MODEL}。 */
    private String resolveModel(TtsOptions opts) {
        String model = opts.getModel();
        if (model == null || model.isEmpty()) model = getConfig("model");
        if (model == null || model.isEmpty()) model = DEFAULT_MODEL;
        return model;
    }

    /** 解析音色：options 优先，其次 config("voice")，最后回退到 {@link #DEFAULT_VOICE}。 */
    private String resolveVoice(TtsOptions opts) {
        String voice = opts.getVoice();
        if (voice == null || voice.isEmpty()) voice = getConfig("voice");
        if (voice == null || voice.isEmpty()) voice = DEFAULT_VOICE;
        return voice;
    }

    /**
     * 构造 Qwen-TTS 请求体（multimodal-generation 端点）。
     */
    public String buildJsonBody(TtsRequest req) {
        TtsOptions opts = req.getOptions();
        JSONObject input = new JSONObject();
        input.put("text", req.getText());
        input.put("voice", resolveVoice(opts));
        if (opts.getLanguageType() != null && !opts.getLanguageType().isEmpty()) {
            input.put("language_type", opts.getLanguageType());
        }
        // Qwen-TTS Instruct 用 instructions
        String instruction = opts.getInstruction();
        if (instruction == null || instruction.isEmpty()) {
            instruction = getConfig("instruction");
        }
        if (instruction != null && !instruction.isEmpty()) {
            input.put("instructions", instruction);
        }
        JSONObject body = new JSONObject();
        body.put("model", resolveModel(opts));
        body.put("input", input);
        return body.toString();
    }

    /**
     * 解析 DashScope 响应：优先取 {@code output.audio.data}（base64），
     * 否则下载 {@code output.audio.url}；错误响应抛出 RuntimeException。
     */
    public TtsResponse parseResponse(JSONObject root, TtsRequest req) {
        String code = root.optString("code", "");
        if (!code.isEmpty()) {
            throw new RuntimeException("Qwen TTS error " + code + ": " + root.optString("message", ""));
        }
        JSONObject output = root.optJSONObject("output");
        JSONObject audio = output == null ? null : output.optJSONObject("audio");
        if (audio == null) {
            throw new RuntimeException("Qwen TTS: response has no output.audio");
        }
        String data = audio.optString("data", "");
        String url = audio.optString("url", "");
        // Qwen-TTS 固定返回 WAV；SpeechSynthesizer 的 format 由请求参数决定
        String mime = "audio/wav";

        if (!data.isEmpty()) {
            return new TtsResponse(Base64.getDecoder().decode(data), mime,
                    url.isEmpty() ? null : url, root);
        }
        if (!url.isEmpty()) {
            return new TtsResponse(download(url), mime, url, root);
        }
        throw new RuntimeException("Qwen TTS: output.audio has neither data nor url");
    }

    /** 音频格式到 MIME 的映射。 */
    static String mimeOf(String format) {
        if (format == null || format.isEmpty()) return "audio/wav";
        return switch (format.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "pcm" -> "audio/pcm";
            case "ogg" -> "audio/ogg";
            case "opus" -> "audio/opus";
            case "flac" -> "audio/flac";
            default -> "audio/" + format.toLowerCase();
        };
    }

    /** 下载 OSS 音频 URL（24 小时有效）。 */
    private byte[] download(String url) {
        try {
            HttpClient client = HttpUtils.createHttpClient();
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<byte[]> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("audio url HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qwen TTS: download interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Qwen TTS: failed to download audio url: " + e.getMessage(), e);
        }
    }

    /** API 模式。 */
    enum ApiPattern {
        /** Qwen-TTS 系列：multimodal-generation 端点 */
        QWEN_TTS,
        /** Qwen-Audio-TTS / CosyVoice：SpeechSynthesizer 端点 */
        SPEECH_SYNTHESIZER
    }
}
