package com.gdxsoft.ai.video.providers.qwen;

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
 * Qwen (Tongyi Wanxiang / 通义万相) video generation via DashScope.
 * <p>
 * Supports three API generations (auto-detected by model prefix):
 * <ul>
 *   <li><b>WAN 3.0 ({@code wan3.0-video})</b> — All-in-one reference video model.
 *       Uses {@code input.media} array (first_frame, last_frame, reference_image,
 *       reference_video, reference_audio, file, link) and {@code parameters.audio}.
 *       file/link 与 reference_xx/first_frame/last_frame 互斥。
 *       Watermark defaults to false. Audio defaults to true.</li>
 *   <li><b>HappyHorse ({@code happyhorse-1.1-t2v}, {@code happyhorse-1.0-t2v},
 *       {@code happyhorse-1.1-i2v}, {@code happyhorse-1.0-i2v})</b> —
 *       T2V: simple prompt-only (no media), supports {@code ratio}.
 *       I2V: requires {@code first_frame} in media, no {@code ratio} (aspect follows image).
 *       Both: watermark defaults to true, duration [3,15], seed [0, 2147483647].
 *       Result: {@code output.video_url}.</li>
 *   <li><b>WAN 2.x ({@code wanx2.1-t2v-turbo}, {@code wanx2.1-i2v-turbo})</b> —
 *       Legacy format with {@code aspect_ratio}, {@code negative_prompt},
 *       {@code ref_image}, {@code fps}, {@code cfg_scale}, {@code camera_movement}.
 *       Result: {@code output.results[].url}.</li>
 * </ul>
 * <p>
 * Both use async pattern:<br>
 * 1. POST with {@code X-DashScope-Async: enable} → 2. Poll GET /api/v1/tasks/{taskId}.
 * <p>
 * WAN 3.0 response includes {@code usage} block (video_count, duration,
 * input_video_duration, output_video_duration, fps, SR, ratio).
 *
 * @since 1.3.0
 */
public class QwenVideoProvider extends VideoProviderBase {
    /** Default endpoint (works for wanx2.x; WAN 3.0 needs workspace-based URL) */
    public static final String DEFAULT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis";

    /** Default model: WAN 3.0 all-in-one video generation */
    public static final String DEFAULT_MODEL = "wan3.0-video";

    /** WAN 3.0 watermark default: false (no watermark). Provider-level default. */
    private boolean watermark = false;

    public QwenVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.QWEN; }

    /** WAN 3.0 watermark default. Per-request override via {@link VideoOptions#setWatermark(Boolean)}. */
    public void setWatermark(boolean v) { this.watermark = v; }
    public boolean isWatermark() { return watermark; }

    // ==================== Core (blocking wrapper) ====================

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        VideoTaskSubmit submit = submitTask(request);

        String taskId = submit.getTaskId();
        if (taskId == null) {
            // No taskId means the response may already contain the result
            return parseSuccess(submit.getRaw(), request.getOptions(), null);
        }

        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            VideoTaskStatus st = pollTask(taskId, request.getOptions());
            if (st.isSucceeded()) {
                LOGGER.info("Qwen video task {} succeeded (poll #{})", taskId, i + 1);
                return st.getResponse();
            }
            if (st.isFailed()) {
                throw new IOException("Qwen video task " + st.getStatus() + ": " + st.getError());
            }
            LOGGER.debug("Qwen video task {} status: {} (poll #{})", taskId, st.getStatus(), i + 1);
        }
        throw new IOException("Qwen video task timed out after "
                + (maxPollCount * pollDelayMs / 1000) + "s: " + taskId);
    }

    // ==================== Non-blocking: submit + poll ====================

    /**
     * 非阻塞：提交视频生成任务，立即返回 taskId。
     * <p>
     * 对应 WAN 3.0 API 的步骤1：
     * <pre>POST /api/v1/services/aigc/video-generation/video-synthesis</pre>
     * 成功响应：
     * <pre>{@code
     * { "output": { "task_status": "PENDING", "task_id": "..." }, "request_id": "..." }
     * }</pre>
     *
     * @param request 视频请求参数
     * @return 提交结果（含 taskId，有效期 24 小时）
     * @throws IOException 网络错误或 API 返回错误
     */
    public VideoTaskSubmit submitTask(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("Qwen video requires DashScope API Key");

        JSONObject body = buildRequestBody(request.getOptions());
        String respBody = postJson(body);
        JSONObject json = new JSONObject(respBody);

        // Check for API-level error
        if (json.has("code") && !"".equals(json.optString("code"))) {
            throw new IOException("Qwen video error [" + json.optString("code")
                    + "]: " + json.optString("message"));
        }

        JSONObject output = json.optJSONObject("output");
        if (output == null)
            throw new IOException("Qwen video: no output in response: " + respBody);

        String taskId = output.optString("task_id", null);
        if (taskId == null || taskId.isEmpty())
            throw new IOException("Qwen video: no task_id: " + respBody);

        LOGGER.info("Qwen video task created: {} (model: {})", taskId,
                request.getOptions().getModel() != null ? request.getOptions().getModel() : DEFAULT_MODEL);

        return new VideoTaskSubmit(taskId, json);
    }

    /**
     * 非阻塞：查询视频生成任务状态。
     * <p>
     * 对应 WAN 3.0 API 的步骤2：
     * <pre>GET /api/v1/tasks/{task_id}</pre>
     * <p>
     * 任务状态流转：PENDING → RUNNING → SUCCEEDED / FAILED / CANCELED。
     * <ul>
     *   <li>task_id 查询有效期：<b>24 小时</b>，超时返回 UNKNOWN</li>
     *   <li>结果视频链接有效期：<b>24 小时</b>，建议及时下载转存</li>
     *   <li>建议轮询间隔：15 秒</li>
     *   <li>查询接口默认 RPS：20</li>
     * </ul>
     * 成功响应：
     * <pre>{@code
     * {
     *   "output": {
     *     "task_id": "...", "task_status": "SUCCEEDED",
     *     "video_url": "https://...", "orig_prompt": "...",
     *     "submit_time": "2026-08-06 10:01:35.452",
     *     "scheduled_time": "2026-08-06 10:01:35.507",
     *     "end_time": "2026-08-06 10:13:33.838"
     *   },
     *   "usage": { "video_count": 1, "duration": 5.0, "fps": 30, "SR": 720, "ratio": "16:9" }
     * }
     * }</pre>
     *
     * @param taskId 任务 ID（由 {@link #submitTask(VideoRequest)} 返回）
     * @param opts   原始请求参数（用于解析成功响应中的模型信息）
     * @return 任务状态（processing / succeeded / failed / unknown）
     * @throws IOException 网络错误
     */
    public VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws IOException, InterruptedException {
        String taskUrl = deriveTaskUrl(taskId, "/api/v1/tasks/");
        String qResp = getJson(taskUrl);
        JSONObject qJson = new JSONObject(qResp);

        JSONObject qOutput = qJson.optJSONObject("output");
        if (qOutput == null) {
            return new VideoTaskStatus("processing", null, null, qJson);
        }

        String status = qOutput.optString("task_status", "");

        // SUCCEEDED
        if ("SUCCEEDED".equals(status)) {
            VideoResponse resp = parseSuccess(qJson, opts, taskId);
            return new VideoTaskStatus("succeeded", resp, null, qJson);
        }

        // FAILED / CANCELED
        if ("FAILED".equals(status) || "CANCELED".equals(status)) {
            String msg = qOutput.optString("message",
                    qOutput.optString("code", "unknown"));
            return new VideoTaskStatus(status.toLowerCase(), null, msg, qJson);
        }

        // UNKNOWN (task_id expired after 24h)
        if ("UNKNOWN".equals(status)) {
            return new VideoTaskStatus("unknown", null,
                    "Task expired or not found (24h limit): " + taskId, qJson);
        }

        // PENDING / RUNNING
        return new VideoTaskStatus("processing", null, null, qJson);
    }

    @Override
    public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n" +
               "  -H 'X-DashScope-Async: enable' \\\n" +
               "  -H 'Authorization: Bearer ****' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    // ==================== Request Body ====================

    /**
     * Build request body, auto-detecting format by model name.
     * <p>
     * Models starting with {@code "wan3"} or {@code "happyhorse"}
     * use the new format (ratio, video_url response).
     * All other models use legacy wanx2.x format
     * (aspect_ratio, negative_prompt, fps, cfg_scale, camera_movement, results[] response).
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        String model = opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL;
        if (isNewFormat(model)) {
            return buildNewFormatBody(opts, model);
        }
        return buildLegacyBody(opts, model);
    }

    // ---- Format detection ----

    private boolean isNewFormat(String model) {
        return model != null && (model.startsWith("wan3") || model.startsWith("happyhorse"));
    }

    private boolean isHappyHorse(String model) {
        return model != null && model.startsWith("happyhorse");
    }

    // ---- New format body (WAN 3.0 / HappyHorse) ----

    /**
     * Build request body for new-format models (WAN 3.0 / HappyHorse T2V / HappyHorse I2V).
     * <pre>{@code
     * // WAN 3.0
     * { "model":"wan3.0-video", "input":{"prompt":"...","media":[...]},
     *   "parameters":{"resolution":"1080P","ratio":"adaptive","duration":5,"audio":true,"seed":123,"watermark":false} }
     *
     * // HappyHorse T2V
     * { "model":"happyhorse-1.1-t2v", "input":{"prompt":"..."},
     *   "parameters":{"resolution":"720P","ratio":"16:9","duration":5,"watermark":true,"seed":123} }
     *
     * // HappyHorse I2V (media: first_frame only, no ratio)
     * { "model":"happyhorse-1.1-i2v", "input":{"prompt":"...","media":[{"type":"first_frame","url":"..."}]},
     *   "parameters":{"resolution":"720P","duration":5,"watermark":true,"seed":123} }
     * }</pre>
     */
    private JSONObject buildNewFormatBody(VideoOptions opts, String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);

        boolean isI2v = model != null && model.contains("i2v");

        // ---- input ----
        JSONObject input = new JSONObject();
        if (opts.getPrompt() != null && !opts.getPrompt().isEmpty()) {
            input.put("prompt", opts.getPrompt());
        }

        // Media: WAN 3.0 always; HappyHorse only i2v (first_frame)
        // HappyHorse t2v has no media support
        if (!isHappyHorse(model) || isI2v) {
            // file/link 与 reference_xx/first_frame/last_frame 互斥校验
            boolean hasFile = opts.getFileUrl() != null && !opts.getFileUrl().isEmpty();
            boolean hasLink = opts.getLinkUrl() != null && !opts.getLinkUrl().isEmpty();
            if (hasFile && hasLink) {
                throw new IllegalArgumentException(
                        "WAN 3.0: file 和 link 不可同时输入");
            }
            if (hasFile || hasLink) {
                boolean hasFrameOrRef = (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty())
                        || (opts.getLastFrameUrl() != null && !opts.getLastFrameUrl().isEmpty())
                        || (opts.getRefImageUrls() != null && !opts.getRefImageUrls().isEmpty())
                        || (opts.getRefVideoUrls() != null && !opts.getRefVideoUrls().isEmpty())
                        || (opts.getRefAudioUrls() != null && !opts.getRefAudioUrls().isEmpty())
                        || (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty())
                        || (opts.getRefVideoUrl() != null && !opts.getRefVideoUrl().isEmpty())
                        || (opts.getRefAudioUrl() != null && !opts.getRefAudioUrl().isEmpty());
                if (hasFrameOrRef) {
                    throw new IllegalArgumentException(
                            "WAN 3.0: file/link 与 reference_xx/first_frame/last_frame 互斥，不可混用");
                }
            }

            // reference 数量上限校验
            int refImageCount = countUrls(opts.getRefImageUrls(), opts.getRefImageUrl());
            if (refImageCount > 10) {
                throw new IllegalArgumentException(
                        "WAN 3.0: reference_image 最多 10 张，当前 " + refImageCount);
            }
            int refVideoCount = countUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl());
            if (refVideoCount > 5) {
                throw new IllegalArgumentException(
                        "WAN 3.0: reference_video 最多 5 段，当前 " + refVideoCount);
            }
            int refAudioCount = countUrls(opts.getRefAudioUrls(), opts.getRefAudioUrl());
            if (refAudioCount > 5) {
                throw new IllegalArgumentException(
                        "WAN 3.0: reference_audio 最多 5 段，当前 " + refAudioCount);
            }

            JSONArray media = buildWan3MediaArray(opts);
            if (!media.isEmpty()) {
                input.put("media", media);
            }
        }
        body.put("input", input);

        // ---- parameters ----
        JSONObject params = new JSONObject();
        if (opts.getResolution() != null) params.put("resolution", wanResolution(opts.getResolution()));

        // ratio: WAN 3.0 + HappyHorse T2V; skip for i2v (aspect follows input image)
        if (opts.getAspectRatio() != null && !isI2v) {
            params.put("ratio", opts.getAspectRatio());
        }

        if (opts.getDuration() != null) {
            int dur = opts.getDuration();
            if (isHappyHorse(model) && (dur < 3 || dur > 15)) {
                LOGGER.warn("HappyHorse duration {} out of range [3,15], sent as-is", dur);
            }
            params.put("duration", dur);
        }

        // audio: WAN 3.0 only, default true (API 文档默认值)
        if (!isHappyHorse(model)) {
            boolean audio = opts.getGenerateAudio() != null ? opts.getGenerateAudio() : true;
            params.put("audio", audio);
        }

        // seed
        if (opts.getSeed() != null) {
            long seed = opts.getSeed();
            if (seed >= 0 && seed <= 2147483647L) {
                params.put("seed", (int) seed);
            } else {
                LOGGER.warn("Qwen seed {} out of range [0, 2147483647], ignored", seed);
            }
        }

        // watermark: HappyHorse default true, WAN 3.0 default false
        Boolean wm = opts.getWatermark();
        params.put("watermark", wm != null ? wm.booleanValue()
                : isHappyHorse(model) ? true : watermark);
        body.put("parameters", params);

        return body;
    }

    /**
     * Build the WAN 3.0 {@code media} array from VideoOptions.
     * Order: first_frame → last_frame → reference_images → reference_videos → reference_audio.
     */
    private JSONArray buildWan3MediaArray(VideoOptions opts) {
        JSONArray media = new JSONArray();

        // first_frame (strict first frame)
        if (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty()) {
            media.put(mediaItem("first_frame", opts.getFirstFrameUrl()));
        }

        // last_frame (strict last frame)
        if (opts.getLastFrameUrl() != null && !opts.getLastFrameUrl().isEmpty()) {
            media.put(mediaItem("last_frame", opts.getLastFrameUrl()));
        }

        // reference_image (list + single fallback, dedupe against first/last)
        List<String> imageUrls = collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl());
        for (String url : imageUrls) {
            if (isDupe(url, opts.getFirstFrameUrl()) || isDupe(url, opts.getLastFrameUrl())) {
                continue;
            }
            media.put(mediaItem("reference_image", url));
        }

        // reference_video
        List<String> videoUrls = collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl());
        for (String url : videoUrls) {
            media.put(mediaItem("reference_video", url));
        }

        // reference_audio
        List<String> audioUrls = collectUrls(opts.getRefAudioUrls(), opts.getRefAudioUrl());
        for (String url : audioUrls) {
            media.put(mediaItem("reference_audio", url));
        }

        // file（文档/文件输入，最多 1 个）
        if (opts.getFileUrl() != null && !opts.getFileUrl().isEmpty()) {
            media.put(mediaItem("file", opts.getFileUrl()));
        }

        // link（网页链接输入，最多 1 个）
        if (opts.getLinkUrl() != null && !opts.getLinkUrl().isEmpty()) {
            media.put(mediaItem("link", opts.getLinkUrl()));
        }

        return media;
    }

    private JSONObject mediaItem(String type, String url) {
        JSONObject m = new JSONObject();
        m.put("type", type);
        m.put("url", url);
        return m;
    }

    // ---- Legacy (wanx2.x) body ----

    /**
     * Build legacy wanx2.x request body (backward compatible).
     */
    private JSONObject buildLegacyBody(VideoOptions opts, String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONObject input = new JSONObject();
        input.put("prompt", opts.getPrompt());
        if (opts.getNegativePrompt() != null)
            input.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty())
            input.put("ref_image", opts.getRefImageUrl());
        body.put("input", input);

        JSONObject params = new JSONObject();
        if (opts.getDuration() != null) params.put("duration", opts.getDuration());
        if (opts.getResolution() != null) params.put("resolution", wanResolution(opts.getResolution()));
        if (opts.getAspectRatio() != null) params.put("aspect_ratio", opts.getAspectRatio());
        if (opts.getFps() != null) params.put("fps", opts.getFps());
        if (opts.getSeed() != null) params.put("seed", opts.getSeed());
        if (opts.getCfgScale() != null) params.put("cfg_scale", opts.getCfgScale());
        if (opts.getCameraMovement() != null) params.put("camera_movement", opts.getCameraMovement());
        body.put("parameters", params);

        return body;
    }

    // ==================== Response Parsing ====================

    /**
     * Parse success response, auto-detecting format by model name.
     */
    public VideoResponse parseSuccess(JSONObject root, VideoOptions opts, String taskId) {
        String model = opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL;
        if (isNewFormat(model)) {
            return parseNewFormatSuccess(root, opts, taskId);
        }
        return parseLegacySuccess(root, opts, taskId);
    }

    /**
     * Parse new-format success response (WAN 3.0 / HappyHorse).
     * <pre>{@code
     * {
     *   "output": {
     *     "task_id": "...", "task_status": "SUCCEEDED",
     *     "video_url": "https://...", "orig_prompt": "...",
     *     "submit_time": "...", "scheduled_time": "...", "end_time": "..."
     *   },
     *   "usage": {
     *     "video_count": 1, "duration": 5.0,
     *     "input_video_duration": 0.0, "output_video_duration": 5.0,
     *     "fps": 30, "SR": 720, "ratio": "16:9"
     *   }
     * }
     * }</pre>
     */
    private VideoResponse parseNewFormatSuccess(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            String videoUrl = output.optString("video_url", null);
            if (videoUrl != null && !videoUrl.isEmpty()) {
                // Use usage-based duration/resolution if available
                Double dur = null;
                String res = null;
                JSONObject usage = root.optJSONObject("usage");
                if (usage != null) {
                    double d = usage.optDouble("duration", 0);
                    if (d > 0) dur = d;
                    int sr = usage.optInt("SR", 0);
                    if (sr > 0) res = sr + "P";
                }
                videos.add(new GeneratedVideo(videoUrl, null, dur, res));
            }
        }

        JSONObject usage = root.optJSONObject("usage");
        return new VideoResponse(videos, taskId, opts.getModel(), usage, root);
    }

    /**
     * Parse legacy (wanx2.x) success response.
     * <pre>{@code
     * { "output": { "results": [{"url": "...", "cover_url": "...", "duration": 5}] } }
     * }</pre>
     */
    private VideoResponse parseLegacySuccess(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            JSONArray results = output.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject v = results.getJSONObject(i);
                    String url = v.optString("url", null);
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

        JSONObject usage = root.optJSONObject("usage");
        return new VideoResponse(videos, taskId, opts.getModel(), usage, root);
    }

    // ==================== Helpers ====================

    /**
     * Merge list field and single fallback field. List takes precedence.
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
     * 统计有效 URL 数量（list + single fallback）。
     */
    private int countUrls(List<String> list, String single) {
        if (list != null) {
            int count = 0;
            for (String s : list) {
                if (s != null && !s.isEmpty()) count++;
            }
            return count;
        }
        return (single != null && !single.isEmpty()) ? 1 : 0;
    }

    /**
     * WAN 系列 API 的 resolution 仅接受大写（'1080P'/'720P'/'480P'），
     * 而项目内调用方（Doubao 习惯）常传小写 '720p'，统一归一化为大写。
     */
    private static String wanResolution(String resolution) {
        return resolution == null ? null : resolution.trim().toUpperCase();
    }

    private boolean isDupe(String url, String existing) {
        return url != null && existing != null && url.equals(existing);
    }

    // ==================== HTTP ====================

    private String postJson(JSONObject body) throws IOException, InterruptedException {
        HttpClient c = HttpUtils.createHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("X-DashScope-Async", "enable")
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

    /** 查询任务状态 curl（创建任务时写入 ai_chat_msg）：GET /api/v1/tasks/{task_id} */
    @Override
    public String queryCurl(String taskId) {
        if (taskId == null || taskId.isEmpty()) return "";
        return "curl -X GET '" + deriveTaskUrl(taskId, "/api/v1/tasks/")
                + "' \\\n  -H 'Authorization: Bearer " + getApiKey() + "'";
    }
}
