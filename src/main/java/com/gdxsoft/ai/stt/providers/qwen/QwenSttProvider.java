package com.gdxsoft.ai.stt.providers.qwen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerI18nConstants;
import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.stt.SttOptions;
import com.gdxsoft.ai.stt.SttProviderBase;
import com.gdxsoft.ai.stt.SttProviderType;
import com.gdxsoft.ai.stt.SttRequest;
import com.gdxsoft.ai.stt.SttResponse;

/**
 * 阿里云通义（DashScope）语音识别 provider，适配 qwen3-asr-flash 等 ASR 模型。
 * <p>
 * 与 Whisper 风格接口不同，qwen ASR 走 OpenAI 兼容模式的
 * {@code /chat/completions} 端点，音频以 {@code input_audio}（base64 data URI）
 * 的形式放在用户消息里。
 * <p>
 * 必需配置：{@code apiKey}（{@link #setApiKey}）。<br>
 * 可选配置：{@code model}（{@code setConfig("model", "...")}），默认 {@code qwen3-asr-flash}。
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/developer-reference/qwen-audio
 */
public class QwenSttProvider extends SttProviderBase {

    /** 默认模型 */
    public static final String DEFAULT_MODEL = "qwen3-asr-flash";

    public QwenSttProvider() {
        this.apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.QWEN; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_STT_NO_API_KEY", false, "qwen_stt"));
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
            throw new IOException("Qwen STT HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    @Override
    public String curl(SttRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
            sb.append("  -H 'Authorization: Bearer ****' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildJsonBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (failed to render body): " + e.getMessage();
        }
    }

    /**
     * 解析模型名：options 优先，其次 config("model")，最后回退到 {@link #DEFAULT_MODEL}。
     * <p>
     * {@link SttOptions} 的通用默认值 "whisper-1" 对 qwen 无意义，视为未设置。
     */
    private String resolveModel(SttOptions opts) {
        String model = opts.getModel();
        if (model == null || model.isEmpty() || "whisper-1".equalsIgnoreCase(model)) {
            model = getConfig("model");
        }
        if (model == null || model.isEmpty()) {
            model = DEFAULT_MODEL;
        }
        return model;
    }

    /**
     * 构造 chat/completions 请求体（音频为 input_audio data URI）。
     */
    public String buildJsonBody(SttRequest req) throws IOException {
        AudioSource audio = req.getAudio();
        String mimeType = audio.mimeType() == null || audio.mimeType().isEmpty()
                ? "audio/webm" : audio.mimeType();
        String dataUri = "data:" + mimeType + ";base64,"
                + Base64.getEncoder().encodeToString(audio.materialize());

        JSONObject inputAudio = new JSONObject();
        inputAudio.put("data", dataUri);
        JSONObject audioItem = new JSONObject();
        audioItem.put("type", "input_audio");
        audioItem.put("input_audio", inputAudio);

        JSONArray content = new JSONArray();
        content.put(audioItem);
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", content);

        JSONObject body = new JSONObject();
        body.put("model", resolveModel(req.getOptions()));
        body.put("stream", false);
        body.put("messages", new JSONArray().put(msg));
        return body.toString();
    }

    /**
     * 解析 OpenAI 兼容模式响应：{@code choices[0].message.content}。
     * 错误响应（含 {@code error} 节点）抛出 RuntimeException。
     */
    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        if (root.has("error")) {
            JSONObject err = root.optJSONObject("error");
            String msg = err == null ? root.optString("error")
                    : err.optString("message", err.optString("code", "qwen stt error"));
            throw new RuntimeException("Qwen STT error: " + msg);
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Qwen STT: response has no choices");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String text = message == null ? "" : message.optString("content", "").trim();
        return new SttResponse(text, req.getOptions().getLanguage(), null, null, root);
    }
}
