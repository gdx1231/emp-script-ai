package com.gdxsoft.ai.voicedesign.providers.qwen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.voicedesign.VoiceDesignOptions;
import com.gdxsoft.ai.voicedesign.VoiceDesignProviderBase;
import com.gdxsoft.ai.voicedesign.VoiceDesignProviderType;
import com.gdxsoft.ai.voicedesign.VoiceDesignRequest;
import com.gdxsoft.ai.voicedesign.VoiceDesignResponse;

/**
 * 阿里云通义（DashScope）声音设计 provider，支持两种模型系列：
 * <ul>
 *   <li><b>CosyVoice / Qwen-Audio-TTS</b>：model={@code voice-enrollment}，
 *       action={@code create_voice}，走北京 maas 端点（需要 workspaceId）</li>
 *   <li><b>Qwen-TTS</b>：model={@code qwen-voice-design}，
 *       action={@code create}，走 {@code dashscope.aliyuncs.com} 端点</li>
 * </ul>
 * <p>
 * 二者均通过 {@code voice_prompt} 传入声音描述，无需音频样本；
 * 返回音色 ID 与 {@code preview_audio}（Base64 WAV）预览音频。
 * <p>
 * 必需配置：{@code apiKey}。<br>
 * CosyVoice 系列还需：{@code workspaceId}（通过 {@link #setConfig}("workspaceId", "...")} 设置）。<br>
 * 可选配置：{@code model}（声音设计模型，默认 {@code qwen-voice-design}）、
 * {@code targetModel}（目标合成模型，默认 {@code qwen3-tts-vd-2026-01-26}）、
 * {@code prefix}（音色名前缀）、{@code region}（CosyVoice 地域，默认 {@code cn-beijing}）。
 * <p>
 * 文档：https://help.aliyun.com/zh/model-studio/voice-design-user-guide
 *
 * @since 1.1.0
 */
public class QwenVoiceDesignProvider extends VoiceDesignProviderBase {

    /** CosyVoice/Qwen-Audio-TTS 声音设计模型 */
    public static final String MODEL_VOICE_ENROLLMENT = "voice-enrollment";

    /** Qwen-TTS 声音设计模型 */
    public static final String MODEL_QWEN_VOICE_DESIGN = "qwen-voice-design";

    /** 默认目标合成模型（Qwen-TTS） */
    public static final String DEFAULT_TARGET_MODEL = "qwen3-tts-vd-2026-01-26";

    /** CosyVoice 系列默认目标合成模型 */
    public static final String DEFAULT_COSYVOICE_TARGET_MODEL = "cosyvoice-v3.5-plus";

    /** 默认地域 */
    public static final String DEFAULT_REGION = "cn-beijing";

    /** 声音设计接口路径 */
    public static final String PATH = "/api/v1/services/audio/tts/customization";

    /** Qwen-TTS 默认端点 */
    public static final String QWEN_TTS_URL = "https://dashscope.aliyuncs.com" + PATH;

    /** 默认采样率 */
    public static final int DEFAULT_SAMPLE_RATE = 24000;

    /** 默认响应编码格式 */
    public static final String DEFAULT_RESPONSE_FORMAT = "wav";

    public QwenVoiceDesignProvider() {
        // apiUrl 会在 buildApiUrl() 中动态构造
    }

    @Override
    public VoiceDesignProviderType getProviderType() {
        return VoiceDesignProviderType.QWEN;
    }

    @Override
    public VoiceDesignResponse create(VoiceDesignRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音设计需要设置 apiKey");
        }

        String body = buildCreateBody(request);
        HttpResponse<String> resp = post("create", body);
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义声音设计 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return parseCreateResponse(new JSONObject(resp.body()));
    }

    @Override
    public VoiceDesignResponse query(String voiceId) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音设计需要设置 apiKey");
        }
        if (voiceId == null || voiceId.isEmpty()) {
            throw new IllegalArgumentException("voiceId 不能为空");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_DESIGN.equals(model);

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

        HttpResponse<String> resp = post("query", body.toString());
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
    public VoiceDesignResponse list(String prefix, int pageIndex, int pageSize)
            throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音设计需要设置 apiKey");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_DESIGN.equals(model);

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

        HttpResponse<String> resp = post("list", body.toString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义音色列表 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JSONObject root = new JSONObject(resp.body());
        VoiceDesignResponse r = new VoiceDesignResponse();
        r.setStatus("success");
        r.setRaw(root);
        return r;
    }

    /**
     * 删除音色。
     *
     * @param voiceId 音色 ID（CosyVoice）或音色名称（Qwen-TTS）
     */
    public VoiceDesignResponse delete(String voiceId) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("通义声音设计需要设置 apiKey");
        }

        String model = resolveModel();
        boolean isQwenTts = MODEL_QWEN_VOICE_DESIGN.equals(model);

        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();
        input.put("action", isQwenTts ? "delete" : "delete_voice");
        input.put(isQwenTts ? "voice" : "voice_id", voiceId);
        body.put("input", input);

        HttpResponse<String> resp = post("delete", body.toString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("通义音色删除 HTTP " + resp.statusCode() + ": " + resp.body());
        }

        return VoiceDesignResponse.success(voiceId, new JSONObject(resp.body()));
    }

    @Override
    public String curl(VoiceDesignRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(buildApiUrl()).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ").append(apiKey).append("' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(buildCreateBody(request).replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    // ---- 内部方法 ----

    /** 统一 POST 请求，并在发送前记录 curl 调试日志（含真实 apiKey，便于排查）。 */
    private HttpResponse<String> post(String action, String body) throws IOException, InterruptedException {
        if (LOGGER.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(buildApiUrl()).append("' \\\n");
            sb.append("  -H 'Authorization: Bearer ").append(apiKey).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(body.replace("'", "'\\''")).append("'");
            LOGGER.info("通义声音设计[{}]: {}", action, sb);
        }

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(buildApiUrl()))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(httpReq, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 构造 API URL：
     * <ul>
     *   <li>CosyVoice：{@code https://{workspaceId}.{region}.maas.aliyuncs.com/api/v1/services/audio/tts/customization}</li>
     *   <li>Qwen-TTS：{@code https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization}</li>
     * </ul>
     */
    public String buildApiUrl() {
        // 如果用户显式设置了 apiUrl，优先使用
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return apiUrl;
        }
        if (MODEL_VOICE_ENROLLMENT.equals(resolveModel())) {
            // CosyVoice 系列走 maas 端点，需要 workspaceId
            String workspaceId = getConfig("workspaceId");
            if (workspaceId == null || workspaceId.isEmpty()) {
                throw new IllegalStateException("CosyVoice 声音设计需要设置 workspaceId");
            }
            String region = getConfig("region");
            if (region == null || region.isEmpty()) {
                region = DEFAULT_REGION;
            }
            return "https://" + workspaceId + "." + region + ".maas.aliyuncs.com" + PATH;
        }
        return QWEN_TTS_URL;
    }

    /** 解析模型：config("model") 或默认 qwen-voice-design */
    public String resolveModel() {
        String model = getConfig("model");
        if (model != null && !model.isEmpty()) return model;
        return MODEL_QWEN_VOICE_DESIGN;
    }

    /** 是否为 CosyVoice/Qwen-Audio-TTS 声音设计系列。 */
    public boolean isCosyVoice() {
        return MODEL_VOICE_ENROLLMENT.equals(resolveModel());
    }

    /** 解析目标模型：config("targetModel") 或按模型系列回退默认值。 */
    public String resolveTargetModel() {
        return resolveTargetModel(null);
    }

    /** 解析目标模型：options 优先，其次 config("targetModel")，最后按模型系列回退默认值。 */
    public String resolveTargetModel(VoiceDesignOptions opts) {
        String targetModel = opts == null ? null : opts.getTargetModel();
        if (targetModel == null || targetModel.isEmpty()) targetModel = getConfig("targetModel");
        if (targetModel == null || targetModel.isEmpty()) {
            targetModel = isCosyVoice() ? DEFAULT_COSYVOICE_TARGET_MODEL : DEFAULT_TARGET_MODEL;
        }
        return targetModel;
    }

    /**
     * 构造声音设计请求体。
     */
    public String buildCreateBody(VoiceDesignRequest request) {
        VoiceDesignOptions opts = request.getOptions();
        boolean isQwenTts = !isCosyVoice();

        JSONObject body = new JSONObject();
        body.put("model", resolveModel());

        JSONObject input = new JSONObject();
        input.put("action", isQwenTts ? "create" : "create_voice");
        input.put("target_model", resolveTargetModel(opts));
        input.put("voice_prompt", request.getVoicePrompt());

        if (request.getPreviewText() != null && !request.getPreviewText().isEmpty()) {
            input.put("preview_text", request.getPreviewText());
        }

        String prefix = opts.getPrefix();
        if (prefix == null || prefix.isEmpty()) prefix = getConfig("prefix");
        if (prefix == null || prefix.isEmpty()) prefix = isQwenTts ? "custom_voice" : "voice";
        if (isQwenTts) {
            input.put("preferred_name", prefix);
        } else {
            input.put("prefix", prefix);
        }

        // 可选：语种提示（仅 CosyVoice 系列）
        String language = getConfig("language");
        if (!isQwenTts && language != null && !language.isEmpty()) {
            input.put("language_hints", new org.json.JSONArray().put(language));
        }

        body.put("input", input);

        // parameters：采样率与响应格式
        JSONObject parameters = new JSONObject();
        Integer sampleRate = opts.getSampleRate();
        if (sampleRate == null) sampleRate = DEFAULT_SAMPLE_RATE;
        parameters.put("sample_rate", sampleRate);

        String responseFormat = opts.getResponseFormat();
        if (responseFormat == null || responseFormat.isEmpty()) responseFormat = DEFAULT_RESPONSE_FORMAT;
        parameters.put("response_format", responseFormat);
        body.put("parameters", parameters);

        return body.toString();
    }

    /**
     * 解析创建响应。
     */
    public VoiceDesignResponse parseCreateResponse(JSONObject root) {
        String code = root.optString("code", null);
        if (code != null && !code.isEmpty()) {
            return VoiceDesignResponse.error(code + ": " + root.optString("message", ""), root);
        }

        JSONObject output = root.optJSONObject("output");
        if (output == null) {
            return VoiceDesignResponse.error("响应中无 output 字段", root);
        }

        // CosyVoice 返回 voice_id，Qwen-TTS 返回 voice
        String voiceId = output.optString("voice_id", null);
        if (voiceId == null || voiceId.isEmpty()) {
            voiceId = output.optString("voice", null);
        }

        if (voiceId == null || voiceId.isEmpty()) {
            return VoiceDesignResponse.error("响应中无 voice_id/voice", root);
        }

        VoiceDesignResponse resp = VoiceDesignResponse.success(voiceId, root);
        resp.setMessage(root.optString("message", null));

        // 预览音频（Base64 WAV）
        JSONObject preview = output.optJSONObject("preview_audio");
        if (preview != null) {
            String data = preview.optString("data", "");
            if (!data.isEmpty()) {
                resp.setPreviewAudio(Base64.getDecoder().decode(data));
                String format = preview.optString("format", "wav");
                resp.setPreviewMimeType("audio/" + format);
            }
        }
        return resp;
    }

    /**
     * 解析查询响应。
     */
    public VoiceDesignResponse parseQueryResponse(JSONObject root, String requestedVoiceId) {
        String code = root.optString("code", null);
        if (code != null && !code.isEmpty()) {
            return VoiceDesignResponse.error(code + ": " + root.optString("message", ""), root);
        }

        JSONObject output = root.optJSONObject("output");
        if (output == null) {
            return VoiceDesignResponse.error("响应中无 output 字段", root);
        }

        String status = output.optString("status", "OK");
        String targetModel = output.optString("target_model", null);

        VoiceDesignResponse resp = new VoiceDesignResponse(requestedVoiceId, status, root);
        if (targetModel != null) {
            resp.setMessage("target_model=" + targetModel);
        }
        return resp;
    }
}
