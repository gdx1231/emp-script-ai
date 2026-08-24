package com.gdxsoft.ai.tts.providers.minimax;

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
 * MiniMax 语音合成 provider（通过 DashScope 代理调用）。
 * <p>
 * 端点：{@code https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}。
 * <p>
 * 支持参数：
 * <ul>
 *   <li>{@code voice_id} — 音色 ID（如 male-qn-qingse）</li>
 *   <li>{@code speed} / {@code vol} / {@code pitch} — 语速/音量/音调</li>
 *   <li>{@code emotion} — 情感控制（如 happy / sad / angry）</li>
 *   <li>{@code format} / {@code sample_rate} / {@code bitrate} / {@code channel} — 音频输出参数</li>
 * </ul>
 * <p>
 * 必需配置：{@code apiKey}、{@code workspaceId}。<br>
 * 可选配置：{@code model}（默认 {@code MiniMax/speech-2.8-hd}）、{@code voice}（默认 {@code male-qn-qingse}）、
 * {@code region}（默认 {@code cn-beijing}）。
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/minimax-speech-synthesis/
 *
 * @since 1.1.0
 */
public class MiniMaxTtsProvider extends TtsProviderBase {

    public static final String DEFAULT_MODEL = "MiniMax/speech-2.8-hd";
    public static final String DEFAULT_VOICE = "male-qn-qingse";
    public static final String DEFAULT_REGION = "cn-beijing";

    public static final String API_URL_TEMPLATE =
            "https://{workspaceId}.{region}.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    public MiniMaxTtsProvider() {
        // apiUrl 在 resolveEndpoint() 中动态构造
    }

    @Override
    public TtsProviderType getProviderType() { return TtsProviderType.MINIMAX; }

    @Override
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_TTS_NO_API_KEY", false, "minimax_tts"));
        }
        String endpoint = resolveEndpoint();
        String body = buildJsonBody(request);

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
        return parseResponse(new JSONObject(resp.body()));
    }

    @Override
    public String curl(TtsRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(resolveEndpoint()).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ").append(apiKey).append("' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(buildJsonBody(request).replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    private String resolveEndpoint() {
        if (apiUrl != null && !apiUrl.isEmpty()) return apiUrl;
        String workspaceId = getConfig("workspaceId");
        if (workspaceId == null || workspaceId.isEmpty()) {
            throw new IllegalStateException(
                    "MiniMax TTS 需要设置 workspaceId，请通过 setConfig(\"workspaceId\", \"...\") 配置");
        }
        String region = getConfig("region");
        if (region == null || region.isEmpty()) region = DEFAULT_REGION;
        return API_URL_TEMPLATE
                .replace("{workspaceId}", workspaceId)
                .replace("{region}", region);
    }

    private String resolveModel(TtsOptions opts) {
        String model = opts.getModel();
        if (model == null || model.isEmpty()) model = getConfig("model");
        if (model == null || model.isEmpty()) model = DEFAULT_MODEL;
        return model;
    }

    private String resolveVoice(TtsOptions opts) {
        String voice = opts.getVoice();
        if (voice == null || voice.isEmpty()) voice = getConfig("voice");
        if (voice == null || voice.isEmpty()) voice = DEFAULT_VOICE;
        return voice;
    }

    /**
     * 构造 MiniMax 请求体。
     * <pre>{@code
     * {
     *   "model": "MiniMax/speech-2.8-hd",
     *   "input": {
     *     "text": "...",
     *     "voice_setting": { "voice_id": "...", "speed": 1, "vol": 1, "pitch": 0, "emotion": "happy" },
     *     "audio_setting": { "sample_rate": 32000, "bitrate": 128000, "format": "mp3", "channel": 1 }
     *   }
     * }
     * }</pre>
     */
    public String buildJsonBody(TtsRequest req) {
        TtsOptions opts = req.getOptions();

        JSONObject voiceSetting = new JSONObject();
        voiceSetting.put("voice_id", resolveVoice(opts));
        if (opts.getSpeed() != null) {
            voiceSetting.put("speed", opts.getSpeed());
        }
        String vol = getConfig("vol");
        if (vol != null && !vol.isEmpty()) voiceSetting.put("vol", Integer.parseInt(vol));
        String pitch = getConfig("pitch");
        if (pitch != null && !pitch.isEmpty()) voiceSetting.put("pitch", Integer.parseInt(pitch));
        String emotion = getConfig("emotion");
        if (emotion != null && !emotion.isEmpty()) voiceSetting.put("emotion", emotion);

        JSONObject audioSetting = new JSONObject();
        String format = opts.getFormat();
        audioSetting.put("format", (format != null && !format.isEmpty()) ? format : "mp3");
        if (opts.getSampleRate() != null) {
            audioSetting.put("sample_rate", opts.getSampleRate());
        } else {
            audioSetting.put("sample_rate", 32000);
        }
        String bitrate = getConfig("bitrate");
        if (bitrate != null && !bitrate.isEmpty()) {
            audioSetting.put("bitrate", Integer.parseInt(bitrate));
        } else {
            audioSetting.put("bitrate", 128000);
        }
        audioSetting.put("channel", 1);

        JSONObject input = new JSONObject();
        input.put("text", req.getText());
        input.put("voice_setting", voiceSetting);
        input.put("audio_setting", audioSetting);

        JSONObject body = new JSONObject();
        body.put("model", resolveModel(opts));
        body.put("input", input);
        return body.toString();
    }

    /**
     * 解析响应：MiniMax 返回 {@code output.audio}（base64 或 url）。
     */
    public TtsResponse parseResponse(JSONObject root) {
        String code = root.optString("code", "");
        if (!code.isEmpty()) {
            throw new RuntimeException("MiniMax TTS error " + code + ": " + root.optString("message", ""));
        }
        JSONObject output = root.optJSONObject("output");
        JSONObject audio = output == null ? null : output.optJSONObject("audio");
        if (audio == null) {
            throw new RuntimeException("MiniMax TTS: response has no output.audio");
        }
        String data = audio.optString("data", "");
        String url = audio.optString("url", "");
        if (!data.isEmpty()) {
            return new TtsResponse(Base64.getDecoder().decode(data), "audio/mpeg",
                    url.isEmpty() ? null : url, root);
        }
        if (!url.isEmpty()) {
            return new TtsResponse(download(url), "audio/mpeg", url, root);
        }
        throw new RuntimeException("MiniMax TTS: output.audio has neither data nor url");
    }

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
            throw new RuntimeException("MiniMax TTS: download interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("MiniMax TTS: failed to download audio url: " + e.getMessage(), e);
        }
    }
}
