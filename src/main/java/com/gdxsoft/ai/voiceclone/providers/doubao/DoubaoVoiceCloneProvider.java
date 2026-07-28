package com.gdxsoft.ai.voiceclone.providers.doubao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderBase;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderType;
import com.gdxsoft.ai.voiceclone.VoiceCloneRequest;
import com.gdxsoft.ai.voiceclone.VoiceCloneResponse;

/**
 * 豆包（火山引擎）声音克隆 provider。
 * <p>
 * 基于火山引擎 MegaTTS 声音复刻 HTTP API，支持：
 * <ul>
 *   <li>新建音色：传入音频样本，返回 speaker_id</li>
 *   <li>升级音色：传入已有 speaker_id + 新音频，增强音色质量</li>
 * </ul>
 * <p>
 * 必需配置：{@code apiKey}（通过 {@link #setApiKey} 设置）。<br>
 * 可选配置：
 * <ul>
 *   <li>{@code apiUrl} — 覆盖默认端点</li>
 *   <li>{@code queryUrl} — 音色查询端点（{@link #setConfig}("queryUrl", "...")}）</li>
 *   <li>{@code demoText} — 默认试听文本</li>
 * </ul>
 * <p>
 * 文档：https://docs.volcengine.com/docs/6561/2534906
 *
 * @since 1.1.0
 */
public class DoubaoVoiceCloneProvider extends VoiceCloneProviderBase {

    /** 默认声音克隆/训练端点 */
    public static final String DEFAULT_CLONE_URL = "https://openspeech.bytedance.com/api/v3/tts/voice_clone";

    /** 默认音色查询端点 */
    public static final String DEFAULT_QUERY_URL = "https://openspeech.bytedance.com/api/v3/tts/voice_query";

    public DoubaoVoiceCloneProvider() {
        this.apiUrl = DEFAULT_CLONE_URL;
    }

    @Override
    public VoiceCloneProviderType getProviderType() {
        return VoiceCloneProviderType.DOUBAO;
    }

    @Override
    public VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("豆包声音克隆需要设置 apiKey");
        }

        String body = buildCloneBody(request);
        String requestId = UUID.randomUUID().toString();

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", apiKey)
                .header("X-Api-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("豆包声音克隆响应状态: {}, logId: {}", resp.statusCode(),
                resp.headers().firstValue("X-Tt-Logid").orElse(""));

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("豆包声音克隆 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseCloneResponse(new JSONObject(resp.body()));
    }

    @Override
    public VoiceCloneResponse query(String speakerId) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("豆包声音克隆需要设置 apiKey");
        }
        if (speakerId == null || speakerId.isEmpty()) {
            throw new IllegalArgumentException("speakerId 不能为空");
        }

        String queryUrl = getConfig("queryUrl");
        if (queryUrl == null || queryUrl.isEmpty()) {
            queryUrl = DEFAULT_QUERY_URL;
        }

        JSONObject body = new JSONObject();
        body.put("speaker_id", speakerId);

        String requestId = UUID.randomUUID().toString();
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(queryUrl))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", apiKey)
                .header("X-Api-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("豆包音色查询响应状态: {}", resp.statusCode());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("豆包音色查询 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseQueryResponse(new JSONObject(resp.body()));
    }

    @Override
    public String curl(VoiceCloneRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -H 'X-Api-Key: ****' \\\n");
            sb.append("  -H 'X-Api-Request-Id: ").append(UUID.randomUUID()).append("' \\\n");
            sb.append("  -d '").append(buildCloneBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (构造请求体失败): " + e.getMessage();
        }
    }

    /**
     * 构造声音克隆/升级请求体。
     */
    private String buildCloneBody(VoiceCloneRequest request) throws IOException {
        AudioSource audio = request.getAudio();
        byte[] audioBytes = audio.materialize();
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        // 音频格式：request 指定 > 从 mimeType 推导 > 默认 wav
        String format = request.getAudioFormat();
        if (format == null || format.isEmpty()) {
            format = resolveFormat(audio.mimeType());
        }

        JSONObject audioObj = new JSONObject();
        audioObj.put("data", audioBase64);
        audioObj.put("format", format);

        JSONObject body = new JSONObject();
        body.put("speaker_id", request.getSpeakerId() != null ? request.getSpeakerId() : "");
        body.put("audio", audioObj);
        body.put("language", request.getLanguage());

        // extra_params
        JSONObject extraParams = new JSONObject();
        String demoText = request.getDemoText();
        if (demoText == null || demoText.isEmpty()) {
            demoText = getConfig("demoText");
        }
        if (demoText != null && !demoText.isEmpty()) {
            extraParams.put("demo_text", demoText);
        }
        String denoiseModelId = request.getDenoiseModelId();
        if (denoiseModelId != null && !denoiseModelId.isEmpty()) {
            extraParams.put("voice_clone_denoise_model_id", denoiseModelId);
        }
        // 合并 options 中的额外参数
        if (request.getOptions() != null) {
            for (var entry : request.getOptions().getExtras().entrySet()) {
                extraParams.put(entry.getKey(), entry.getValue());
            }
        }
        if (!extraParams.isEmpty()) {
            body.put("extra_params", extraParams);
        }

        return body.toString();
    }

    /**
     * 解析克隆/升级响应。
     * <p>
     * 预期响应格式（成功）：
     * <pre>{@code
     * {
     *   "code": 0,
     *   "message": "success",
     *   "data": {
     *     "speaker_id": "xxx",
     *     "status": "success"
     *   }
     * }
     * }</pre>
     */
    private VoiceCloneResponse parseCloneResponse(JSONObject root) {
        int code = root.optInt("code", -1);
        String message = root.optString("message", "");

        if (code != 0) {
            return VoiceCloneResponse.error("code=" + code + ", " + message, root);
        }

        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            // 某些响应可能直接在顶层
            String speakerId = root.optString("speaker_id", null);
            if (speakerId != null && !speakerId.isEmpty()) {
                return VoiceCloneResponse.success(speakerId, root);
            }
            return VoiceCloneResponse.error("响应中无 data 字段", root);
        }

        String speakerId = data.optString("speaker_id", null);
        String status = data.optString("status", "success");

        VoiceCloneResponse resp = new VoiceCloneResponse(speakerId, status, root);
        resp.setMessage(message);
        return resp;
    }

    /**
     * 解析音色查询响应。
     */
    private VoiceCloneResponse parseQueryResponse(JSONObject root) {
        int code = root.optInt("code", -1);
        String message = root.optString("message", "");

        if (code != 0) {
            return VoiceCloneResponse.error("code=" + code + ", " + message, root);
        }

        JSONObject data = root.optJSONObject("data");
        String speakerId = data != null ? data.optString("speaker_id", null) : root.optString("speaker_id", null);
        String status = data != null ? data.optString("status", "success") : "success";

        VoiceCloneResponse resp = new VoiceCloneResponse(speakerId, status, root);
        resp.setMessage(message);
        return resp;
    }

    /**
     * 从 MIME 类型推导音频格式名。
     */
    static String resolveFormat(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return "wav";
        String fmt = mimeType;
        int slash = fmt.indexOf('/');
        if (slash >= 0) fmt = fmt.substring(slash + 1);
        int semi = fmt.indexOf(';');
        if (semi >= 0) fmt = fmt.substring(0, semi);
        fmt = fmt.trim().toLowerCase();
        if (fmt.startsWith("x-")) fmt = fmt.substring(2);
        if ("mpeg".equals(fmt) || "mpga".equals(fmt)) fmt = "mp3";
        if ("x-wav".equals(fmt) || "wave".equals(fmt)) fmt = "wav";
        return fmt.isEmpty() ? "wav" : fmt;
    }
}
