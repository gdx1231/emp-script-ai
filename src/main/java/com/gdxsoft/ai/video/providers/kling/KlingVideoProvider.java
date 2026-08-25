package com.gdxsoft.ai.video.providers.kling;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * 可灵（Kling）视频生成 Provider -- Kling 3.0 API（api-beijing.klingai.com）。
 * <p>
 * 按模型名 + 参考素材自动路由到四个端点：
 * <ul>
 *   <li>{@code /text-to-video/kling-3.0} - 文生视频（默认，无图片/视频参考时）</li>
 *   <li>{@code /image-to-video/kling-3.0} - 图生视频：首帧 / 首尾帧 / 主体
 *       （{@link VideoOptions#firstFrameUrl(String)}、{@link VideoOptions#lastFrameUrl(String)}、
 *       {@link VideoOptions#refElementIds(String...)}）</li>
 *   <li>{@code /omni-video/kling-3.0-omni} - Omni 超集端点（model=kling-3.0-omni 显式指定，
 *       或默认模型携带参考图 {@code refImageUrls} / 参考视频 {@code refVideoUrls} /
 *       待编辑视频 {@code baseVideoUrl} 时自动升级）：参考图、特征参考视频、视频编辑、主体</li>
 *   <li>{@code /motion-control/kling-3.0} - 动作控制（model=kling-3.0-motion 显式指定，
 *       或设置 {@link VideoOptions#characterOrientation(String)} 时自动路由）：
 *       形象参考图 + 动作参考视频 + 人物朝向</li>
 * </ul>
 * 素材 id 自动编号：图片 @image_1...、视频 @video_1...、主体 @element_1...，prompt 中通过
 * {@code @id} 引用。
 * <p>
 * 支持音画同出（{@code generateAudio=true} -> audio=native）、保留参考视频原声
 * （{@code keepSourceAudio=true} -> audio=original）、多镜头（{@code multiShot}）、
 * 720p/1080p/4k、时长 3-15s、水印（{@code watermark}）。
 * <p>
 * 任务查询统一走 {@code GET /tasks?task_ids=xxx}，状态枚举 submitted / processing /
 * succeeded / failed。同时实现阻塞 {@link #generate} 与非阻塞
 * {@link #submitTask}/{@link #pollTask}。
 *
 * @since 1.3.0
 */
public class KlingVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_BASE_URL = "https://api-beijing.klingai.com";

    /** 默认模型：文生/图生（按素材自动路由） */
    public static final String DEFAULT_MODEL = "kling-3.0";
    /** Omni 模型：参考图 / 参考视频 / 视频编辑 / 主体 */
    public static final String MODEL_OMNI = "kling-3.0-omni";
    /** 动作控制模型：形象参考图 + 动作参考视频 */
    public static final String MODEL_MOTION = "kling-3.0-motion";

    public static final String PATH_TEXT_TO_VIDEO = "/text-to-video/kling-3.0";
    public static final String PATH_IMAGE_TO_VIDEO = "/image-to-video/kling-3.0";
    public static final String PATH_OMNI_VIDEO = "/omni-video/kling-3.0-omni";
    public static final String PATH_MOTION_CONTROL = "/motion-control/kling-3.0";
    public static final String PATH_TASKS = "/tasks";

    public KlingVideoProvider() { this.apiUrl = DEFAULT_BASE_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.KLING; }

    // ==================== 核心：阻塞 / 非阻塞 ====================

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        VideoTaskSubmit submit = submitTask(request);
        String taskId = submit.getTaskId();
        if (taskId == null || taskId.isEmpty()) {
            throw new IOException("可灵提交响应缺少任务 ID: " + submit.getRaw());
        }

        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            VideoTaskStatus st = pollTask(taskId, request.getOptions());
            if (st.isSucceeded()) return st.getResponse();
            if (st.isFailed()) throw new IOException("可灵视频生成任务失败: " + st.getError());
        }
        throw new IOException("可灵视频任务超时（"
                + (maxPollCount * pollDelayMs / 1000) + "s）: " + taskId);
    }

    /**
     * 非阻塞：提交视频生成任务，立即返回 taskId。
     *
     * @param request 视频请求
     * @return 提交结果（含 taskId）
     */
    @Override
    public VideoTaskSubmit submitTask(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("可灵视频生成需要 API Key");

        VideoOptions opts = request.getOptions();
        String url = baseUrl() + resolveEndpoint(opts);
        String resp = postJson(url, buildRequestBody(opts));

        JSONObject json = new JSONObject(resp);
        int code = json.optInt("code", -1);
        if (code != 0)
            throw new IOException("可灵错误 [" + code + "]: " + json.optString("message"));

        JSONObject data = json.optJSONObject("data");
        if (data == null)
            throw new IOException("可灵提交响应缺少 data: " + resp);

        return new VideoTaskSubmit(data.optString("id", null), json);
    }

    /**
     * 非阻塞：查询视频生成任务状态。
     *
     * @param taskId 任务 ID（由 submitTask 返回）
     * @param opts   原始请求参数（解析结果时用于水印等偏好）
     * @return 任务状态（processing / succeeded / failed）
     */
    @Override
    public VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws IOException, InterruptedException {
        String url = baseUrl() + PATH_TASKS + "?task_ids="
                + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        String resp = getJson(url);
        JSONObject json = new JSONObject(resp);

        int code = json.optInt("code", -1);
        if (code != 0)
            throw new IOException("可灵错误 [" + code + "]: " + json.optString("message"));

        JSONArray data = json.optJSONArray("data");
        if (data == null || data.isEmpty()) {
            return new VideoTaskStatus("failed", null, "任务不存在: " + taskId, json);
        }

        JSONObject task = data.getJSONObject(0);
        String status = task.optString("status", "");
        if ("succeeded".equals(status)) {
            return new VideoTaskStatus("succeeded", parseTask(task, opts, taskId), null, json);
        }
        if ("failed".equals(status)) {
            return new VideoTaskStatus("failed", null,
                    task.optString("message", "unknown"), json);
        }
        // submitted / processing
        LOGGER.debug("可灵任务 {} 状态: {}", taskId, status);
        return new VideoTaskStatus("processing", null, null, json);
    }

    // ==================== 端点路由 ====================

    /**
     * 解析端点路径：按模型名与参考素材自动路由。
     *
     * @param opts 视频请求参数
     * @return 端点路径（如 /text-to-video/kling-3.0）
     */
    public String resolveEndpoint(VideoOptions opts) {
        return resolveEndpoint(opts, resolveModel(opts));
    }

    private String resolveEndpoint(VideoOptions opts, String model) {
        boolean hasFrame = notEmpty(opts.getFirstFrameUrl()) || notEmpty(opts.getLastFrameUrl());
        boolean hasRefImage = !collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl()).isEmpty();
        boolean hasRefVideo = !collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl()).isEmpty();
        boolean hasBaseVideo = notEmpty(opts.getBaseVideoUrl());
        boolean hasElement = opts.getRefElementIds() != null && !opts.getRefElementIds().isEmpty();
        boolean hasOrientation = notEmpty(opts.getCharacterOrientation());

        String endpoint;
        switch (model) {
            case MODEL_OMNI:
                endpoint = PATH_OMNI_VIDEO;
                break;
            case MODEL_MOTION:
            default:
                if (MODEL_MOTION.equals(model) || hasOrientation) {
                    checkMotionSettings(opts);
                    endpoint = PATH_MOTION_CONTROL;
                } else if (hasRefImage || hasRefVideo || hasBaseVideo) {
                    endpoint = PATH_OMNI_VIDEO;
                } else if (hasFrame || hasElement) {
                    endpoint = PATH_IMAGE_TO_VIDEO;
                } else {
                    endpoint = PATH_TEXT_TO_VIDEO;
                }
        }
        validate(endpoint, opts);
        return endpoint;
    }

    /** 归一化模型名：null/空白回退默认模型，未知模型直接报错。 */
    private String resolveModel(VideoOptions opts) {
        String m = opts.getModel();
        if (m == null || m.isBlank()) return DEFAULT_MODEL;
        m = m.trim();
        if (!DEFAULT_MODEL.equals(m) && !MODEL_OMNI.equals(m) && !MODEL_MOTION.equals(m)) {
            throw new IllegalArgumentException("不支持的可灵模型: " + m
                    + "，可选 " + DEFAULT_MODEL + " / " + MODEL_OMNI + " / " + MODEL_MOTION);
        }
        return m;
    }

    /** 端点相关的本地校验（覆盖服务端明确规则，提前失败）。 */
    private void validate(String endpoint, VideoOptions opts) {
        // 仅尾帧不支持（图生视频与 Omni 通用规则）
        boolean lastOnly = notEmpty(opts.getLastFrameUrl()) && !notEmpty(opts.getFirstFrameUrl());
        if (lastOnly && !PATH_MOTION_CONTROL.equals(endpoint)) {
            throw new IllegalArgumentException("可灵不支持仅尾帧图生视频，请同时提供 firstFrameUrl");
        }
        if (opts.getPrompt() == null || opts.getPrompt().isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        List<String> elements = opts.getRefElementIds() == null
                ? List.of() : opts.getRefElementIds();
        if (PATH_IMAGE_TO_VIDEO.equals(endpoint) && elements.size() > 3) {
            throw new IllegalArgumentException("图生视频最多支持 3 个主体，当前 " + elements.size());
        }
        if (PATH_MOTION_CONTROL.equals(endpoint) && elements.size() > 1) {
            throw new IllegalArgumentException("动作控制最多支持 1 个主体，当前 " + elements.size());
        }
    }

    private void checkMotionSettings(VideoOptions opts) {
        String o = opts.getCharacterOrientation();
        if (!"image".equals(o) && !"video".equals(o)) {
            throw new IllegalArgumentException(
                    "动作控制必须设置 characterOrientation（\"image\" 或 \"video\"）");
        }
    }

    // ==================== 请求体构建 ====================

    /**
     * 构造 Kling 3.0 请求体（按端点分派）。
     * <p>
     * 文生视频：{@code {"prompt":..., "settings":{...}, "options":{...}}}；
     * 其余端点：{@code {"contents":[...], "settings":{...}, "options":{...}}}
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        String model = resolveModel(opts);
        String endpoint = resolveEndpoint(opts, model);

        JSONObject body = new JSONObject();
        if (PATH_TEXT_TO_VIDEO.equals(endpoint)) {
            body.put("prompt", opts.getPrompt());
            body.put("settings", buildSettings(opts, true));
        } else if (PATH_IMAGE_TO_VIDEO.equals(endpoint)) {
            body.put("contents", buildImageContents(opts));
            body.put("settings", buildSettings(opts, false));
        } else if (PATH_OMNI_VIDEO.equals(endpoint)) {
            body.put("contents", buildOmniContents(opts));
            body.put("settings", buildSettings(opts, true));
        } else {
            body.put("contents", buildMotionContents(opts));
            body.put("settings", buildMotionSettings(opts));
        }
        applyOptions(body, opts);
        return body;
    }

    /** 通用 settings：multi_shot / audio / resolution / aspect_ratio / duration。 */
    private JSONObject buildSettings(VideoOptions opts, boolean withAspectRatio) {
        JSONObject settings = new JSONObject();
        if (opts.getMultiShot() != null) settings.put("multi_shot", opts.getMultiShot());
        String audio = resolveAudio(opts);
        if (audio != null) settings.put("audio", audio);
        if (opts.getResolution() != null) settings.put("resolution", opts.getResolution());
        if (withAspectRatio && opts.getAspectRatio() != null)
            settings.put("aspect_ratio", opts.getAspectRatio());
        if (opts.getDuration() != null) settings.put("duration", opts.getDuration());
        return settings;
    }

    /** 动作控制 settings：character_orientation（必填）/ audio / resolution。 */
    private JSONObject buildMotionSettings(VideoOptions opts) {
        JSONObject settings = new JSONObject();
        settings.put("character_orientation", opts.getCharacterOrientation());
        String audio = resolveAudio(opts);
        if (audio != null) settings.put("audio", audio);
        if (opts.getResolution() != null) settings.put("resolution", opts.getResolution());
        return settings;
    }

    /** options：watermark_info（仅在显式设置 watermark 时发送）。 */
    private void applyOptions(JSONObject body, VideoOptions opts) {
        if (opts.getWatermark() == null) return;
        JSONObject watermarkInfo = new JSONObject();
        watermarkInfo.put("enabled", opts.getWatermark().booleanValue());
        JSONObject options = new JSONObject();
        options.put("watermark_info", watermarkInfo);
        body.put("options", options);
    }

    /**
     * 音频模式映射：keepSourceAudio -> original（优先）；generateAudio -> native/off；
     * 均未设置时返回 null（省略字段，走服务端默认）。
     */
    private String resolveAudio(VideoOptions opts) {
        if (Boolean.TRUE.equals(opts.getKeepSourceAudio())) return "original";
        if (Boolean.TRUE.equals(opts.getGenerateAudio())) return "native";
        if (Boolean.FALSE.equals(opts.getGenerateAudio())) return "off";
        return null;
    }

    /** 图生视频 contents：prompt + first_frame / last_frame + element（无 id 字段）。 */
    private JSONArray buildImageContents(VideoOptions opts) {
        JSONArray contents = new JSONArray();
        contents.put(promptBlock(opts));
        if (notEmpty(opts.getFirstFrameUrl())) {
            contents.put(urlBlock("first_frame", opts.getFirstFrameUrl(), null));
        }
        if (notEmpty(opts.getLastFrameUrl())) {
            contents.put(urlBlock("last_frame", opts.getLastFrameUrl(), null));
        }
        appendElements(contents, opts.getRefElementIds());
        return contents;
    }

    /**
     * Omni contents：prompt + first_frame / last_frame / refer_image / feature_video /
     * base_video + element。图片/视频/主体自动编号 image_N / video_N / element_N，
     * 供 prompt 中 {@code @id} 引用。
     */
    private JSONArray buildOmniContents(VideoOptions opts) {
        JSONArray contents = new JSONArray();
        contents.put(promptBlock(opts));

        int imageIdx = 0;
        if (notEmpty(opts.getFirstFrameUrl())) {
            contents.put(urlBlock("first_frame", opts.getFirstFrameUrl(), "image_" + (++imageIdx)));
        }
        if (notEmpty(opts.getLastFrameUrl())) {
            contents.put(urlBlock("last_frame", opts.getLastFrameUrl(), "image_" + (++imageIdx)));
        }
        for (String url : collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl())) {
            contents.put(urlBlock("refer_image", url, "image_" + (++imageIdx)));
        }

        int videoIdx = 0;
        for (String url : collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl())) {
            contents.put(urlBlock("feature_video", url, "video_" + (++videoIdx)));
        }
        if (notEmpty(opts.getBaseVideoUrl())) {
            contents.put(urlBlock("base_video", opts.getBaseVideoUrl(), "video_" + (++videoIdx)));
        }

        appendElements(contents, opts.getRefElementIds());
        return contents;
    }

    /** 动作控制 contents：prompt + image（形象参考图）+ video（动作参考视频）+ element。 */
    private JSONArray buildMotionContents(VideoOptions opts) {
        JSONArray contents = new JSONArray();
        contents.put(promptBlock(opts));

        List<String> images = collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl());
        if (!images.isEmpty()) {
            contents.put(urlBlock("image", images.get(0), null));
        }
        List<String> videos = collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl());
        if (!videos.isEmpty()) {
            contents.put(urlBlock("video", videos.get(0), null));
        }

        List<String> elements = opts.getRefElementIds() == null
                ? List.of() : opts.getRefElementIds();
        if (!elements.isEmpty()) {
            contents.put(elementBlock(elements.get(0), "element_1"));
        }
        return contents;
    }

    private JSONObject promptBlock(VideoOptions opts) {
        JSONObject block = new JSONObject();
        block.put("type", "prompt");
        block.put("text", opts.getPrompt());
        return block;
    }

    private JSONObject urlBlock(String type, String url, String id) {
        JSONObject block = new JSONObject();
        block.put("type", type);
        block.put("url", url);
        if (id != null) block.put("id", id);
        return block;
    }

    private void appendElements(JSONArray contents, List<String> elementIds) {
        if (elementIds == null) return;
        int idx = 0;
        for (String elementId : elementIds) {
            if (elementId == null || elementId.isBlank()) continue;
            contents.put(elementBlock(elementId, "element_" + (++idx)));
        }
    }

    private JSONObject elementBlock(String elementId, String id) {
        JSONObject block = new JSONObject();
        block.put("type", "element");
        block.put("element_id", elementId);
        block.put("id", id);
        return block;
    }

    // ==================== 响应解析 ====================

    /**
     * 解析任务对象（/tasks 响应 data[0]，status=succeeded）。
     * <p>
     * outputs 中 type=video 的条目映射为 {@link GeneratedVideo}；
     * watermark=true 时优先取 watermark_url；billing 数组包装进 usage。
     */
    public VideoResponse parseTask(JSONObject task, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();
        boolean preferWatermark = opts != null && Boolean.TRUE.equals(opts.getWatermark());

        JSONArray outputs = task.optJSONArray("outputs");
        if (outputs != null) {
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject o = outputs.getJSONObject(i);
                if (!"video".equals(o.optString("type"))) continue;

                String url = o.optString("url", null);
                String watermarkUrl = o.optString("watermark_url", null);
                if (preferWatermark && watermarkUrl != null && !watermarkUrl.isEmpty()) {
                    url = watermarkUrl;
                }
                if (url == null || url.isEmpty()) continue;

                Double duration = parseDuration(o.optString("duration", null));
                videos.add(new GeneratedVideo(url, null, duration, null));
            }
        }

        JSONObject usage = null;
        JSONArray billing = task.optJSONArray("billing");
        if (billing != null && !billing.isEmpty()) {
            usage = new JSONObject().put("billing", billing);
        }

        return new VideoResponse(videos, taskId,
                opts != null ? opts.getModel() : null, usage, task);
    }

    private Double parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) return null;
        try {
            return Double.parseDouble(duration);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 调试 ====================

    @Override
    public String curl(VideoRequest request) {
        VideoOptions opts = request.getOptions();
        String endpoint = resolveEndpoint(opts);
        JSONObject body = buildRequestBody(opts);
        return "curl -X POST '" + baseUrl() + endpoint + "' \\\n" +
               "  -H 'Authorization: Bearer ****' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'\n" +
               "# Then poll: curl '" + baseUrl() + PATH_TASKS
               + "?task_ids={task_id}' -H 'Authorization: Bearer ****'";
    }

    // ==================== 工具 ====================

    /** apiUrl 作为基础 URL 使用（默认 https://api-beijing.klingai.com），去掉末尾斜杠。 */
    private String baseUrl() {
        String url = apiUrl != null ? apiUrl : DEFAULT_BASE_URL;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    /** 合并列表字段和单个字段：列表优先，单个字段作为 fallback。 */
    private List<String> collectUrls(List<String> list, String single) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isEmpty()) result.add(s);
            }
        }
        if (result.isEmpty() && notEmpty(single)) {
            result.add(single);
        }
        return result;
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
