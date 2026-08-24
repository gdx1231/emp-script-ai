package com.gdxsoft.ai.tts.providers.doubao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerI18nConstants;
import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.tts.TtsOptions;
import com.gdxsoft.ai.tts.TtsProviderBase;
import com.gdxsoft.ai.tts.TtsProviderType;
import com.gdxsoft.ai.tts.TtsRequest;
import com.gdxsoft.ai.tts.TtsResponse;

/**
 * 豆包（火山引擎）语音合成 provider（openspeech V3，新版控制台 {@code X-Api-Key} 单头鉴权）。
 * <p>
 * 按模型自动选择接口：
 * <ul>
 *   <li><b>seed-audio-1.0 / seed-audio-1.0-multilingual</b>（默认）— HTTP 非流式
 *   {@code /api/v3/tts/create}，一次性返回 base64 音频；</li>
 *   <li><b>seed-tts-2.0 等 seed-tts-* 模型</b> — HTTP Chunked 单向流式
 *   {@code /api/v3/tts/unidirectional}（{@code X-Api-Resource-Id} 头），
 *   逐行返回 JSON 音频分片，provider 聚合为完整音频；speaker 音色必传
 *   （未设置时默认 {@code zh_female_xiaohe_uranus_bigtts}）。</li>
 * </ul>
 * 可选配置：{@code model}、{@code voice}、{@code resourceId}（默认取模型名）、
 * {@code uid}（默认 emp-script-ai）。
 * <p>
 * 文档：https://www.volcengine.com/docs/6561/2534847 （tts/create）、
 * https://www.volcengine.com/docs/6561/1598757 （unidirectional）
 */
public class DoubaoTtsProvider extends TtsProviderBase {

    /** 默认模型（seed-audio 系列，走 tts/create 非流式接口） */
    public static final String DEFAULT_MODEL = "seed-audio-1.0";
    /** seed-tts-* 模型的默认音色（豆包语音合成模型2.0音色） */
    public static final String DEFAULT_STREAM_VOICE = "zh_female_xiaohe_uranus_bigtts";
    /** seed-tts-* 模型的流式端点 */
    public static final String UNIDIRECTIONAL_URL = "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    /** 非流式默认端点 */
    public static final String CREATE_URL = "https://openspeech.bytedance.com/api/v3/tts/create";

    public DoubaoTtsProvider() {
        this.apiUrl = CREATE_URL;
    }

    @Override
    public TtsProviderType getProviderType() { return TtsProviderType.DOUBAO; }

    /** 是否为 seed-tts-* 系列模型（走 unidirectional 流式接口）。 */
    public static boolean isStreamModel(String model) {
        return model != null && model.toLowerCase().startsWith("seed-tts");
    }

    @Override
    public TtsResponse synthesize(TtsRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_TTS_NO_API_KEY", false, "doubao_tts"));
        }
        String model = resolveModel(request.getOptions());
        return isStreamModel(model)
                ? synthesizeStream(request, model)
                : synthesizeCreate(request, model);
    }

    // ------------------------------------------------------------
    // seed-audio：/api/v3/tts/create（非流式）
    // ------------------------------------------------------------

    private TtsResponse synthesizeCreate(TtsRequest request, String model)
            throws IOException, InterruptedException {
        String body = buildJsonBody(request, model);
        HttpResponse<String> resp = send(resolveCreateUrl(), body, null);
        if (resp.statusCode() / 100 != 2) {
            throw new IOException(ChatManagerI18nConstants.getText(
                    "ERROR_TTS_HTTP_ERROR", false, String.valueOf(resp.statusCode()), resp.body()));
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    /** apiUrl 被显式改成非默认地址时尊重之，否则用 create 端点。 */
    private String resolveCreateUrl() {
        return apiUrl == null || apiUrl.isEmpty() ? CREATE_URL : apiUrl;
    }

    // ------------------------------------------------------------
    // seed-tts-*：/api/v3/tts/unidirectional（HTTP Chunked 流式）
    // ------------------------------------------------------------

    private TtsResponse synthesizeStream(TtsRequest request, String model)
            throws IOException, InterruptedException {
        String resourceId = getConfig("resourceId");
        if (resourceId == null || resourceId.isEmpty()) resourceId = model;

        String body = buildStreamJsonBody(request);
        HttpResponse<String> resp = send(resolveStreamUrl(), body, resourceId);
        if (resp.statusCode() / 100 != 2) {
            throw new IOException(ChatManagerI18nConstants.getText(
                    "ERROR_TTS_HTTP_ERROR", false, String.valueOf(resp.statusCode()), resp.body()));
        }
        return parseChunked(resp.body(), request);
    }

    /** apiUrl 被显式改成非 create 地址时尊重之，否则用 unidirectional 端点。 */
    private String resolveStreamUrl() {
        if (apiUrl == null || apiUrl.isEmpty() || apiUrl.contains("/tts/create")) {
            return UNIDIRECTIONAL_URL;
        }
        return apiUrl;
    }

    private HttpResponse<String> send(String url, String body, String resourceId)
            throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("X-Api-Key", apiKey)
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (resourceId != null && !resourceId.isEmpty()) {
            b.header("X-Api-Resource-Id", resourceId);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public String curl(TtsRequest request) {
        String model = resolveModel(request.getOptions());
        StringBuilder sb = new StringBuilder();
        if (isStreamModel(model)) {
            String resourceId = getConfig("resourceId");
            if (resourceId == null || resourceId.isEmpty()) resourceId = model;
            sb.append("curl -X POST '").append(resolveStreamUrl()).append("' \\\n");
            sb.append("  -H 'X-Api-Key: ").append(apiKey).append("' \\\n");
            sb.append("  -H 'X-Api-Resource-Id: ").append(resourceId).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildStreamJsonBody(request).replace("'", "'\\''")).append("'");
        } else {
            sb.append("curl -X POST '").append(resolveCreateUrl()).append("' \\\n");
            sb.append("  -H 'X-Api-Key: ").append(apiKey).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildJsonBody(request, model).replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }

    /** 解析模型名：options 优先，其次 config("model")，最后回退到 {@link #DEFAULT_MODEL}。 */
    private String resolveModel(TtsOptions opts) {
        String model = opts.getModel();
        if (model == null || model.isEmpty()) model = getConfig("model");
        if (model == null || model.isEmpty()) model = DEFAULT_MODEL;
        return model;
    }

    /** 解析音色：options 优先，其次 config("voice")；stream 模型回退到 {@link #DEFAULT_STREAM_VOICE}。 */
    private String resolveVoice(TtsOptions opts, boolean streamModel) {
        String voice = opts.getVoice();
        if (voice == null || voice.isEmpty()) voice = getConfig("voice");
        if ((voice == null || voice.isEmpty()) && streamModel) voice = DEFAULT_STREAM_VOICE;
        return voice;
    }

    /** 语速倍率（1.0 原速）换算为 speech_rate：0.5x → -50，2.0x → 100，线性映射并裁剪到 [-50, 100]。 */
    public static int speedToSpeechRate(double speed) {
        int rate = (int) Math.round((speed - 1.0) * 100);
        return Math.max(-50, Math.min(100, rate));
    }

    /**
     * 构造 tts/create 请求体（seed-audio 系列）。
     */
    public String buildJsonBody(TtsRequest req) {
        return buildJsonBody(req, resolveModel(req.getOptions()));
    }

    private String buildJsonBody(TtsRequest req, String model) {
        TtsOptions opts = req.getOptions();

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("text_prompt", req.getText());

        // speaker / 参考音频 / 参考图片（三者互斥）
        String voice = resolveVoice(opts, false);
        if (opts.getRefAudioUrls() != null && opts.getRefAudioUrls().length > 0) {
            // 参考音频生成模式
            JSONArray refs = new JSONArray();
            for (String url : opts.getRefAudioUrls()) {
                JSONObject ref = new JSONObject();
                ref.put("audio_url", url);
                refs.put(ref);
            }
            if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
                JSONObject imgRef = new JSONObject();
                imgRef.put("image_url", opts.getRefImageUrl());
                refs.put(imgRef);
            }
            body.put("references", refs);
        } else if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
            // 参考图片生成模式
            JSONArray refs = new JSONArray();
            JSONObject imgRef = new JSONObject();
            imgRef.put("image_url", opts.getRefImageUrl());
            refs.put(imgRef);
            body.put("references", refs);
        } else if (voice != null && !voice.isEmpty()) {
            body.put("speaker", voice);
        }

        JSONObject audioConfig = new JSONObject();
        String format = opts.getFormat() == null || opts.getFormat().isEmpty() ? "wav" : opts.getFormat();
        audioConfig.put("format", format);
        if (opts.getSampleRate() != null) {
            audioConfig.put("sample_rate", opts.getSampleRate());
        }
        if (opts.getSpeed() != null) {
            audioConfig.put("speech_rate", speedToSpeechRate(opts.getSpeed()));
        }
        if (opts.getLoudnessRate() != null) {
            audioConfig.put("loudness_rate", opts.getLoudnessRate());
        }
        if (opts.getPitchRate() != null) {
            audioConfig.put("pitch_rate", opts.getPitchRate());
        }
        if (opts.getEnableSubtitle() != null) {
            audioConfig.put("enable_subtitle", opts.getEnableSubtitle());
        }
        body.put("audio_config", audioConfig);
        return body.toString();
    }

    /**
     * 构造 unidirectional 请求体（seed-tts-* 系列）。
     */
    public String buildStreamJsonBody(TtsRequest req) {
        TtsOptions opts = req.getOptions();

        String uid = getConfig("uid");
        JSONObject user = new JSONObject();
        user.put("uid", uid == null || uid.isEmpty() ? "emp-script-ai" : uid);

        JSONObject audioParams = new JSONObject();
        String format = opts.getFormat() == null || opts.getFormat().isEmpty() ? "mp3" : opts.getFormat();
        audioParams.put("format", format);
        audioParams.put("sample_rate", 24000);
        if (opts.getSpeed() != null) {
            audioParams.put("speech_rate", speedToSpeechRate(opts.getSpeed()));
        }

        JSONObject reqParams = new JSONObject();
        reqParams.put("text", req.getText());
        reqParams.put("speaker", resolveVoice(opts, true));
        reqParams.put("audio_params", audioParams);

        JSONObject body = new JSONObject();
        body.put("user", user);
        body.put("req_params", reqParams);
        return body.toString();
    }

    /**
     * 解析 tts/create 响应：{@code audio} 为 base64 音频，{@code url} 为 2 小时有效的音频地址。
     * <p>
     * 成功时响应可能没有 {@code code} 字段（实测），只有出错时才返回非 0 code。
     */
    public TtsResponse parseResponse(JSONObject root, TtsRequest req) {
        int code = root.optInt("code", 0);
        if (code != 0) {
            throw new RuntimeException("Doubao TTS error " + code + ": "
                    + root.optString("message", ""));
        }
        String data = root.optString("audio", "");
        if (data.isEmpty()) {
            throw new RuntimeException("Doubao TTS: response has no audio data");
        }
        String url = root.optString("url", "");
        return new TtsResponse(Base64.getDecoder().decode(data), mimeOf(req.getOptions().getFormat()),
                url.isEmpty() ? null : url, root);
    }

    /**
     * 解析 unidirectional 的 HTTP Chunked 响应（逐行 JSON）：
     * {@code code=0} 且 {@code data} 非空为音频分片（base64，需拼接）；
     * {@code code=20000000} 为合成结束；其余非 0 code 抛异常。
     */
    public TtsResponse parseChunked(String body, TtsRequest req) {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        String lastMessage = null;
        for (String line : body.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("{")) continue;
            JSONObject chunk = new JSONObject(line);
            int code = chunk.optInt("code", 0);
            if (code == 0) {
                String data = chunk.optString("data", "");
                if (!data.isEmpty() && !"null".equals(data)) {
                    byte[] bytes = Base64.getDecoder().decode(data);
                    audio.write(bytes, 0, bytes.length);
                }
            } else if (code == 20000000) {
                break; // 合成结束
            } else {
                throw new RuntimeException("Doubao TTS error " + code + ": "
                        + chunk.optString("message", ""));
            }
        }
        if (audio.size() == 0) {
            throw new RuntimeException("Doubao TTS: stream has no audio data"
                    + (lastMessage == null ? "" : ": " + lastMessage));
        }
        return new TtsResponse(audio.toByteArray(), mimeOf(req.getOptions().getFormat()), null, null);
    }

    /** 音频编码到 MIME 的映射。 */
    static String mimeOf(String encoding) {
        if (encoding == null) return "audio/mpeg";
        return switch (encoding.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg_opus", "ogg" -> "audio/ogg";
            case "pcm" -> "audio/pcm";
            default -> "audio/" + encoding.toLowerCase();
        };
    }
}
