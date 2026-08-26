package com.gdxsoft.ai.video.providers.minimax;

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
 * MiniMax H3 (Hailuo-03) video generation V2 API.
 * <p>
 * Uses multimodal {@code content} array format (text / image_url / video_url / audio_url),
 * supporting:
 * <ul>
 *   <li>文生视频 (T2V): text only</li>
 *   <li>图生视频 (I2V): text + first_frame / last_frame</li>
 *   <li>多模态参考生视频 (R2V): text + reference_image / reference_video / reference_audio</li>
 * </ul>
 * <p>
 * Key constraints:
 * <ul>
 *   <li>Text (prompt) is <b>required</b> in all scenarios</li>
 *   <li>first_frame / last_frame and reference_* are <b>mutually exclusive</b></li>
 *   <li>Resolution: {@code 768P}, {@code 2K}</li>
 *   <li>Duration: [4, 15] seconds</li>
 *   <li>Ratio: {@code adaptive}, {@code 21:9}, {@code 16:9}, {@code 4:3}, {@code 1:1}, {@code 3:4}, {@code 9:16}</li>
 * </ul>
 * <p>
 * Async flow:
 * <pre>POST /v2/video_generation → poll GET /v2/query/video_generation/{task_id}</pre>
 * Response: {@code task.content.url} (video URL).
 * Task query window: <b>7 days</b>.
 * <p>
 * Task statuses: queued → running → succeeded / failed / cancelled.
 *
 * @since 1.3.0
 */
public class MiniMaxVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_URL = "https://api.minimaxi.com/v2/video_generation";
    public static final String DEFAULT_MODEL = "MiniMax-H3";

    /** AIGC watermark default: false. Per-request override via VideoOptions. */
    private boolean watermark = false;

    public MiniMaxVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.MINIMAX; }

    public void setWatermark(boolean v) { this.watermark = v; }
    public boolean isWatermark() { return watermark; }

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
                LOGGER.info("MiniMax task {} succeeded (poll #{})", taskId, i + 1);
                return st.getResponse();
            }
            if (st.isFailed()) {
                throw new IOException("MiniMax task " + st.getStatus() + ": " + st.getError());
            }
            LOGGER.debug("MiniMax task {} status: {} (poll #{})", taskId, st.getStatus(), i + 1);
        }
        throw new IOException("MiniMax task timed out after "
                + (maxPollCount * pollDelayMs / 1000) + "s: " + taskId);
    }

    // ==================== Non-blocking: submit + poll ====================

    /**
     * 非阻塞：提交视频生成任务，立即返回 taskId。
     * <pre>POST /v2/video_generation</pre>
     * 成功响应：
     * <pre>{@code {"task_id": "424010985738629"}}</pre>
     *
     * @param request 视频请求参数
     * @return 提交结果（含 taskId）
     * @throws IOException 网络错误或 API 返回错误
     */
    public VideoTaskSubmit submitTask(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("MiniMax video requires API Key");

        JSONObject body = buildRequestBody(request.getOptions());
        String respBody = postJson(apiUrl, body);
        JSONObject json = new JSONObject(respBody);

        // OpenAI-style error
        if ("error".equals(json.optString("type"))) {
            JSONObject err = json.optJSONObject("error");
            throw new IOException("MiniMax error: "
                    + (err != null ? err.optString("message", respBody) : respBody));
        }

        String taskId = json.optString("task_id", null);
        if (taskId == null || taskId.isEmpty())
            throw new IOException("MiniMax: no task_id in response: " + respBody);

        LOGGER.info("MiniMax task created: {} (model: {})", taskId,
                request.getOptions().getModel() != null ? request.getOptions().getModel() : DEFAULT_MODEL);

        return new VideoTaskSubmit(taskId, json);
    }

    /**
     * 非阻塞：查询视频生成任务状态。
     * <pre>GET /v2/query/video_generation/{task_id}</pre>
     * <p>
     * 任务状态流转：queued → running → succeeded / failed / cancelled。
     * <p>
     * 任务查询窗口：最近 <b>7 天</b>，超时返回 invalid task_id。
     * 视频产物下载链接有时效，请及时下载或转存。
     * <p>
     * 成功响应：
     * <pre>{@code
     * {
     *   "task": {
     *     "id": "...", "model": "MiniMax-H3", "status": "succeeded",
     *     "content": {"url": "https://..."},
     *     "resolution": "2K", "duration": 5, "ratio": "16:9",
     *     "usage": {"total_seconds": 5, "input_seconds": 0, "output_seconds": 5, "input_image_count": 0}
     *   }
     * }
     * }</pre>
     * 失败响应：
     * <pre>{@code
     * { "task": { "status": "failed", "error": {"code": "1026", "message": "sensitive content"} } }
     * }</pre>
     *
     * @param taskId 任务 ID（由 {@link #submitTask(VideoRequest)} 返回）
     * @param opts   原始请求参数
     * @return 任务状态
     */
    public VideoTaskStatus pollTask(String taskId, VideoOptions opts) throws IOException, InterruptedException {
        String queryUrl = deriveTaskUrl(taskId, "/v2/query/video_generation/");
        String qResp = getJson(queryUrl);
        JSONObject qJson = new JSONObject(qResp);

        // OpenAI-style error
        if ("error".equals(qJson.optString("type"))) {
            JSONObject err = qJson.optJSONObject("error");
            return new VideoTaskStatus("failed", null,
                    err != null ? err.optString("message", qResp) : qResp, qJson);
        }

        JSONObject task = qJson.optJSONObject("task");
        if (task == null) {
            return new VideoTaskStatus("processing", null, null, qJson);
        }

        String status = task.optString("status", "");

        if ("succeeded".equals(status)) {
            VideoResponse resp = parseSuccess(qJson, opts, taskId);
            return new VideoTaskStatus("succeeded", resp, null, qJson);
        }

        if ("failed".equals(status) || "cancelled".equals(status)) {
            JSONObject taskErr = task.optJSONObject("error");
            String msg = taskErr != null
                    ? taskErr.optString("code", "") + ": " + taskErr.optString("message", "unknown")
                    : "Task " + status + ": " + taskId;
            return new VideoTaskStatus(status, null, msg, qJson);
        }

        // queued / running
        return new VideoTaskStatus("processing", null, null, qJson);
    }

    // ==================== Task List ====================

    /**
     * 分页查询最近 7 天内的任务列表。
     * <pre>GET /v2/query/video_generation?page_num=1&page_size=20&filter.status=succeeded</pre>
     * <p>
     * 响应格式：
     * <pre>{@code
     * { "items": [{ "id":"...", "status":"succeeded", "content":{"url":"..."}, ... }], "total": 476 }
     * }</pre>
     * item 结构与单任务查询的 {@code task} 对象一致。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @param status   按状态过滤（queued/running/succeeded/failed/cancelled），null 不过滤
     * @param taskIds  按任务 ID 列表过滤，null 或空不过滤
     * @param model    按模型过滤，如 "MiniMax-H3"，null 不过滤
     * @param taskType 按类型过滤（generation/h3_context_ir/regeneration），null 不过滤
     * @return 原始响应 JSON（含 items 数组和 total）
     * @throws IOException 网络错误或 API 返回错误
     */
    public JSONObject listTasks(int pageNum, int pageSize,
                                 String status, List<String> taskIds,
                                 String model, String taskType)
            throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("MiniMax video requires API Key");

        URI uri = URI.create(apiUrl);
        String listUrl = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + "/v2/query/video_generation";

        StringBuilder qs = new StringBuilder();
        qs.append("?page_num=").append(pageNum);
        qs.append("&page_size=").append(pageSize);

        if (status != null && !status.isEmpty()) {
            qs.append("&filter.status=").append(encode(status));
        }
        if (taskIds != null) {
            for (String id : taskIds) {
                if (id != null && !id.isEmpty()) {
                    qs.append("&filter.task_ids=").append(encode(id));
                }
            }
        }
        if (model != null && !model.isEmpty()) {
            qs.append("&filter.model=").append(encode(model));
        }
        if (taskType != null && !taskType.isEmpty()) {
            qs.append("&filter.task_type=").append(encode(taskType));
        }

        String qResp = getJson(listUrl + qs);
        JSONObject qJson = new JSONObject(qResp);

        // OpenAI-style error
        if ("error".equals(qJson.optString("type"))) {
            JSONObject err = qJson.optJSONObject("error");
            throw new IOException("MiniMax list tasks error: "
                    + (err != null ? err.optString("message", qResp) : qResp));
        }

        LOGGER.debug("MiniMax task list: page={}/{}, total={}",
                pageNum, pageSize, qJson.optInt("total", -1));
        return qJson;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n" +
               "  -H 'Authorization: Bearer " + (apiKey != null ? apiKey : "") + "' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    // ==================== Request Body ====================

    /**
     * Build MiniMax H3 request body.
     * <pre>{@code
     * // 文生视频
     * {"model":"MiniMax-H3","content":[{"type":"text","text":"..."}],
     *  "resolution":"2K","duration":5,"ratio":"16:9","aigc_watermark":false}
     *
     * // 图生视频（首帧）
     * {"model":"MiniMax-H3","content":[
     *   {"type":"text","text":"..."},
     *   {"type":"image_url","image_url":{"url":"..."},"role":"first_frame"}
     * ],"resolution":"2K","duration":5,"ratio":"adaptive","aigc_watermark":false}
     *
     * // 多模态参考生视频
     * {"model":"MiniMax-H3","content":[
     *   {"type":"text","text":"..."},
     *   {"type":"video_url","video_url":{"url":"..."},"role":"reference_video"},
     *   {"type":"audio_url","audio_url":{"url":"..."},"role":"reference_audio"}
     * ],"resolution":"2K","duration":5,"ratio":"adaptive","aigc_watermark":false}
     * }</pre>
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        // ---- content array ----
        JSONArray content = new JSONArray();

        // Text (required in all scenarios)
        if (opts.getPrompt() != null && !opts.getPrompt().isEmpty()) {
            JSONObject textBlock = new JSONObject();
            textBlock.put("type", "text");
            textBlock.put("text", opts.getPrompt());
            content.put(textBlock);
        }

        // first_frame
        if (opts.getFirstFrameUrl() != null && !opts.getFirstFrameUrl().isEmpty()) {
            content.put(contentBlock("image_url", opts.getFirstFrameUrl(), "first_frame"));
        }

        // last_frame
        if (opts.getLastFrameUrl() != null && !opts.getLastFrameUrl().isEmpty()) {
            content.put(contentBlock("image_url", opts.getLastFrameUrl(), "last_frame"));
        }

        // reference_image (dedupe against first/last)
        List<String> imageUrls = collectUrls(opts.getRefImageUrls(), opts.getRefImageUrl());
        for (String url : imageUrls) {
            if (isDupe(url, opts.getFirstFrameUrl()) || isDupe(url, opts.getLastFrameUrl())) continue;
            content.put(contentBlock("image_url", url, "reference_image"));
        }

        // reference_video
        List<String> videoUrls = collectUrls(opts.getRefVideoUrls(), opts.getRefVideoUrl());
        for (String url : videoUrls) {
            content.put(contentBlock("video_url", url, "reference_video"));
        }

        // reference_audio
        List<String> audioUrls = collectUrls(opts.getRefAudioUrls(), opts.getRefAudioUrl());
        for (String url : audioUrls) {
            content.put(contentBlock("audio_url", url, "reference_audio"));
        }

        body.put("content", content);

        // ---- parameters ----
        if (opts.getResolution() != null) body.put("resolution", opts.getResolution());
        if (opts.getDuration() != null) body.put("duration", opts.getDuration());
        if (opts.getAspectRatio() != null) body.put("ratio", opts.getAspectRatio());

        // watermark: MiniMax H3 default false
        Boolean wm = opts.getWatermark();
        body.put("aigc_watermark", wm != null ? wm.booleanValue() : watermark);

        return body;
    }

    private JSONObject contentBlock(String type, String url, String role) {
        JSONObject block = new JSONObject();
        block.put("type", type);
        JSONObject inner = new JSONObject();
        inner.put("url", url);
        block.put(type, inner);
        if (role != null) block.put("role", role);
        return block;
    }

    // ==================== Response Parsing ====================

    /**
     * Parse MiniMax H3 success response.
     * Video URL at {@code task.content.url}.
     */
    public VideoResponse parseSuccess(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();

        JSONObject task = root.optJSONObject("task");
        if (task != null) {
            JSONObject content = task.optJSONObject("content");
            if (content != null) {
                String videoUrl = content.optString("url", null);
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    double dur = task.optDouble("duration", 0);
                    String res = task.optString("resolution", null);
                    videos.add(new GeneratedVideo(videoUrl, null,
                            dur > 0 ? dur : null, res));
                }
            }
        }

        JSONObject usage = task != null ? task.optJSONObject("usage") : null;
        return new VideoResponse(videos, taskId, opts.getModel(), usage, root);
    }

    // ==================== Helpers ====================

    private List<String> collectUrls(List<String> list, String single) {
        List<String> result = new ArrayList<>();
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isEmpty()) result.add(s);
            }
        }
        if (result.isEmpty() && single != null && !single.isEmpty()) result.add(single);
        return result;
    }

    private boolean isDupe(String url, String existing) {
        return url != null && existing != null && url.equals(existing);
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

    /** 查询任务状态 curl（创建任务时写入 ai_chat_msg）：GET /v2/query/video_generation/{task_id} */
    @Override
    public String queryCurl(String taskId) {
        if (taskId == null || taskId.isEmpty()) return "";
        return "curl -X GET '" + deriveTaskUrl(taskId, "/v2/query/video_generation/")
                + "' \\\n  -H 'Authorization: Bearer " + getApiKey() + "'";
    }
}
