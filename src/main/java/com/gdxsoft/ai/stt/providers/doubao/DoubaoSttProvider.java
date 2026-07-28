package com.gdxsoft.ai.stt.providers.doubao;

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
 * 豆包（火山方舟）语音识别 provider，适配具备音频理解能力的 doubao-seed 模型。
 * <p>
 * 走方舟 OpenAI 兼容模式的 {@code /chat/completions} 端点，音频以
 * {@code input_audio}（base64 原文 + format）的形式放在用户消息里。
 * <p>
 * 必需配置：{@code apiKey}（{@link #setApiKey}）。<br>
 * 可选配置：{@code model}（{@code setConfig("model", "...")} 或
 * {@link SttOptions#setModel}）、转写提示词（{@link SttOptions#setPrompt}）。
 * <p>
 * 文档：https://www.volcengine.com/docs/82379/2377589
 */
public class DoubaoSttProvider extends SttProviderBase {

    /** 默认模型（方舟音频理解文档示例模型，可按需替换 Model ID） */
    public static final String DEFAULT_MODEL = "doubao-seed-2-0-lite-260428";

    /** 默认转写提示词，可用 SttOptions.prompt 覆盖 */
    public static final String DEFAULT_PROMPT = "请将音频内容转写为文字。";

    public DoubaoSttProvider() {
        this.apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.DOUBAO; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_STT_NO_API_KEY", false, "doubao_stt"));
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
            throw new IOException("Doubao STT HTTP " + resp.statusCode() + ": " + resp.body());
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
     * {@link SttOptions} 的通用默认值 "whisper-1" 对豆包无意义，视为未设置。
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
     * 从 mimeType 推导方舟要求的音频格式（如 audio/webm → webm，audio/x-wav → wav）。
     */
    static String resolveFormat(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return "webm";
        String fmt = mimeType;
        int slash = fmt.indexOf('/');
        if (slash >= 0) fmt = fmt.substring(slash + 1);
        int semi = fmt.indexOf(';');
        if (semi >= 0) fmt = fmt.substring(0, semi);
        fmt = fmt.trim().toLowerCase();
        if (fmt.startsWith("x-")) fmt = fmt.substring(2); // audio/x-wav → wav
        if ("mpeg".equals(fmt) || "mpga".equals(fmt)) fmt = "mp3"; // audio/mpeg → mp3（方舟格式名）
        return fmt.isEmpty() ? "webm" : fmt;
    }

    /**
     * 构造 chat/completions 请求体（input_audio 为 base64 原文 + format，附转写提示词）。
     */
    public String buildJsonBody(SttRequest req) throws IOException {
        AudioSource audio = req.getAudio();
        SttOptions opts = req.getOptions();

        JSONObject inputAudio = new JSONObject();
        inputAudio.put("data", Base64.getEncoder().encodeToString(audio.materialize()));
        inputAudio.put("format", resolveFormat(audio.mimeType()));
        JSONObject audioItem = new JSONObject();
        audioItem.put("type", "input_audio");
        audioItem.put("input_audio", inputAudio);

        String prompt = opts.getPrompt();
        if (prompt == null || prompt.isEmpty()) prompt = DEFAULT_PROMPT;
        JSONObject textItem = new JSONObject();
        textItem.put("type", "text");
        textItem.put("text", prompt);

        JSONArray content = new JSONArray();
        content.put(audioItem);
        content.put(textItem);
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", content);

        JSONObject body = new JSONObject();
        body.put("model", resolveModel(opts));
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
                    : err.optString("message", err.optString("code", "doubao stt error"));
            throw new RuntimeException("Doubao STT error: " + msg);
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Doubao STT: response has no choices");
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String text = message == null ? "" : message.optString("content", "").trim();
        return new SttResponse(text, req.getOptions().getLanguage(), null, null, root);
    }
}
