package com.gdxsoft.ai.video.providers.jimeng;

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

/**
 * Jimeng / Seedance (即梦 / 豆包视频生成) via Volcengine Ark.
 * <p>
 * Uses {@code /api/v3/contents/generations/tasks} with multimodal
 * {@code content} array format (text + image_url blocks).
 * <p>
 * Model: {@code doubao-seedance-1-0-pro-250528}
 *
 * @since 1.3.0
 */
public class JimengVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_URL =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";
    public static final String DEFAULT_MODEL = "doubao-seedance-1-0-pro-250528";

    private boolean cameraFixed = false;
    private boolean watermark = true;

    public JimengVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.JIMENG; }

    // === Seedance-specific ===

    /** Camera static (false) or dynamic (true). Default false. */
    public void setCameraFixed(boolean v) { this.cameraFixed = v; }
    public boolean isCameraFixed() { return cameraFixed; }

    /** Add watermark. Default true. */
    public void setWatermark(boolean v) { this.watermark = v; }
    public boolean isWatermark() { return watermark; }

    // === Core ===

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("Jimeng requires an API key (Volcengine Ark)");

        JSONObject body = buildRequestBody(request.getOptions());

        // 1) Submit task
        String resp = postJson(apiUrl, body);
        JSONObject json = new JSONObject(resp);

        // Check for error
        if (json.has("error")) {
            JSONObject err = json.getJSONObject("error");
            throw new IOException("Jimeng error: " + err.optString("message", resp));
        }

        // Extract task ID
        String taskId = json.optString("id", null);
        if (taskId == null) {
            JSONObject data = json.optJSONObject("data");
            if (data != null) taskId = data.optString("task_id", data.optString("id", null));
        }
        if (taskId == null) {
            // Immediate response?
            return parseResponse(json, request.getOptions(), null);
        }

        // 2) Poll — same endpoint with GET + task ID
        String queryUrl = apiUrl + "/" + taskId;
        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            String qResp = getJson(queryUrl);
            JSONObject qJson = new JSONObject(qResp);

            String status = qJson.optString("status",
                    qJson.optJSONObject("data") != null
                            ? qJson.getJSONObject("data").optString("status", "processing")
                            : "processing");

            if ("succeeded".equals(status) || "completed".equals(status)
                    || "success".equals(status)) {
                return parseResponse(qJson, request.getOptions(), taskId);
            }
            if ("failed".equals(status) || "cancelled".equals(status)) {
                JSONObject errInfo = qJson.optJSONObject("error");
                String msg = errInfo != null ? errInfo.optString("message", qResp) : qResp;
                throw new IOException("Jimeng task " + status + ": " + msg);
            }
            LOGGER.debug("Jimeng task {} status: {}", taskId, status);
        }
        throw new IOException("Jimeng task timed out after "
                + (maxPollCount * pollDelayMs / 1000) + "s: " + taskId);
    }

    @Override
    public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n" +
               "  -H 'Authorization: Bearer ****' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    /**
     * Build the content-array request body.
     * <pre>{@code
     * {
     *   "model": "doubao-seedance-1-0-pro-250528",
     *   "content": [
     *     {"type": "text", "text": "prompt --duration 5 --camerafixed false --watermark true"},
     *     {"type": "image_url", "image_url": {"url": "https://..."}}   // optional
     *   ]
     * }
     * }</pre>
     */
    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        JSONArray content = new JSONArray();

        // Text prompt with embedded parameters
        StringBuilder text = new StringBuilder(opts.getPrompt());
        if (opts.getDuration() != null)
            text.append(" --duration ").append(opts.getDuration());
        if (opts.getFps() != null)
            text.append(" --fps ").append(opts.getFps());
        if (opts.getAspectRatio() != null)
            text.append(" --ar ").append(opts.getAspectRatio());
        text.append(" --camerafixed ").append(cameraFixed);
        text.append(" --watermark ").append(watermark);
        if (opts.getNegativePrompt() != null)
            text.append(" --negative \"").append(opts.getNegativePrompt()).append("\"");
        if (opts.getSeed() != null)
            text.append(" --seed ").append(opts.getSeed());

        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", text.toString());
        content.put(textBlock);

        // Image reference (image-to-video)
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
            JSONObject imgBlock = new JSONObject();
            imgBlock.put("type", "image_url");
            JSONObject imgUrl = new JSONObject();
            imgUrl.put("url", opts.getRefImageUrl());
            imgBlock.put("image_url", imgUrl);
            content.put(imgBlock);
        }

        body.put("content", content);
        return body;
    }

    /**
     * Parse the response — looks for video URLs in various possible locations.
     */
    public VideoResponse parseResponse(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();

        // Try multiple common response shapes
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

        // Also check root-level video_url (simple response)
        if (videos.isEmpty()) {
            String directUrl = root.optString("video_url",
                    root.optString("url", null));
            if (directUrl != null && !directUrl.isEmpty()) {
                videos.add(new GeneratedVideo(directUrl, null, null, null));
            }
        }

        return new VideoResponse(videos, taskId, opts.getModel(), null, root);
    }

    /**
     * Search for video results array in common response structures.
     */
    private JSONArray findResults(JSONObject root) {
        // output.results (Qwen-style)
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            JSONArray r = output.optJSONArray("results");
            if (r != null) return r;
        }
        // data.results
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray r = data.optJSONArray("results");
            if (r != null) return r;
            r = data.optJSONArray("videos");
            if (r != null) return r;
        }
        // data (array directly)
        JSONArray r = root.optJSONArray("data");
        if (r != null && !r.isEmpty() && r.get(0) instanceof JSONObject) return r;
        // videos
        r = root.optJSONArray("videos");
        if (r != null) return r;
        // results
        return root.optJSONArray("results");
    }

    // === HTTP ===

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
