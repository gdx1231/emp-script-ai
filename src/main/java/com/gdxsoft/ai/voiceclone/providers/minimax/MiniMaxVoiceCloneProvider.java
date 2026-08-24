package com.gdxsoft.ai.voiceclone.providers.minimax;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderBase;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderType;
import com.gdxsoft.ai.voiceclone.VoiceCloneRequest;
import com.gdxsoft.ai.voiceclone.VoiceCloneResponse;

/**
 * MiniMax 声音克隆 provider（通过 DashScope 代理调用）。
 * <p>
 * 基于 MiniMax speech-2.8 系列模型的声音复刻 API，支持：
 * <ul>
 *   <li>新建音色：传入音频 URL + 自定义 voice_id，首次合成时扣 9.9 元音色解锁费</li>
 * </ul>
 * <p>
 * <b>注意</b>：MiniMax 不支持音色查询（query）和列表/删除管理操作。
 * <p>
 * 必需配置：{@code apiKey}（DashScope API Key）。<br>
 * 可选配置：
 * <ul>
 *   <li>{@code model} — 模型名，默认 {@code MiniMax/speech-2.8-turbo}</li>
 *   <li>{@code text} — 复刻试听文本，默认"你说是什么就是什么"</li>
 * </ul>
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/voice-clone
 *
 * @since 1.1.0
 */
public class MiniMaxVoiceCloneProvider extends VoiceCloneProviderBase {

    public static final String DEFAULT_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    /** 默认模型 */
    public static final String DEFAULT_MODEL = "MiniMax/speech-2.8-turbo";

    /** 默认试听文本 */
    public static final String DEFAULT_TEXT = "你说是什么就是什么";

    public MiniMaxVoiceCloneProvider() {
        this.apiUrl = DEFAULT_API_URL;
    }

    @Override
    public VoiceCloneProviderType getProviderType() {
        return VoiceCloneProviderType.MINIMAX;
    }

    @Override
    public VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("MiniMax 声音克隆需要设置 apiKey");
        }

        String body = buildCloneBody(request);

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("MiniMax 声音克隆响应状态: {}", resp.statusCode());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("MiniMax 声音克隆 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseCloneResponse(new JSONObject(resp.body()), request);
    }

    /**
     * MiniMax 不支持 query 操作。
     * 复刻时用户自行指定 voice_id，无需查询。
     */
    @Override
    public VoiceCloneResponse query(String speakerId) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("MiniMax 声音克隆不支持 query 操作");
    }

    @Override
    public String curl(VoiceCloneRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
            sb.append("  -H 'Authorization: Bearer ****' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildCloneBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (构造请求体失败): " + e.getMessage();
        }
    }

    /**
     * 构造 MiniMax 声音克隆请求体。
     * <pre>{@code
     * {
     *   "model": "MiniMax/speech-2.8-turbo",
     *   "input": {
     *     "action": "voice_clone",
     *     "voice_id": "my-custom-voice",
     *     "audio_url": "https://...",
     *     "text": "你说是什么就是什么"
     *   }
     * }
     * }</pre>
     */
    private String buildCloneBody(VoiceCloneRequest request) throws IOException {
        String model = getConfig("model");
        if (model == null || model.isEmpty()) model = DEFAULT_MODEL;

        // voice_id：request.speakerId > config("voiceId") > 默认
        String voiceId = request.getSpeakerId();
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = getConfig("voiceId");
        }
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = "my-custom-voice";
        }

        // 音频 URL
        String audioUrl = resolveAudioUrl(request);

        // 试听文本
        String text = request.getDemoText();
        if (text == null || text.isEmpty()) {
            text = getConfig("text");
        }
        if (text == null || text.isEmpty()) {
            text = DEFAULT_TEXT;
        }

        JSONObject input = new JSONObject();
        input.put("action", "voice_clone");
        input.put("voice_id", voiceId);
        input.put("audio_url", audioUrl);
        input.put("text", text);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        return body.toString();
    }

    /**
     * 解析音频 URL：优先 config("audioUrl")，其次 AudioSource 为 UrlSource 时取其 URL。
     */
    private String resolveAudioUrl(VoiceCloneRequest request) throws IOException {
        String audioUrl = getConfig("audioUrl");
        if (audioUrl != null && !audioUrl.isEmpty()) {
            return audioUrl;
        }

        AudioSource audio = request.getAudio();
        if (audio instanceof AudioSource.UrlSource urlSource) {
            return urlSource.url();
        }

        throw new IllegalArgumentException(
                "MiniMax 声音克隆需要公网可访问的音频 URL，" +
                "请通过 setConfig(\"audioUrl\", \"...\") 或 AudioSource.url(...) 传入");
    }

    /**
     * 解析克隆响应。
     * <p>
     * MiniMax 声音复刻成功后会生成试听音频，响应中包含 {@code output.audio} 数据。
     * voice_id 由用户在请求中自行指定。
     */
    private VoiceCloneResponse parseCloneResponse(JSONObject root, VoiceCloneRequest request) {
        String code = root.optString("code", null);
        if (code != null && !code.isEmpty()) {
            return VoiceCloneResponse.error(code + ": " + root.optString("message", ""), root);
        }

        // voice_id 是用户在请求中指定的
        String voiceId = request.getSpeakerId();
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = getConfig("voiceId");
        }
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = "my-custom-voice";
        }

        return VoiceCloneResponse.success(voiceId, root);
    }
}
