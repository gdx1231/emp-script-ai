package com.gdxsoft.ai.video.providers.zai;

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
import com.gdxsoft.ai.video.VideoPromptBuilder;
import com.gdxsoft.ai.video.VideoProviderBase;
import com.gdxsoft.ai.video.VideoProviderType;
import com.gdxsoft.ai.video.VideoRequest;
import com.gdxsoft.ai.video.VideoResponse;
import com.gdxsoft.ai.video.VideoResponse.GeneratedVideo;
import com.gdxsoft.ai.video.VideoTaskStatus;
import com.gdxsoft.ai.video.VideoTaskSubmit;

/**
 * 智谱 AI（ZAI / BigModel）视频生成 provider。
 * <p>
 * 支持以下模型系列：
 * <ul>
 *   <li><b>CogVideoX-3</b>（{@code cogvideox-3}）：文生视频、图生视频、首尾帧生视频</li>
 *   <li><b>CogVideoX</b>（{@code cogvideox-2}、{@code cogvideox-flash}）：文生视频、图生视频</li>
 *   <li><b>Vidu</b>：
 *     {@code viduq1-text}（文生视频）、
 *     {@code viduq1-image}/{@code vidu2-image}（图生视频）、
 *     {@code viduq1-start-end}/{@code vidu2-start-end}（首尾帧生）、
 *     {@code vidu2-reference}（参考生视频）
 *   </li>
 * </ul>
 * <p>
 * 异步流程：
 * <pre>POST {base}/paas/v4/videos/generations → poll GET {base}/paas/v4/async-result/{id}</pre>
 * <p>
 * 提交响应：{@code {id, task_status: "PROCESSING"}}<br>
 * 轮询响应：{@code {task_status: "PROCESSING"|"SUCCESS"|"FAIL", video_result: [{url, cover_image_url}]}}
 *
 * @since 1.3.0
 */
public class ZaiVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_URL = "https://open.bigmodel.cn/api/paas/v4/videos/generations";
    public static final String DEFAULT_MODEL = "cogvideox-3";

    /** 质量模式：speed（速度优先）或 quality（质量优先），仅 CogVideoX 系列有效 */
    private String quality = "speed";

    public ZaiVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.ZAI; }

    public void setQuality(String v) { this.quality = v; }
    public String getQuality() { return quality; }

    // ==================== 模型分类 ====================

    private static boolean isCogVideoX(String model) {
        return model != null && model.toLowerCase().startsWith("cogvideox");
    }

    private static boolean isVidu(String model) {
        return model != null && model.toLowerCase().startsWith("vidu");
    }

    private static boolean isViduFrames(String model) {
        if (model == null) return false;
        String lower = model.toLowerCase();
        return lower.contains("start-end");
    }

    private static boolean isViduReference(String model) {
        if (model == null) return false;
        String lower = model.toLowerCase();
        return lower.contains("reference");
    }

    private static boolean isViduTextOnly(String model) {
        if (model == null) return false;
        String lower = model.toLowerCase();
        return lower.endsWith("-text");
    }

    // ==================== Blocking wrapper ====================

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        VideoTaskSubmit submit = submitTask(request);

        String taskId = submit.getTaskId();
        if (taskId == null) {
            return parseSuccess(submit.getRaw(), request.getOptions(), null);
        }

        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            VideoTaskStatus st = pollTask(taskId, request.getOptions());
            if (st.isSucceeded()) {
                LOGGER.info("ZAI task {} succeeded (poll #{})", taskId, i + 1);
                return st.getResponse();
            }
            if (st.isFailed()) {
                throw new IOException("ZAI task " + st.getStatus() + ": " + st.getError());
            }
            LOGGER.debug("ZAI task {} status: {} (poll #{})", taskId, st.getStatus(), i + 1);
        }
        throw new IOException("ZAI task timed out after "
                + (maxPollCount * pollDelayMs / 1000) + "s: " + taskId);
    }

    // ==================== Non-blocking: submit + poll ====================

    /**
     * 非阻塞：提交视频生成任务。
     * <pre>POST {base}/paas/v4/videos/generations</pre>
     *
     * @param request 视频请求参数
     * @return 提交结果（含 taskId）
     * @throws IOException 网络错误或 API 返回错误
     */
    @Override
    public VideoTaskSubmit submitTask(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("ZAI video requires API Key");

        JSONObject body = buildRequestBody(request.getOptions());
        String respBody = postJson(apiUrl, body);
        JSONObject json = new JSONObject(respBody);

        // 错误检查
        if (json.has("error")) {
            JSONObject err = json.getJSONObject("error");
            throw new IOException("ZAI error: "
                    + err.optString("code", "") + " " + err.optString("message", respBody));
        }

        String taskId = json.optString("id", null);
        if (taskId == null || taskId.isEmpty())
            throw new IOException("ZAI: no id in response: " + respBody);

        String taskStatus = json.optString("task_status", "");
        LOGGER.info("ZAI task created: {} (model: {}, status: {})", taskId,
                json.optString("model", ""), taskStatus);

        // 如果提交后直接成功（罕见）
        if ("SUCCESS".equals(taskStatus)) {
            return new VideoTaskSubmit(null, json);
        }

        return new VideoTaskSubmit(taskId, json);
    }

    /**
     * 非阻塞：查询视频生成任务状态。
     * <pre>GET {base}/paas/v4/async-result/{id}</pre>
     * <p>
     * 任务状态：PROCESSING（处理中）→ SUCCESS（成功）/ FAIL（失败）
     *
     * @param taskId 任务 ID（由 {@link #submitTask(VideoRequest)} 返回）
     * @param opts   原始请求参数
     * @return 任务状态
     */
    @Override
    public VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws IOException, InterruptedException {
        String queryUrl = deriveTaskUrl(taskId, "/api/paas/v4/async-result/");
        String qResp = getJson(queryUrl);
        JSONObject qJson = new JSONObject(qResp);

        // 错误检查
        if (qJson.has("error")) {
            JSONObject err = qJson.getJSONObject("error");
            return new VideoTaskStatus("failed", null,
                    err.optString("code", "") + " " + err.optString("message", qResp), qJson);
        }

        String status = qJson.optString("task_status", "");

        if ("SUCCESS".equals(status)) {
            VideoResponse resp = parseSuccess(qJson, opts, taskId);
            return new VideoTaskStatus("succeeded", resp, null, qJson);
        }

        if ("FAIL".equals(status)) {
            return new VideoTaskStatus("failed", null, "Task failed: " + taskId, qJson);
        }

        // PROCESSING
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

    /** 查询任务状态 curl：GET {base}/paas/v4/async-result/{task_id} */
    @Override
    public String queryCurl(String taskId) {
        if (taskId == null || taskId.isEmpty()) return "";
        return "curl -X GET '" + deriveTaskUrl(taskId, "/api/paas/v4/async-result/")
                + "' \\\n  -H 'Authorization: Bearer " + getApiKey() + "'";
    }

    // ==================== Request Body ====================

    /**
     * 根据模型系列构建不同的请求体。
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        String model = opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL;

        if (isVidu(model)) {
            return buildViduRequestBody(opts, model);
        }
        return buildCogVideoXRequestBody(opts, model);
    }

    /**
     * CogVideoX 系列请求体（cogvideox-3 / cogvideox-2 / cogvideox-flash）。
     * <pre>{@code
     * {
     *   "model": "cogvideox-3",
     *   "prompt": "...",
     *   "image_url": "..." | ["first", "last"],
     *   "quality": "speed",
     *   "with_audio": true,
     *   "size": "1920x1080",
     *   "fps": 30,
     *   "duration": 5,
     *   "watermark_enabled": true
     * }
     * }</pre>
     */
    private JSONObject buildCogVideoXRequestBody(VideoOptions opts, String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);

        // prompt + 长度校验（CogVideoX 上限 512 字符）
        VideoPromptBuilder builder = VideoPromptBuilder.forCogVideoX();
        builder.prompt(opts.getPrompt());
        builder.validateLength();
        String finalPrompt = builder.buildPrompt();
        if (!finalPrompt.isEmpty()) {
            body.put("prompt", finalPrompt);
        }

        // image_url：CogVideoX-3 支持首尾帧（数组），其他仅单图
        if (isCogVideoX3(model)) {
            buildCogVideoX3ImageUrl(opts, body);
        } else {
            // cogvideox-2 / cogvideox-flash：单图
            String imgUrl = firstImageUrl(opts);
            if (imgUrl != null) {
                body.put("image_url", imgUrl);
            }
        }

        // quality（CogVideoX 通用）
        body.put("quality", quality);

        // with_audio
        if (opts.getGenerateAudio() != null) {
            body.put("with_audio", opts.getGenerateAudio());
        }

        // size（分辨率）
        String size = resolveCogVideoXSize(opts, model);
        if (size != null) {
            body.put("size", size);
        }

        // fps
        if (opts.getFps() != null) {
            int fps = opts.getFps();
            // CogVideoX 仅支持 30 / 60
            body.put("fps", fps >= 60 ? 60 : 30);
        }

        // duration（仅 cogvideox-3）
        if (isCogVideoX3(model) && opts.getDuration() != null) {
            int dur = opts.getDuration();
            // cogvideox-3 支持 5 / 10
            body.put("duration", dur > 5 ? 10 : 5);
        }

        // watermark
        Boolean wm = opts.getWatermark();
        if (wm != null) {
            body.put("watermark_enabled", wm);
        }

        return body;
    }

    private static boolean isCogVideoX3(String model) {
        return "cogvideox-3".equalsIgnoreCase(model);
    }

    /**
     * CogVideoX-3 的 image_url 字段：
     * - 首尾帧：[firstFrameUrl, lastFrameUrl] 数组
     * - 单图：字符串
     */
    private void buildCogVideoX3ImageUrl(VideoOptions opts, JSONObject body) {
        String first = opts.getFirstFrameUrl();
        String last = opts.getLastFrameUrl();

        // 首尾帧模式
        if (first != null && !first.isEmpty() && last != null && !last.isEmpty()) {
            JSONArray arr = new JSONArray();
            arr.put(first);
            arr.put(last);
            body.put("image_url", arr);
            return;
        }

        // 单图模式：优先 firstFrameUrl → refImageUrl → refImageUrls[0]
        String imgUrl = firstImageUrl(opts);
        if (imgUrl != null) {
            body.put("image_url", imgUrl);
        }
    }

    /**
     * 取第一张可用的图片 URL。
     * 优先级：firstFrameUrl → refImageUrl → refImageUrls[0]
     */
    private String firstImageUrl(VideoOptions opts) {
        if (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty())
            return opts.getFirstFrameUrl();
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty())
            return opts.getRefImageUrl();
        if (opts.getRefImageUrls() != null && !opts.getRefImageUrls().isEmpty()) {
            for (String url : opts.getRefImageUrls()) {
                if (url != null && !url.isEmpty()) return url;
            }
        }
        return null;
    }

    /**
     * 解析 CogVideoX 分辨率。
     * CogVideoX-3: 1280x720, 720x1280, 1024x1024, 1920x1080, 1080x1920, 2048x1080, 3840x2160
     * CogVideoX:   720x480, 1024x1024, 1280x960, 960x1280, 1920x1080, 1080x1920, 2048x1080, 3840x2160
     */
    private String resolveCogVideoXSize(VideoOptions opts, String model) {
        // 优先使用 resolution（如果是 WxH 格式直接返回）
        if (opts.getResolution() != null && opts.getResolution().contains("x")) {
            return opts.getResolution();
        }

        // 从通用分辨率映射
        String res = opts.getResolution();
        if (res != null) {
            String lower = res.trim().toLowerCase();
            if (lower.contains("4k") || lower.contains("2160")) return "3840x2160";
            if (lower.contains("1080")) return "1920x1080";
            if (lower.contains("720")) return "1280x720";
            if (lower.contains("1024")) return "1024x1024";
        }

        // 从 aspectRatio 映射（默认横屏）
        String ratio = opts.getAspectRatio();
        if (ratio != null) {
            switch (ratio.trim()) {
                case "16:9": return "1920x1080";
                case "9:16": return "1080x1920";
                case "1:1": return "1024x1024";
                default: break;
            }
        }

        return null; // 使用服务端默认
    }

    /**
     * Vidu 系列请求体。
     * <pre>{@code
     * // viduq1-text (T2V)
     * {"model":"viduq1-text","prompt":"...","style":"general","duration":5,
     *  "aspect_ratio":"16:9","size":"1920x1080","movement_amplitude":"auto"}
     *
     * // viduq1-image / vidu2-image (I2V)
     * {"model":"viduq1-image","prompt":"...","image_url":"...",
     *  "duration":5,"size":"1920x1080","movement_amplitude":"auto","with_audio":false}
     *
     * // viduq1-start-end / vidu2-start-end (首尾帧)
     * {"model":"viduq1-start-end","prompt":"...","image_url":["first","last"],
     *  "duration":5,"size":"1920x1080","movement_amplitude":"auto","with_audio":false}
     *
     * // vidu2-reference (参考生视频)
     * {"model":"vidu2-reference","prompt":"...","image_url":["ref1","ref2","ref3"],
     *  "duration":4,"aspect_ratio":"16:9","size":"1280x720","movement_amplitude":"auto","with_audio":false}
     * }</pre>
     */
    private JSONObject buildViduRequestBody(VideoOptions opts, String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);

        // prompt（必填）+ 长度校验（Vidu 上限 512 字符）
        VideoPromptBuilder vBuilder = VideoPromptBuilder.forCogVideoX();
        vBuilder.prompt(opts.getPrompt());
        vBuilder.validateLength();
        String vPrompt = vBuilder.buildPrompt();
        if (!vPrompt.isEmpty()) {
            body.put("prompt", vPrompt);
        }

        // style（仅 viduq1-text）
        if (isViduTextOnly(model)) {
            String style = getConfig("vidu_style");
            body.put("style", style != null ? style : "general");
        }

        // image_url
        buildViduImageUrl(opts, model, body);

        // duration
        if (opts.getDuration() != null) {
            body.put("duration", opts.getDuration());
        }

        // aspect_ratio（viduq1-text / vidu2-reference）
        if (isViduTextOnly(model) || isViduReference(model)) {
            if (opts.getAspectRatio() != null) {
                body.put("aspect_ratio", opts.getAspectRatio());
            }
        }

        // size
        String size = resolveViduSize(opts, model);
        if (size != null) {
            body.put("size", size);
        }

        // movement_amplitude
        String movement = getConfig("vidu_movement_amplitude");
        if (movement != null) {
            body.put("movement_amplitude", movement);
        }

        // with_audio
        if (opts.getGenerateAudio() != null) {
            body.put("with_audio", opts.getGenerateAudio());
        }

        return body;
    }

    /**
     * Vidu 的 image_url 字段：
     * - I2V（viduq1-image / vidu2-image）：单张图，字符串
     * - 首尾帧（*-start-end）：两张图，数组
     * - 参考（vidu2-reference）：1-3 张图，数组
     */
    private void buildViduImageUrl(VideoOptions opts, String model, JSONObject body) {
        if (isViduFrames(model)) {
            // 首尾帧
            List<String> frames = new ArrayList<>();
            if (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty())
                frames.add(opts.getFirstFrameUrl());
            if (opts.getLastFrameUrl() != null && !opts.getLastFrameUrl().isEmpty())
                frames.add(opts.getLastFrameUrl());
            if (!frames.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String url : frames) arr.put(url);
                body.put("image_url", arr);
            }
        } else if (isViduReference(model)) {
            // 参考图（1-3 张）
            List<String> refs = new ArrayList<>();
            if (opts.getRefImageUrls() != null) {
                for (String url : opts.getRefImageUrls()) {
                    if (url != null && !url.isEmpty()) refs.add(url);
                    if (refs.size() >= 3) break;
                }
            }
            if (refs.isEmpty() && opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
                refs.add(opts.getRefImageUrl());
            }
            if (refs.isEmpty()) {
                String first = firstImageUrl(opts);
                if (first != null) refs.add(first);
            }
            if (!refs.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String url : refs) arr.put(url);
                body.put("image_url", arr);
            }
        } else if (!isViduTextOnly(model)) {
            // I2V：单图
            String imgUrl = firstImageUrl(opts);
            if (imgUrl != null) {
                body.put("image_url", imgUrl);
            }
        }
    }

    /**
     * Vidu 分辨率映射。
     * viduq1-text / viduq1-image / viduq1-start-end: 1920x1080
     * vidu2-image / vidu2-start-end / vidu2-reference: 1280x720
     */
    private String resolveViduSize(VideoOptions opts, String model) {
        // 直接指定 WxH
        if (opts.getResolution() != null && opts.getResolution().contains("x")) {
            return opts.getResolution();
        }

        String lower = model.toLowerCase();
        String defaultSize = lower.startsWith("viduq1") ? "1920x1080" : "1280x720";

        if (opts.getResolution() != null) {
            String res = opts.getResolution().trim().toLowerCase();
            if (res.contains("4k") || res.contains("2160")) return "3840x2160";
            if (res.contains("1080")) return "1920x1080";
            if (res.contains("720")) return "1280x720";
        }

        return defaultSize;
    }

    // ==================== Response Parsing ====================

    /**
     * 解析成功响应。
     * <p>
     * 提交响应格式：{@code {id, task_status, model, request_id}}<br>
     * 轮询成功格式：{@code {task_status: "SUCCESS", video_result: [{url, cover_image_url}], model, request_id}}
     */
    public VideoResponse parseSuccess(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();

        JSONArray videoResult = root.optJSONArray("video_result");
        if (videoResult != null) {
            for (int i = 0; i < videoResult.length(); i++) {
                JSONObject item = videoResult.optJSONObject(i);
                if (item == null) continue;
                String url = item.optString("url", null);
                String coverUrl = item.optString("cover_image_url", null);
                if (url != null && !url.isEmpty()) {
                    videos.add(new GeneratedVideo(url, coverUrl, null, null));
                }
            }
        }

        String model = root.optString("model", opts != null ? opts.getModel() : null);
        return new VideoResponse(videos, taskId, model, null, root);
    }

    // ==================== HTTP ====================

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
