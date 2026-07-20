package com.gdxsoft.ai.video.providers.kling;

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
 * Kling (可灵) video generation provider.
 * <p>
 * Creates a text2video task, polls for completion.
 *
 * @since 1.3.0
 */
public class KlingVideoProvider extends VideoProviderBase {
    public static final String CREATE_URL = "https://api.klingai.com/v1/videos/text2video";
    public static final String QUERY_URL = "https://api.klingai.com/v1/videos/text2video/";
    public static final String DEFAULT_MODEL = "kling-v1-6";

    public KlingVideoProvider() { this.apiUrl = CREATE_URL; }

    @Override public VideoProviderType getProviderType() { return VideoProviderType.KLING; }

    @Override
    public VideoResponse generate(VideoRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty())
            throw new IllegalStateException("Kling requires an API key");

        JSONObject body = buildRequestBody(request.getOptions());

        // 1) Submit task
        String resp = postJson(CREATE_URL, body);
        JSONObject json = new JSONObject(resp);
        int code = json.optInt("code", -1);
        if (code != 0) throw new IOException("Kling error [" + code + "]: " + json.optString("message"));

        JSONObject data = json.getJSONObject("data");
        String taskId = data.getString("task_id");

        // 2) Poll
        String queryUrl = QUERY_URL + taskId;
        for (int i = 0; i < maxPollCount; i++) {
            Thread.sleep(pollDelayMs);
            String qResp = getJson(queryUrl);
            JSONObject qJson = new JSONObject(qResp);
            JSONObject qData = qJson.getJSONObject("data");
            String status = qData.optString("task_status", "");

            if ("succeed".equals(status)) {
                return parseSuccess(qData, request.getOptions(), taskId, qJson);
            }
            if ("failed".equals(status)) {
                throw new IOException("Kling task failed: " +
                        qData.optString("task_status_msg", "unknown"));
            }
            LOGGER.debug("Kling task {} status: {}", taskId, status);
        }
        throw new IOException("Kling task timed out: " + taskId);
    }

    @Override
    public String curl(VideoRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        return "curl -X POST '" + CREATE_URL + "' \\\n" +
               "  -H 'Authorization: Bearer ****' \\\n" +
               "  -H 'Content-Type: application/json' \\\n" +
               "  -d '" + body.toString().replace("'", "'\\''") + "'\n" +
               "# Then poll: curl '" + QUERY_URL + "{task_id}' -H 'Authorization: Bearer ****'";
    }

    public JSONObject buildRequestBody(VideoOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model_name", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);
        body.put("prompt", opts.getPrompt());
        if (opts.getNegativePrompt() != null) body.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getDuration() != null) body.put("duration", String.valueOf(opts.getDuration()));
        if (opts.getAspectRatio() != null) body.put("aspect_ratio", opts.getAspectRatio());
        if (opts.getCfgScale() != null) body.put("cfg_scale", opts.getCfgScale().doubleValue());
        if (opts.getCameraMovement() != null) body.put("camera_control", opts.getCameraMovement());
        // Image-to-video
        if (opts.getRefImageUrl() != null) body.put("image_url", opts.getRefImageUrl());
        return body;
    }

    public VideoResponse parseSuccess(JSONObject data, VideoOptions opts,
                                       String taskId, JSONObject raw) {
        List<GeneratedVideo> videos = new ArrayList<>();
        JSONObject result = data.optJSONObject("task_result");
        if (result != null) {
            JSONArray arr = result.optJSONArray("videos");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject v = arr.getJSONObject(i);
                    String url = v.optString("url", null);
                    String cover = v.optString("cover_url", null);
                    double dur = v.optDouble("duration", 0);
                    videos.add(new GeneratedVideo(url, cover, dur > 0 ? dur : null, null));
                }
            }
        }
        return new VideoResponse(videos, taskId, opts.getModel(), null, raw);
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
