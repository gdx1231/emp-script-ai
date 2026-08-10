package com.gdxsoft.ai.img.providers.qwen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.ai.img.ImgResponse.GeneratedImage;

/**
 * Qwen Wanx 2.7 image generation/editing provider.
 * <p>
 * Wanx 2.7 ({@code wan2.7-image-pro} / {@code wan2.7-image}) supports:
 * <ul>
 *   <li>文生图（T2I，无图输入）</li>
 *   <li>图像编辑（1-9 张参考图）</li>
 *   <li>交互式编辑（{@code bbox_list} 指定编辑区域）</li>
 *   <li>文生组图 / 图生组图（{@code enable_sequential=true}）</li>
 *   <li>思考模式（{@code thinking_mode}，仅 T2I 无图且非组图）</li>
 *   <li>自定义颜色主题（{@code color_palette}，仅非组图）</li>
 * </ul>
 *
 * <h2>调用模式</h2>
 * <ul>
 *   <li><b>同步（默认）</b>：POST 到
 *       {@code /api/v1/services/aigc/multimodal-generation/generation}，
 *       一次请求返回完整结果。推荐大多数场景使用。</li>
 *   <li><b>异步（可选）</b>：POST 到
 *       {@code /api/v1/services/aigc/image-generation/generation}（带
 *       {@code X-DashScope-Async: enable} 头），返回 {@code task_id}，
 *       轮询 {@code GET /api/v1/tasks/{task_id}}。适用于耗时较长的任务。</li>
 * </ul>
 *
 * <h2>URL</h2>
 * 推荐使用 workspace 专属域名（更高性能）：
 * <ul>
 *   <li>北京：{@code https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/...}</li>
 *   <li>新加坡：{@code https://{WorkspaceId}.ap-southeast-1.maas.aliyuncs.com/api/v1/...}</li>
 * </ul>
 * 不设置 workspace 时回退到 {@code https://dashscope.aliyuncs.com/api/v1/...}。
 *
 * @since 1.2.0
 */
public class QwenImgWanxProvider extends ImgProviderBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenImgWanxProvider.class);

    /** 同步端点路径（无前缀 host）。 */
    private static final String SYNC_PATH =
            "/api/v1/services/aigc/multimodal-generation/generation";
    /** 异步端点路径（无前缀 host）。 */
    private static final String ASYNC_PATH =
            "/api/v1/services/aigc/image-generation/generation";
    /** 异步任务查询路径模板（无前缀 host）。 */
    private static final String ASYNC_TASK_PATH = "/api/v1/tasks/";
    /** 旧版 dashscope 默认 host。 */
    private static final String DEFAULT_DASHSCOPE_HOST = "dashscope.aliyuncs.com";
    private static final String DEFAULT_DASHSCOPE_HOST_INTL = "dashscope-intl.aliyuncs.com";

    public static final String DEFAULT_MODEL = "wan2.7-image-pro";

    private static final int MAX_POLL_COUNT = 60;
    private static final long POLL_DELAY_MS = 2000;

    /**
     * 同步模式开关：默认 true（一次请求返回结果）。
     * 设为 false 时走异步模式：先提交任务，再轮询 task_id。
     */
    private boolean syncMode = true;

    /** Workspace ID。设置时使用专属域名 {@code {WorkspaceId}.{region}.maas.aliyuncs.com}。 */
    private String workspaceId;

    /** API region: "cn-beijing"（默认）或 "ap-southeast-1"。 */
    private String region = "cn-beijing";

    public QwenImgWanxProvider() {
        this.apiUrl = "https://" + DEFAULT_DASHSCOPE_HOST + SYNC_PATH;
    }

    @Override
    public ImgProviderType getProviderType() {
        return ImgProviderType.WANX;
    }

    // ======================== Configuration ========================

    /** Set sync mode (default true). When false, submit an async task and poll. */
    public void setSyncMode(boolean syncMode) { this.syncMode = syncMode; }
    public boolean isSyncMode() { return syncMode; }

    /** Set workspace ID for the dedicated domain. */
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getWorkspaceId() { return workspaceId; }

    /** Set API region: "cn-beijing" (default) or "ap-southeast-1". */
    public void setRegion(String region) { this.region = region; }
    public String getRegion() { return region; }

    // ======================== Generate ========================

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Qwen Wanx 2.7 requires an API key (DashScope API Key)");
        }
        JSONObject body = buildRequestBody(request.getOptions());
        if (syncMode) {
            return generateSync(body, request.getOptions());
        }
        return generateAsync(body, request.getOptions());
    }

    private ImgResponse generateSync(JSONObject body, ImgOptions opts)
            throws IOException, InterruptedException {
        String url = resolveSyncUrl();
        String responseBody = postJson(url, body, false);
        return parseResponse(new JSONObject(responseBody), opts);
    }

    private ImgResponse generateAsync(JSONObject body, ImgOptions opts)
            throws IOException, InterruptedException {
        String submitUrl = resolveAsyncUrl();
        String submitBody = postJson(submitUrl, body, true);
        JSONObject submitJson = new JSONObject(submitBody);

        // 如果同步端点也接受异步头，可能直接返回完整结果
        if (hasChoices(submitJson)) {
            return parseResponse(submitJson, opts);
        }

        JSONObject output = submitJson.optJSONObject("output");
        if (output == null) {
            throw new IOException("Wanx 2.7 async: no output in response: " + submitBody);
        }
        String taskId = output.optString("task_id", null);
        if (taskId == null || taskId.isEmpty()) {
            throw new IOException("Wanx 2.7 async: no task_id in response: " + submitBody);
        }

        String taskUrl = resolveTaskUrl(taskId);
        LOGGER.info("Wanx 2.7 async task submitted, taskId={}, pollUrl={}", taskId, taskUrl);

        for (int i = 0; i < MAX_POLL_COUNT; i++) {
            Thread.sleep(POLL_DELAY_MS);

            HttpClient client = HttpUtils.createHttpClient();
            HttpRequest pollReq = HttpRequest.newBuilder(URI.create(taskUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> pollResp =
                    client.send(pollReq, HttpResponse.BodyHandlers.ofString());
            if (pollResp.statusCode() / 100 != 2) {
                throw new IOException("Wanx 2.7 poll HTTP " + pollResp.statusCode()
                        + ": " + pollResp.body());
            }

            JSONObject pollJson = new JSONObject(pollResp.body());
            String status = pollJson.optJSONObject("output") != null
                    ? pollJson.getJSONObject("output").optString("task_status", "")
                    : "";
            if ("SUCCEEDED".equals(status)) {
                return parseResponse(pollJson, opts);
            }
            if ("FAILED".equals(status) || "CANCELED".equals(status) || "UNKNOWN".equals(status)) {
                String msg = pollJson.getJSONObject("output").optString("message", "");
                throw new IOException("Wanx 2.7 task " + status + ": " + msg);
            }
            // PENDING / RUNNING — 继续轮询
        }
        throw new IOException("Wanx 2.7 async task timed out after "
                + (MAX_POLL_COUNT * POLL_DELAY_MS / 1000) + "s, taskId=" + taskId);
    }

    // ======================== Request body ========================

    /**
     * Build wanx 2.7 request body.
     * <p>
     * Multipart content order: images first, then text. Matches both sync and async shapes.
     *
     * <pre>{@code
     * {
     *   "model": "wan2.7-image-pro",
     *   "input": {
     *     "messages": [{
     *       "role": "user",
     *       "content": [
     *         {"image": "url1"}, {"image": "url2"},
     *         {"text": "prompt"}
     *       ]
     *     }]
     *   },
     *   "parameters": {
     *     "size": "2K",
     *     "n": 1,
     *     "watermark": false,
     *     "thinking_mode": true,
     *     "enable_sequential": false,
     *     "seed": 42
     *   }
     * }
     * }</pre>
     */
    public JSONObject buildRequestBody(ImgOptions opts) {
        String model = opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL;

        JSONObject body = new JSONObject();
        body.put("model", model);

        // ---- input.messages ----
        JSONObject input = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");

        JSONArray content = new JSONArray();

        // 0-9 张参考图（按顺序）
        List<String> refs = resolveRefImages(opts, 9);
        if (refs != null) {
            for (String refUrl : refs) {
                content.put(new JSONObject().put("image", refUrl));
            }
        }
        // 文本
        content.put(new JSONObject().put("text", opts.getPrompt()));

        userMsg.put("content", content);
        messages.put(userMsg);
        input.put("messages", messages);
        body.put("input", input);

        // ---- parameters ----
        JSONObject params = new JSONObject();
        if (opts.getSize() != null && !opts.getSize().isBlank()) {
            params.put("size", normalizeSize(opts.getSize()));
        }
        if (opts.getN() != null && opts.getN() > 0) {
            params.put("n", opts.getN());
        }
        if (opts.getWatermark() != null) {
            params.put("watermark", opts.getWatermark());
        } else {
            params.put("watermark", false);
        }
        if (opts.getThinkingMode() != null) {
            params.put("thinking_mode", opts.getThinkingMode());
        }
        if (opts.getEnableSequential() != null) {
            params.put("enable_sequential", opts.getEnableSequential());
        }
        if (opts.getBboxList() != null && !opts.getBboxList().isEmpty()) {
            params.put("bbox_list", bboxListToJson(opts.getBboxList()));
        }
        if (opts.getColorPalette() != null && !opts.getColorPalette().isEmpty()) {
            params.put("color_palette", new JSONArray(opts.getColorPalette()));
        }
        if (opts.getSeed() != null) {
            long seed = opts.getSeed();
            if (seed < 0 || seed > 2147483647L) {
                throw new IllegalArgumentException(
                        "seed must be in [0, 2147483647], got: " + seed);
            }
            params.put("seed", seed);
        }
        body.put("parameters", params);

        return body;
    }

    /**
     * Normalize size: accept {@code 1K/2K/4K} pass-through, normalize {@code x|X} to {@code *}.
     */
    private static String normalizeSize(String raw) {
        String s = raw.trim();
        // 分辨率模式：仅规范化 K 后缀大小写（"2k" / "2K" / "2k " 都接受），其余字符按数字字面保留
        if (s.length() >= 2 && (s.charAt(s.length() - 1) == 'k' || s.charAt(s.length() - 1) == 'K')) {
            char prefix = s.charAt(0);
            if (prefix >= '0' && prefix <= '9') {
                return s.substring(0, s.length() - 1) + "K";
            }
        }
        String normalized = s.replace("x", "*").replace("X", "*");
        if (!normalized.matches("\\d+\\*\\d+")) {
            throw new IllegalArgumentException(
                    "size must be '1K'/'2K'/'4K' or '<width>*<height>', got: '" + raw + "'");
        }
        return normalized;
    }

    /**
     * Convert {@code List<List<List<Integer>>>} to {@code JSONArray} of bbox arrays.
     */
    private static JSONArray bboxListToJson(List<List<List<Integer>>> bboxList) {
        JSONArray outer = new JSONArray();
        for (List<List<Integer>> imageBboxes : bboxList) {
            JSONArray imgArr = new JSONArray();
            if (imageBboxes != null) {
                for (List<Integer> bbox : imageBboxes) {
                    JSONArray b = new JSONArray();
                    if (bbox != null) {
                        for (Integer v : bbox) {
                            b.put(v);
                        }
                    }
                    imgArr.put(b);
                }
            }
            outer.put(imgArr);
        }
        return outer;
    }

    // ======================== Response parsing ========================

    /**
     * Parse wanx 2.7 response. Works for both sync and async query results.
     * <p>
     * Expected output:
     * <pre>{@code
     * "output": {
     *   "choices": [{
     *     "finish_reason": "stop",
     *     "message": {
     *       "role": "assistant",
     *       "content": [{"image": "https://...png?Expires=...", "type": "image"}, ...]
     *     }
     *   }],
     *   "task_status": "SUCCEEDED",   // only in async responses
     *   "finished": true
     * }
     * }</pre>
     */
    public ImgResponse parseResponse(JSONObject root, ImgOptions opts) {
        // Error at root (sync)
        if (root.has("code") && root.has("message")) {
            String code = root.optString("code", "");
            String message = root.optString("message", "Unknown error");
            throw new RuntimeException("Wanx 2.7 error: [" + code + "] " + message);
        }

        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            String taskStatus = output.optString("task_status", null);
            if (taskStatus != null && !taskStatus.isEmpty()) {
                if ("FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus)
                        || "UNKNOWN".equals(taskStatus)) {
                    String msg = output.optString("message", "Unknown error");
                    String code = output.optString("code", "");
                    throw new RuntimeException(
                            "Wanx 2.7 " + taskStatus + ": [" + code + "] " + msg);
                }
                if ("PENDING".equals(taskStatus) || "RUNNING".equals(taskStatus)) {
                    return new ImgResponse(new ArrayList<>(), null, null,
                            opts.getModel(), null, root);
                }
            }
        }

        String model = root.optString("model", opts.getModel());
        List<GeneratedImage> images = new ArrayList<>();
        if (output != null) {
            JSONArray choices = output.optJSONArray("choices");
            if (choices != null) {
                for (int i = 0; i < choices.length(); i++) {
                    JSONObject choice = choices.getJSONObject(i);
                    JSONObject message = choice.optJSONObject("message");
                    if (message == null) continue;
                    JSONArray cnt = message.optJSONArray("content");
                    if (cnt == null) continue;
                    for (int j = 0; j < cnt.length(); j++) {
                        JSONObject part = cnt.getJSONObject(j);
                        String type = part.optString("type", null);
                        String imgUrl = part.optString("image", null);
                        if ("image".equals(type) && imgUrl != null && !imgUrl.isEmpty()) {
                            images.add(new GeneratedImage(imgUrl, null, null));
                        } else if (imgUrl != null && !imgUrl.isEmpty() && !"text".equals(type)) {
                            // 兼容 type 缺失或非 image/text 的情况
                            images.add(new GeneratedImage(imgUrl, null, null));
                        }
                    }
                }
            }
        }

        JSONObject usage = root.optJSONObject("usage");
        return new ImgResponse(images, null, null, model, usage, root);
    }

    /** Check if response has choices (sync direct result or async query SUCCEEDED). */
    private static boolean hasChoices(JSONObject root) {
        JSONObject output = root.optJSONObject("output");
        if (output == null) return false;
        return output.optJSONArray("choices") != null;
    }

    // ======================== URL resolution ========================

    private String resolveSyncUrl() {
        return resolveHost() + SYNC_PATH;
    }

    private String resolveAsyncUrl() {
        return resolveHost() + ASYNC_PATH;
    }

    private String resolveTaskUrl(String taskId) {
        return resolveHost() + ASYNC_TASK_PATH + taskId;
    }

    /** Build the host portion of the URL, preferring the workspace domain when set. */
    private String resolveHost() {
        // 显式设置 apiUrl 时优先使用（保留历史用法）
        if (apiUrl != null && !apiUrl.isEmpty()
                && !apiUrl.equals("https://" + DEFAULT_DASHSCOPE_HOST + SYNC_PATH)) {
            try {
                URI uri = URI.create(apiUrl);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                int port = uri.getPort();
                if (scheme != null && host != null) {
                    return port > 0 ? scheme + "://" + host + ":" + port : scheme + "://" + host;
                }
            } catch (Exception ignore) { /* fall through */ }
        }
        if (workspaceId != null && !workspaceId.isEmpty()) {
            String r = region != null ? region : "cn-beijing";
            return "https://" + workspaceId + "." + r + ".maas.aliyuncs.com";
        }
        // 默认 host：国际 region 选 dashscope-intl，否则选国内 dashscope
        if ("ap-southeast-1".equalsIgnoreCase(region)) {
            return "https://" + DEFAULT_DASHSCOPE_HOST_INTL;
        }
        return "https://" + DEFAULT_DASHSCOPE_HOST;
    }

    // ======================== HTTP ========================

    private String postJson(String url, JSONObject body, boolean async)
            throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (async) {
            builder.header("X-DashScope-Async", "enable");
        }
        HttpResponse<String> resp =
                client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    // ======================== Helpers ========================

    // ======================== curl (debug) ========================

    @Override
    public String curl(ImgRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        String url = syncMode ? resolveSyncUrl() : resolveAsyncUrl();
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(url).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json'");
        if (!syncMode) {
            sb.append(" \\\n  -H 'X-DashScope-Async: enable'");
        }
        sb.append(" \\\n  -d '")
          .append(body.toString().replace("'", "'\\''"))
          .append("'");
        return sb.toString();
    }
}
