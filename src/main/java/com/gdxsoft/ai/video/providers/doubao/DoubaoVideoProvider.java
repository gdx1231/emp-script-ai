package com.gdxsoft.ai.video.providers.doubao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.video.VideoOptions;
import com.gdxsoft.ai.video.VideoProviderBase;
import com.gdxsoft.ai.video.VideoProviderType;
import com.gdxsoft.ai.video.VideoRequest;
import com.gdxsoft.ai.video.VideoResponse;
import com.gdxsoft.ai.video.VideoResponse.GeneratedVideo;
import com.gdxsoft.ai.video.VideoTaskStatus;
import com.gdxsoft.ai.video.VideoTaskSubmit;

/**
 * 豆包视频生成（Doubao / Seedance 2.5 / 2.0 / 1.5 / 1.0）via 火山引擎方舟（Volcengine Ark）。
 * <p>
 * 使用 {@code /api/v3/contents/generations/tasks} 接口，
 * multimodal {@code content} 数组格式，支持 text / image_url / video_url / audio_url，
 * 每种内容类型通过 {@code role} 字段标识用途（reference_image / reference_video / reference_audio）。
 * <p>
 * 支持的能力：
 * <ul>
 *   <li>文生视频、图生视频（首帧 / 首尾帧）、多模态参考</li>
 *   <li>视频编辑、视频延长（Seedance 2.5 {@code omni_reference_task_type}）</li>
 *   <li>生成有声视频（generate_audio）</li>
 *   <li>联网搜索（enableWebSearch，仅纯文本输入）</li>
 *   <li>4K 输出（仅 Seedance 2.0，resolution=4k，H.265/HEVC 10bit）</li>
 *   <li>1080p 10bit（Seedance 2.5，H.265/HEVC）</li>
 *   <li>离线推理（service_tier=flex，仅 1.0/1.5）</li>
 *   <li>返回尾帧图（return_last_frame）</li>
 *   <li>输出格式（output_format: mp4/mov，仅 Seedance 2.5）</li>
 *   <li>优先级队列（priority: 0-9，Seedance 2.5/2.0）</li>
 *   <li>任务超时（execution_expires_after，Seedance 2.5/2.0）</li>
 *   <li>多张参考图（Seedance 2.5 最多 30 张，2.0 最多 9 张）、多个参考视频（2.5 最多 10 段，2.0 最多 3 段）、多个参考音频（2.5 最多 10 段，2.0 最多 3 段）</li>
 *   <li>仅音频输入生成视频（仅 Seedance 2.5）</li>
 *   <li>私域素材库（asset:// URI）：通过 {@link com.gdxsoft.ai.video.asset.ArkAssetClient} 管理已认证的真人人像素材，
 *       在 refImageUrls / firstFrameUrl 等字段中传入 {@code asset://<assetId>} 即可引用</li>
 * </ul>
 * <p>
 * 默认模型：{@code doubao-seedance-2-5-260814}
 *
 * @since 1.3.0
 */
public class DoubaoVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_URL =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";
    /** 默认模型：Seedance 2.5（2026-08-14 发布） */
    public static final String DEFAULT_MODEL = "doubao-seedance-2-5-260814";

    private boolean watermark = false;

    public DoubaoVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.DOUBAO; }

    /** 是否添加水印。默认 true。 */
    public void setWatermark(boolean v) { this.watermark = v; }
    public boolean isWatermark() { return watermark; }

    // === 核心 ===

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        VideoTaskSubmit submit = submitTask(request);
        String taskId = submit.getTaskId();
        if (taskId == null) {
            // 无 taskId 说明直接返回了结果
            return parseResponse(submit.getRaw(), request.getOptions(), null);
        }

        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            VideoTaskStatus st = pollTask(taskId, request.getOptions());
            if (st.isSucceeded()) return st.getResponse();
            if (st.isFailed()) throw new IOException("豆包视频生成任务 " + st.getStatus() + ": " + st.getError());
        }
        throw new IOException("豆包视频任务超时（"
                + (maxPollCount * pollDelayMs / 1000) + "s）: " + taskId);
    }

    /**
     * 非阻塞：提交视频生成任务，立即返回 taskId。
     *
     * @param request 视频请求
     * @return 提交结果（含 taskId）
     */
    public VideoTaskSubmit submitTask(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("豆包视频生成需要 API Key（火山引擎方舟）");

        JSONObject body = buildRequestBody(request.getOptions());
        String resp = postJson(apiUrl, body);
        JSONObject json = new JSONObject(resp);

        if (json.has("error")) {
            JSONObject err = json.getJSONObject("error");
            throw new IOException("豆包视频生成错误: " + err.optString("message", resp));
        }

        String taskId = json.optString("id", null);
        if (taskId == null) {
            JSONObject data = json.optJSONObject("data");
            if (data != null) taskId = data.optString("task_id", data.optString("id", null));
        }

        return new VideoTaskSubmit(taskId, json);
    }

    /**
     * 非阻塞：查询视频生成任务状态。
     *
     * @param taskId 任务 ID（由 submitTask 返回）
     * @param opts   原始请求参数（用于解析成功响应）
     * @return 任务状态（processing / succeeded / failed）
     */
    public VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws IOException, InterruptedException {
        String queryUrl = apiUrl + "/" + taskId;
        String qResp = getJson(queryUrl);
        JSONObject qJson = new JSONObject(qResp);

        String status = qJson.optString("status",
                qJson.optJSONObject("data") != null
                        ? qJson.getJSONObject("data").optString("status", "processing")
                        : "processing");

        if ("succeeded".equals(status) || "completed".equals(status) || "success".equals(status)) {
            VideoResponse resp = parseResponse(qJson, opts, taskId);
            return new VideoTaskStatus("succeeded", resp, null, qJson);
        }
        if ("failed".equals(status) || "cancelled".equals(status)) {
            JSONObject errInfo = qJson.optJSONObject("error");
            String msg = errInfo != null ? errInfo.optString("message", qResp) : qResp;
            return new VideoTaskStatus(status, null, msg, qJson);
        }

        LOGGER.debug("豆包视频任务 {} 状态: {}", taskId, status);
        return new VideoTaskStatus("processing", null, null, qJson);
    }

    @Override
    public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n" +
               "  -H 'Authorization: Bearer " + (apiKey != null ? apiKey : "") + "' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    /**
     * 构造 Seedance 2.0 请求体。
     * <pre>{@code
     * {
     *   "model": "doubao-seedance-2-0-260128",
     *   "content": [
     *     {"type": "text", "text": "提示词"},
     *     {"type": "image_url", "image_url": {"url": "..."}, "role": "reference_image"},
     *     {"type": "video_url", "video_url": {"url": "..."}, "role": "reference_video"},
     *     {"type": "audio_url", "audio_url": {"url": "..."}, "role": "reference_audio"}
     *   ],
     *   "duration": 5,
     *   "ratio": "16:9",
     *   "resolution": "720p",
     *   "watermark": true,
     *   "generate_audio": true,
     *   "return_last_frame": true,
     *   "service_tier": "flex",
     *   "tools": [{"type": "web_search"}]
     * }
     * }</pre>
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        // content 数组
        JSONArray content = new JSONArray();

        // 文本提示词
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", opts.getPrompt());
        content.put(textBlock);

        // 严格首帧（first_frame）：视频第一帧与该图一致，优先输出
        if (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty()) {
            content.put(buildContentBlock("image_url", opts.getFirstFrameUrl(), "first_frame"));
        }

        // 严格尾帧（last_frame）：视频最后一帧与该图一致
        if (opts.getLastFrameUrl() != null && !opts.getLastFrameUrl().isEmpty()) {
            content.put(buildContentBlock("image_url", opts.getLastFrameUrl(), "last_frame"));
        }

        // 参考图片（列表 + 单个兼容）
        List<String> imageUrls = collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl());
        for (String url : imageUrls) {
            // 首帧/尾帧 URL 不重复以 reference_image 提交
            if (opts.getFirstFrameUrl() != null && opts.getFirstFrameUrl().equals(url)) {
                continue;
            }
            if (opts.getLastFrameUrl() != null && opts.getLastFrameUrl().equals(url)) {
                continue;
            }
            content.put(buildContentBlock("image_url", url, "reference_image"));
        }

        // 参考视频（列表 + 单个兼容）
        List<String> videoUrls = collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl());
        for (String url : videoUrls) {
            content.put(buildContentBlock("video_url", url, "reference_video"));
        }

        // 参考音频（列表 + 单个兼容）
        List<String> audioUrls = collectUrls(opts.getRefAudioUrls(), opts.getRefAudioUrl());
        for (String url : audioUrls) {
            content.put(buildContentBlock("audio_url", url, "reference_audio"));
        }

        body.put("content", content);

        // 顶层参数
        if (opts.getDuration() != null) body.put("duration", opts.getDuration());
        if (opts.getAspectRatio() != null) body.put("ratio", opts.getAspectRatio());
        if (opts.getResolution() != null) body.put("resolution", opts.getResolution());
        if (opts.getGenerateAudio() != null) body.put("generate_audio", opts.getGenerateAudio());
        // watermark: prefer per-task override on VideoOptions, fall back to
        // the provider instance default. Keeps existing
        // provider.setWatermark() callers working (e.g. legacy tests).
        Boolean wm = opts.getWatermark();
        body.put("watermark", wm != null ? wm.booleanValue() : watermark);
        if (opts.getReturnLastFrame() != null) body.put("return_last_frame", opts.getReturnLastFrame());
        if (opts.getServiceTier() != null) body.put("service_tier", opts.getServiceTier());

        // Seedance 2.5 新增字段
        String model = opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL;
        if (opts.getOutputFormat() != null) body.put("output_format", opts.getOutputFormat());
        if (opts.getOmniReferenceTaskType() != null)
            body.put("omni_reference_task_type", opts.getOmniReferenceTaskType());
        if (opts.getPriority() != null && (isSeedance25(model) || isSeedance20(model)))
            body.put("priority", opts.getPriority());
        if (opts.getExecutionExpiresAfter() != null && (isSeedance25(model) || isSeedance20(model)))
            body.put("execution_expires_after", opts.getExecutionExpiresAfter());
        if (opts.getFrames() != null && isSeedance1x(model))
            body.put("frames", opts.getFrames());

        // 联网搜索工具
        if (Boolean.TRUE.equals(opts.getEnableWebSearch())) {
            JSONArray tools = new JSONArray();
            JSONObject webSearch = new JSONObject();
            webSearch.put("type", "web_search");
            tools.put(webSearch);
            body.put("tools", tools);
        }

        return body;
    }

    /**
     * 构造单个 content block。
     * 外层键与 type 一致：image_url / video_url / audio_url 下挂 {"url": ...}
     * （Seedance 要求 {"type":"image_url","image_url":{"url":"..."},"role":"reference_image"}）
     */
    private JSONObject buildContentBlock(String type, String url, String role) {
        JSONObject block = new JSONObject();
        block.put("type", type);
        JSONObject inner = new JSONObject();
        inner.put("url", url);
        block.put(type, inner);
        block.put("role", role);
        return block;
    }

    /**
     * 合并列表字段和单个字段。列表优先，单个字段作为 fallback。
     */
    private List<String> collectUrls(List<String> list, String single) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isEmpty()) result.add(s);
            }
        }
        if (result.isEmpty() && single != null && !single.isEmpty()) {
            result.add(single);
        }
        return result;
    }

    /**
     * 解析 Seedance 2.0 响应。
     * <p>
     * 成功时响应结构：
     * <pre>{@code
     * {
     *   "id": "task-xxx",
     *   "status": "succeeded",
     *   "content": {"video_url": "...", "last_frame_url": "..."},
     *   "usage": {"completion_tokens": 123},
     *   "created_at": ..., "updated_at": ...
     * }
     * }</pre>
     */
    public VideoResponse parseResponse(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();
        String lastFrameUrl = null;

        // Seedance 2.0: content.video_url / content.last_frame_url
        JSONObject contentObj = root.optJSONObject("content");
        if (contentObj != null) {
            String videoUrl = contentObj.optString("video_url", null);
            if (videoUrl != null && !videoUrl.isEmpty()) {
                videos.add(new GeneratedVideo(videoUrl, null, null, null));
            }
            lastFrameUrl = contentObj.optString("last_frame_url", null);
        }

        // 兼容：data.content.video_url
        if (videos.isEmpty()) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONObject dc = data.optJSONObject("content");
                if (dc != null) {
                    String videoUrl = dc.optString("video_url", null);
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        videos.add(new GeneratedVideo(videoUrl, null, null, null));
                    }
                    if (lastFrameUrl == null) {
                        lastFrameUrl = dc.optString("last_frame_url", null);
                    }
                }
            }
        }

        // 兼容 1.0 格式：搜索 results 数组
        if (videos.isEmpty()) {
            JSONArray results = findResults(root);
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject v = results.getJSONObject(i);
                    String url = v.optString("url", v.optString("video_url", null));
                    if (url != null && !url.isEmpty()) {
                        String cover = v.optString("cover_url",
                                v.optString("thumbnail_url", null));
                        double dur = v.optDouble("duration", 0);
                        String res = v.optString("resolution", null);
                        videos.add(new GeneratedVideo(url, cover,
                                dur > 0 ? dur : null, res));
                    }
                }
            }
        }

        // 兼容：根级 video_url
        if (videos.isEmpty()) {
            String directUrl = root.optString("video_url",
                    root.optString("url", null));
            if (directUrl != null && !directUrl.isEmpty()) {
                videos.add(new GeneratedVideo(directUrl, null, null, null));
            }
        }

        return new VideoResponse(videos, taskId, opts.getModel(), null, root, lastFrameUrl);
    }

    private JSONArray findResults(JSONObject root) {
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            JSONArray r = output.optJSONArray("results");
            if (r != null) return r;
        }
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray r = data.optJSONArray("results");
            if (r != null) return r;
            r = data.optJSONArray("videos");
            if (r != null) return r;
        }
        JSONArray r = root.optJSONArray("data");
        if (r != null && !r.isEmpty() && r.get(0) instanceof JSONObject) return r;
        r = root.optJSONArray("videos");
        if (r != null) return r;
        return root.optJSONArray("results");
    }

    // === HTTP ===

    /** 检测是否为 Seedance 2.5 模型 */
    private boolean isSeedance25(String model) {
        return model != null && model.contains("seedance-2-5");
    }

    /** 检测是否为 Seedance 2.0 系列模型 */
    private boolean isSeedance20(String model) {
        return model != null && model.contains("seedance-2-0");
    }

    /** 检测是否为 Seedance 1.0/1.5 系列模型 */
    private boolean isSeedance1x(String model) {
        return model != null && (model.contains("seedance-1-0") || model.contains("seedance-1-5"));
    }

    private String postJson(String url, JSONObject body) throws IOException, InterruptedException {
        HttpClient c = HttpUtils.createHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2)
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        return resp.body();
    }

    private String getJson(String url) throws IOException, InterruptedException {
        HttpClient c = HttpUtils.createHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey).GET().build();
        HttpResponse<String> resp = c.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2)
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        return resp.body();
    }
}
