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
 * 阿里云通义（DashScope）语音合成 provider，适配 qwen3-tts-flash / qwen-tts 等模型。
 * <p>
 * 走 DashScope 原生 multimodal-generation 端点（非 OpenAI 兼容模式），
 * 请求体为 {@code {model, input:{text, voice, language_type}}}；
 * 响应 {@code output.audio.data}（base64）或 {@code output.audio.url}
 * （24 小时有效的 OSS 地址，provider 会自动下载为字节）。
 * <p>
 * 必需配置：{@code apiKey}。<br>
 * 可选配置：{@code model}（默认 {@code qwen3-tts-flash}）、{@code voice}（默认 {@code Cherry}）。
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/qwen-tts-api
 */
public class QwenTtsProvider extends TtsProviderBase {

    /** 默认模型 */
    public static final String DEFAULT_MODEL = "qwen3-tts-flash";
    /** 默认音色 */
    public static final String DEFAULT_VOICE = "Cherry";

    public QwenTtsProvider() {
        this.apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    }

    @Override
    public TtsProviderType getProviderType() { return TtsProviderType.QWEN; }

    @Override
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_TTS_NO_API_KEY", false, "qwen_tts"));
        }
        String body = buildJsonBody(request);

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(apiUrl))
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
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ").append(apiKey).append("' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(buildJsonBody(request).replace("'", "'\\''")).append("'");
        return sb.toString();
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
     * 构造 multimodal-generation 请求体。
     */
    public String buildJsonBody(TtsRequest req) {
        TtsOptions opts = req.getOptions();
        JSONObject input = new JSONObject();
        input.put("text", req.getText());
        input.put("voice", resolveVoice(opts));
        if (opts.getLanguageType() != null && !opts.getLanguageType().isEmpty()) {
            input.put("language_type", opts.getLanguageType());
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
        if (!data.isEmpty()) {
            return new TtsResponse(Base64.getDecoder().decode(data), "audio/wav",
                    url.isEmpty() ? null : url, root);
        }
        if (!url.isEmpty()) {
            return new TtsResponse(download(url), "audio/wav", url, root);
        }
        throw new RuntimeException("Qwen TTS: output.audio has neither data nor url");
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
}
