package com.gdxsoft.ai.voiceclone.providers.qwen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderBase;
import com.gdxsoft.ai.voiceclone.VoiceCloneProviderType;
import com.gdxsoft.ai.voiceclone.VoiceCloneRequest;
import com.gdxsoft.ai.voiceclone.VoiceCloneResponse;

/**
 * 阿里云通义（DashScope）声音克隆 provider，支持两种模型系列：
 * <ul>
 *   <li><b>Qwen-Audio-TTS / CosyVoice</b>：model={@code voice-enrollment}，
 *       通过 {@code url} 字段传公网可访问音频 URL</li>
 *   <li><b>Qwen-TTS</b>：model={@code qwen-voice-enrollment}，
 *       通过 {@code audio.data} 传 Base64 Data URL 或公网 URL</li>
 * </ul>
 * <p>
 * 必需配置：
 * <ul>
 *   <li>{@code apiKey} — DashScope API Key</li>
 *   <li>{@code workspaceId} — 百炼工作空间 ID（通过 {@link #setConfig}("workspaceId", "...")} 设置）</li>
 * </ul>
 * 可选配置：
 * <ul>
 *   <li>{@code model} — 复刻模型，默认 {@code voice-enrollment}（CosyVoice 系列）</li>
 *   <li>{@code targetModel} — 驱动合成的目标模型，默认 {@code qwen-audio-3.0-tts-flash}</li>
 *   <li>{@code region} — 地域，默认 {@code cn-beijing}</li>
 * </ul>
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/voice-clone-design-http-api
 *
 * @since 1.1.0
 */
public class QwenVoiceCloneProvider extends VoiceCloneProviderBase {

    /** CosyVoice/Qwen-Audio-TTS 复刻模型 */
    public static final String MODEL_VOICE_ENROLLMENT = "voice-enrollment";

    /** Qwen-TTS 复刻模型 */
    public static final String MODEL_QWEN_VOICE_ENROLLMENT = "qwen-voice-enrollment";

    /** 默认目标合成模型 */
    public static final String DEFAULT_TARGET_MODEL = "qwen3-tts-flash";

    /** 默认地域 */
    public static final String DEFAULT_REGION = "cn-beijing";

    public QwenVoiceCloneProvider() {
        // apiUrl 会在 buildApiUrl() 中动态构造
    }

    @Override
    public VoiceCloneProviderType getProviderType() {
        return VoiceCloneProviderType.QWEN;
    }

    @Override
    public VoiceCloneResponse clone(VoiceCloneRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音克隆需要设置 apiKey");
        }

        String body = buildCloneBody(request);
        String url = buildApiUrl();

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("通义声音克隆响应状态: {}", resp.statusCode());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义声音克隆 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseCloneResponse(new JSONObject(resp.body()));
    }

    @Override
    public VoiceCloneResponse query(String voiceId) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音克隆需要设置 apiKey");
        }
        if (voiceId == null || voiceId.isEmpty()) {
            throw new IllegalArgumentException("voiceId 不能为空");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_ENROLLMENT.equals(model);

        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();
        if (isQwenTts) {
            // Qwen-TTS 不支持 query 单个音色详情，用 list 过滤
            input.put("action", "list");
        } else {
            input.put("action", "query_voice");
            input.put("voice_id", voiceId);
        }
        body.put("input", input);

        String url = buildApiUrl();
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("通义音色查询响应状态: {}", resp.statusCode());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义音色查询 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseQueryResponse(new JSONObject(resp.body()), voiceId);
    }

    /**
     * 列出已创建的音色。
     *
     * @param prefix    前缀过滤（可选，null 或空表示不过滤）
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize  每页数量
     * @return 音色列表响应
     */
    public VoiceCloneResponse list(String prefix, int pageIndex, int pageSize)
            throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音克隆需要设置 apiKey");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_ENROLLMENT.equals(model);

        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();
        input.put("action", isQwenTts ? "list" : "list_voice");
        if (prefix != null && !prefix.isEmpty()) {
            input.put("prefix", prefix);
        }
        input.put("page_index", pageIndex);
        input.put("page_size", pageSize);
        body.put("input", input);

        String url = buildApiUrl();
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义音色列表 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JSONObject root = new JSONObject(resp.body());
        VoiceCloneResponse r = new VoiceCloneResponse();
        r.setStatus("success");
        r.setRaw(root);
        return r;
    }

    /**
     * 删除音色。
     *
     * @param voiceId 音色 ID（CosyVoice）或音色名称（Qwen-TTS）
     */
    public VoiceCloneResponse delete(String voiceId) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音克隆需要设置 apiKey");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_ENROLLMENT.equals(model);

        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();
        input.put("action", isQwenTts ? "delete" : "delete_voice");
        input.put(isQwenTts ? "voice" : "voice_id", voiceId);
        body.put("input", input);

        String url = buildApiUrl();
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义音色删除 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return VoiceCloneResponse.success(voiceId, new JSONObject(resp.body()));
    }

    @Override
    public String curl(VoiceCloneRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(buildApiUrl()).append("' \\\n");
            sb.append("  -H 'Authorization: Bearer ****' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildCloneBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (构造请求体失败): " + e.getMessage();
        }
    }

    // ---- 内部方法 ----

    /**
     * 构造 API URL：{@code https://{workspaceId}.{region}.maas.aliyuncs.com/api/v1/services/audio/tts/customization}
     */
    private String buildApiUrl() {
        // 如果用户显式设置了 apiUrl，优先使用
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return apiUrl;
        }
        String workspaceId = getConfig("workspaceId");
        if (workspaceId == null || workspaceId.isEmpty()) {
            throw new IllegalStateException("通义声音克隆需要设置 workspaceId");
        }
        String region = getConfig("region");
        if (region == null || region.isEmpty()) {
            region = DEFAULT_REGION;
        }
        return "https://" + workspaceId + "." + region + ".maas.aliyuncs.com/api/v1/services/audio/tts/customization";
    }

    /** 解析模型：config("model") 或默认 voice-enrollment */
    private String resolveModel() {
        String model = getConfig("model");
        if (model != null && !model.isEmpty()) return model;
        return MODEL_VOICE_ENROLLMENT;
    }

    /** 解析目标模型：config("targetModel") 或默认 */
    private String resolveTargetModel() {
        String targetModel = getConfig("targetModel");
        if (targetModel != null && !targetModel.isEmpty()) return targetModel;
        return DEFAULT_TARGET_MODEL;
    }

    /**
     * 构造克隆请求体。
     */
    private String buildCloneBody(VoiceCloneRequest request) throws IOException {
        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_ENROLLMENT.equals(model);

        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();

        if (isQwenTts) {
            // Qwen-TTS: action=create, audio.data=base64 data URL 或公网 URL
            input.put("action", "create");
            input.put("target_model", resolveTargetModel());

            // preferred_name: 音色名前缀
            String prefix = getConfig("prefix");
            if (prefix == null || prefix.isEmpty()) prefix = "voice";
            input.put("preferred_name", prefix);

            // 音频：优先使用 URL，否则 base64
            AudioSource audio = request.getAudio();
            String audioUrl = getConfig("audioUrl");
            if (audioUrl != null && !audioUrl.isEmpty()) {
                JSONObject audioObj = new JSONObject();
                audioObj.put("data", audioUrl);
                input.put("audio", audioObj);
            } else {
                byte[] bytes = audio.materialize();
                String b64 = Base64.getEncoder().encodeToString(bytes);
                String mimeType = audio.mimeType();
                if (mimeType == null || mimeType.isEmpty()) mimeType = "audio/wav";
                JSONObject audioObj = new JSONObject();
                audioObj.put("data", "data:" + mimeType + ";base64," + b64);
                input.put("audio", audioObj);
            }

            // 可选：音频对应文本
            String text = request.getDemoText();
            if (text != null && !text.isEmpty()) {
                input.put("text", text);
            }

            // 可选：语种
            String language = getConfig("language");
            if (language != null && !language.isEmpty()) {
                input.put("language", language);
            }
        } else {
            // CosyVoice/Qwen-Audio-TTS: action=create_voice, url=公网音频URL
            input.put("action", "create_voice");
            input.put("target_model", resolveTargetModel());

            // prefix: 音色名前缀（仅数字/字母，≤10字符）
            String prefix = getConfig("prefix");
            if (prefix == null || prefix.isEmpty()) prefix = "voice";
            input.put("prefix", prefix);

            // 音频 URL
            String audioUrl = getConfig("audioUrl");
            if (audioUrl != null && !audioUrl.isEmpty()) {
                input.put("url", audioUrl);
            } else {
                // 没有公网 URL，尝试用 audio 的 URL source
                AudioSource audio = request.getAudio();
                if (audio instanceof AudioSource.UrlSource urlSource) {
                    input.put("url", urlSource.url());
                } else {
                    throw new IllegalArgumentException(
                            "CosyVoice 声音克隆需要公网可访问的音频 URL，请通过 setConfig(\"audioUrl\", ...) 设置");
                }
            }

            // 可选：语种提示
            String language = getConfig("language");
            if (language != null && !language.isEmpty()) {
                input.put("language_hints", new JSONArray().put(language));
            }

            // 可选：最大音频时长
            String maxLen = getConfig("maxPromptAudioLength");
            if (maxLen != null && !maxLen.isEmpty()) {
                input.put("max_prompt_audio_length", Double.parseDouble(maxLen));
            }

            // 可选：预处理
            String preprocess = getConfig("enablePreprocess");
            if ("true".equalsIgnoreCase(preprocess)) {
                input.put("enable_preprocess", true);
            }
        }

        body.put("input", input);
        return body.toString();
    }

    /**
     * 解析克隆响应。
     */
    private VoiceCloneResponse parseCloneResponse(JSONObject root) {
        // 检查错误
        String code = root.optString("code", null);
        if (code != null && !code.isEmpty()) {
            return VoiceCloneResponse.error(code + ": " + root.optString("message", ""), root);
        }

        JSONObject output = root.optJSONObject("output");
        if (output == null) {
            return VoiceCloneResponse.error("响应中无 output 字段", root);
        }

        // CosyVoice 返回 voice_id，Qwen-TTS 返回 voice
        String voiceId = output.optString("voice_id", null);
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = output.optString("voice", null);
        }

        if (voiceId == null || voiceId.isEmpty()) {
            return VoiceCloneResponse.error("响应中无 voice_id/voice", root);
        }

        VoiceCloneResponse resp = VoiceCloneResponse.success(voiceId, root);
        resp.setMessage(root.optString("message", null));
        return resp;
    }

    /**
     * 解析查询响应。
     */
    private VoiceCloneResponse parseQueryResponse(JSONObject root, String requestedVoiceId) {
        String code = root.optString("code", null);
        if (code != null && !code.isEmpty()) {
            return VoiceCloneResponse.error(code + ": " + root.optString("message", ""), root);
        }

        JSONObject output = root.optJSONObject("output");
        if (output == null) {
            return VoiceCloneResponse.error("响应中无 output 字段", root);
        }

        // query_voice 直接返回音色详情
        String status = output.optString("status", "OK");
        String targetModel = output.optString("target_model", null);

        VoiceCloneResponse resp = new VoiceCloneResponse(requestedVoiceId, status, root);
        if (targetModel != null) {
            resp.setMessage("target_model=" + targetModel);
        }
        return resp;
    }
}
