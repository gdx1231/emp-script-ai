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

/**
 * Qwen (Tongyi Wanxiang / 通义万相) video generation via DashScope.
 * <p>
 * Follows the same async pattern as Qwen image generation:
 * <ol>
 *   <li>POST with {@code X-DashScope-Async: enable}</li>
 *   <li>Get {@code task_id}, poll {@code GET /api/v1/tasks/{taskId}}</li>
 *   <li>Parse {@code output.results[].url}</li>
 * </ol>
 * <p>
 * Supported models: {@code wanx2.1-t2v-turbo} (text-to-video),
 * {@code wanx2.1-i2v-turbo} (image-to-video).
 *
 * @since 1.3.0
 */
public class QwenVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis";
    public static final String DEFAULT_MODEL = "wanx2.1-t2v-turbo";

    public QwenVideoProvider() { this.apiUrl = DEFAULT_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.QWEN; }

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("Qwen video requires DashScope API Key");

        JSONObject body = buildRequestBody(request.getOptions());

        // 1) Submit async task
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

        // 2) Poll
        String taskUrl = deriveTaskUrl(taskId, "/api/v1/tasks/");
        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            String qResp = getJson(taskUrl);
            JSONObject qJson = new JSONObject(qResp);
            JSONObject qOutput = qJson.optJSONObject("output");

            if (qOutput != null) {
                String status = qOutput.optString("task_status", "");
                if ("SUCCEEDED".equals(status)) {
                    return parseSuccess(qJson, request.getOptions(), taskId);
                }
                if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                    throw new IOException("Qwen video task " + status + ": "
                            + qOutput.optString("message", "unknown"));
                }
                LOGGER.debug("Qwen video task {} status: {}", taskId, status);
            }
        }
        throw new IOException("Qwen video task timed out after "
                + (maxPollCount * pollDelayMs / 1000) + "s: " + taskId);
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

    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        // input block
        JSONObject input = new JSONObject();
        input.put("prompt", opts.getPrompt());
        if (opts.getNegativePrompt() != null)
            input.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty())
            input.put("ref_image", opts.getRefImageUrl());
        body.put("input", input);

        // parameters block
        JSONObject params = new JSONObject();
        if (opts.getDuration() != null) params.put("duration", opts.getDuration());
        if (opts.getResolution() != null) params.put("resolution", opts.getResolution());
        if (opts.getAspectRatio() != null) params.put("aspect_ratio", opts.getAspectRatio());
        if (opts.getFps() != null) params.put("fps", opts.getFps());
        if (opts.getSeed() != null) params.put("seed", opts.getSeed());
        if (opts.getCfgScale() != null) params.put("cfg_scale", opts.getCfgScale());
        if (opts.getCameraMovement() != null) params.put("camera_movement", opts.getCameraMovement());
        body.put("parameters", params);

        return body;
    }

    public VideoResponse parseSuccess(JSONObject root, VideoOptions opts, String taskId) {
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
}
