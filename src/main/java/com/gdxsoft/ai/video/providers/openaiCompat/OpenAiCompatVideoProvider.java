package com.gdxsoft.ai.video.providers.openaiCompat;

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
 * OpenAI-compatible video generation (e.g. Sora-compatible proxies).
 * Requires manual apiUrl configuration.
 *
 * @since 1.3.0
 */
public class OpenAiCompatVideoProvider extends VideoProviderBase {
    public static final String DEFAULT_MODEL = "sora-2";

    public OpenAiCompatVideoProvider() {} // caller must setApiUrl

    @Override public VideoProviderType getProviderType() { return VideoProviderType.OPENAI_COMPAT; }

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        if (apiUrl == null || apiUrl.isEmpty())
            throw new IllegalStateException("apiUrl required for OpenAI-compat video");
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("API key required");

        JSONObject body = buildRequestBody(request.getOptions());
        String resp = postJson(body);
        JSONObject json = new JSONObject(resp);

        // Check for async task
        String taskId = json.optString("task_id",
                json.optJSONObject("data") != null
                        ? json.getJSONObject("data").optString("task_id", null) : null);

        if (taskId != null) {
            String queryUrl = deriveTaskUrl(taskId, "/v1/video/tasks/");
            for (int i = 0; i < maxPollCount; i++) {
                Thread.sleep(pollDelayMs);
                String qResp = getJson(queryUrl);
                JSONObject qJson = new JSONObject(qResp);
                String status = qJson.optString("status", "processing");
                if ("completed".equals(status) || "succeeded".equals(status)) {
                    return parseResponse(qJson, request.getOptions(), taskId);
                }
                if ("failed".equals(status))
                    throw new IOException("Task failed: " + qResp);
            }
            throw new IOException("Task timed out: " + taskId);
        }

        return parseResponse(json, request.getOptions(), null);
    }

    @Override public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + apiUrl + "' \\\n" +
               "  -H 'Authorization: Bearer ****' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }

    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);
        body.put("prompt", opts.getPrompt());
        if (opts.getNegativePrompt() != null) body.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getDuration() != null) body.put("duration", opts.getDuration());
        if (opts.getResolution() != null) body.put("resolution", opts.getResolution());
        if (opts.getAspectRatio() != null) body.put("aspect_ratio", opts.getAspectRatio());
        if (opts.getFps() != null) body.put("fps", opts.getFps());
        if (opts.getSeed() != null) body.put("seed", opts.getSeed());
        if (opts.getRefImageUrl() != null) body.put("image_url", opts.getRefImageUrl());
        return body;
    }

    public VideoResponse parseResponse(JSONObject root, VideoOptions opts, String taskId) {
        List<GeneratedVideo> videos = new ArrayList<>();
        JSONArray data = root.optJSONArray("data");
        if (data == null && root.has("output"))
            data = root.getJSONObject("output").optJSONArray("results");
        if (data == null) data = root.optJSONArray("videos");

        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject v = data.getJSONObject(i);
                String url = v.optString("url", null);
                String cover = v.optString("cover_url", v.optString("thumbnail_url", null));
                double dur = v.optDouble("duration", 0);
                videos.add(new GeneratedVideo(url, cover, dur > 0 ? dur : null, null));
            }
        }
        return new VideoResponse(videos, taskId, opts.getModel(), null, root);
    }

    private String postJson(JSONObject body) throws IOException, InterruptedException {
        HttpClient c = HttpUtils.createHttpClient();
        HttpRequest r = HttpRequest.newBuilder(URI.create(apiUrl))
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
