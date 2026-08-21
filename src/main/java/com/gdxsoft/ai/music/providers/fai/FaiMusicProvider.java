package com.gdxsoft.ai.music.providers.fai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.music.MusicCoverPreprocessRequest;
import com.gdxsoft.ai.music.MusicCoverPreprocessResponse;
import com.gdxsoft.ai.music.MusicLyricsRequest;
import com.gdxsoft.ai.music.MusicLyricsResponse;
import com.gdxsoft.ai.music.MusicOptions;
import com.gdxsoft.ai.music.MusicProviderBase;
import com.gdxsoft.ai.music.MusicProviderType;
import com.gdxsoft.ai.music.MusicRequest;
import com.gdxsoft.ai.music.MusicResponse;
import com.gdxsoft.ai.music.MusicTaskStatus;
import com.gdxsoft.ai.music.MusicTaskSubmit;

/**
 * fal.ai MiniMax Music 3 音乐生成 Provider。
 * <p>
 * 端点：{@code https://fal.run/minimax/music-3}；鉴权：{@code Authorization: Key <FAL_KEY>}。
 * 输入需 {@code prompt}（风格/情绪描述）与 {@code lyrics}（带段落标签的歌词），
 * 可选 {@code duration}、{@code seed}、{@code num_inference_steps}、{@code guidance_scale}。
 * 输出为 {@code audio.url}（音频文件地址），{@code seed} 与 {@code duration}。
 * <p>
 * 支持两种调用方式：{@link #generate(MusicRequest)} 走 fal.ai 直跑端点（同步阻塞）；
 * {@link #submitTask(MusicRequest)} / {@link #pollTask(String, MusicOptions)} 走 fal.ai 队列端点
 * （原生服务端异步，返回 {@code request_id} 后轮询），与视频模块的处理模式一致。
 */
public class FaiMusicProvider extends MusicProviderBase {
    public static final String DEFAULT_URL = "https://fal.run/minimax/music-3";
    public static final String FAL_HOST = "https://fal.run/";
    public static final String QUEUE_HOST = "https://queue.fal.run/";
    public static final String DEFAULT_ENDPOINT = "minimax/music-3";

    public FaiMusicProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public MusicProviderType getProviderType() { return MusicProviderType.FAI; }

    @Override
    public MusicResponse generate(MusicRequest request) throws IOException, InterruptedException {
        JSONObject body = buildRequestBody(request);
        return parseResponse(postJson(runUrl(request.getOptions()), body));
    }

    /**
     * 原生异步提交：调用 fal.ai 队列端点，立即返回服务端 {@code request_id}。
     * <p>覆盖 {@link MusicProviderBase} 的本地异步回退，获得真正的服务端异步能力。
     */
    @Override
    public MusicTaskSubmit submitTask(MusicRequest request) throws IOException, InterruptedException {
        JSONObject body = buildRequestBody(request);
        String url = queueUrl(request.getOptions());
        JSONObject resp = postJson(url, body);
        String requestId = resp.optString("request_id", null);
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalStateException("fal.ai music-3 submit returned no request_id: " + resp);
        }
        return new MusicTaskSubmit(requestId, resp);
    }

    /**
     * 原生异步轮询：先查队列状态，完成后再取结果。
     * <p>{@code IN_QUEUE}/{@code IN_PROGRESS} → processing；{@code COMPLETED} → 取结果；
     * {@code FAILED} → failed。
     */
    @Override
    public MusicTaskStatus pollTask(String taskId, MusicOptions opts) throws IOException, InterruptedException {
        String endpoint = endpointId(opts);
        String statusUrl = QUEUE_HOST + endpoint + "/requests/" + taskId + "/status";
        JSONObject statusJson = getJson(statusUrl);
        String status = statusJson.optString("status", "");
        if ("COMPLETED".equals(status) || "COMPLETE".equals(status)) {
            String resultUrl = QUEUE_HOST + endpoint + "/requests/" + taskId;
            JSONObject result = getJson(resultUrl);
            return mapResult(result);
        }
        if ("FAILED".equals(status) || "CANCELLED".equals(status) || "CANCELED".equals(status)) {
            String msg = statusJson.has("detail")
                    ? statusJson.get("detail").toString()
                    : ("fal.ai music-3 task " + status + ": " + taskId);
            return new MusicTaskStatus("failed", null, msg, statusJson);
        }
        return new MusicTaskStatus("processing", null, null, statusJson);
    }

    /** 由 fal.ai 队列结果 JSON 映射为统一的任务状态。 */
    public MusicTaskStatus mapResult(JSONObject result) {
        if (result.has("detail")) {
            return new MusicTaskStatus("failed", null,
                    "fal.ai music-3 error: " + result.get("detail"), result);
        }
        try {
            return new MusicTaskStatus("succeeded", parseResponse(result), null, result);
        } catch (RuntimeException e) {
            return new MusicTaskStatus("failed", null, e.getMessage(), result);
        }
    }

    @Override
    public String curl(MusicRequest request) {
        JSONObject body = buildRequestBody(request);
        return curl(runUrl(request.getOptions()), body);
    }

    @Override
    public MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request) {
        throw new UnsupportedOperationException(
                "fal.ai MiniMax Music 3 不支持翻唱前处理（music_cover_preprocess）");
    }

    @Override
    public String curl(MusicCoverPreprocessRequest request) {
        throw new UnsupportedOperationException(
                "fal.ai MiniMax Music 3 不支持翻唱前处理（music_cover_preprocess）");
    }

    @Override
    public MusicLyricsResponse generateLyrics(MusicLyricsRequest request) {
        throw new UnsupportedOperationException(
                "fal.ai MiniMax Music 3 不支持独立歌词生成（lyrics_generation）");
    }

    @Override
    public String curl(MusicLyricsRequest request) {
        throw new UnsupportedOperationException(
                "fal.ai MiniMax Music 3 不支持独立歌词生成（lyrics_generation）");
    }

    /** 构造 API 请求体，公开给单元测试复用。 */
    public JSONObject buildRequestBody(MusicRequest request) {
        MusicOptions opts = request.getOptions();
        if (isBlank(request.getPrompt())) {
            throw new IllegalArgumentException("prompt is required");
        }
        if (isBlank(opts.getLyrics())) {
            throw new IllegalArgumentException("lyrics is required by fal.ai MiniMax Music 3");
        }
        if (opts.getDuration() != null && (opts.getDuration() < 1 || opts.getDuration() > 300)) {
            throw new IllegalArgumentException("duration must be between 1 and 300");
        }
        if (opts.getNumInferenceSteps() != null
                && (opts.getNumInferenceSteps() < 1 || opts.getNumInferenceSteps() > 100)) {
            throw new IllegalArgumentException("num_inference_steps must be between 1 and 100");
        }
        if (opts.getGuidanceScale() != null
                && (opts.getGuidanceScale() < 0 || opts.getGuidanceScale() > 20)) {
            throw new IllegalArgumentException("guidance_scale must be between 0 and 20");
        }

        JSONObject body = new JSONObject();
        body.put("prompt", request.getPrompt());
        body.put("lyrics", opts.getLyrics());
        if (opts.getDuration() != null) body.put("duration", opts.getDuration());
        if (opts.getSeed() != null) body.put("seed", opts.getSeed());
        if (opts.getNumInferenceSteps() != null) body.put("num_inference_steps", opts.getNumInferenceSteps());
        if (opts.getGuidanceScale() != null) body.put("guidance_scale", opts.getGuidanceScale());
        return body;
    }

    /** 解析 API 响应，公开给单元测试复用。 */
    public MusicResponse parseResponse(JSONObject root) {
        if (root.has("detail")) {
            Object detail = root.get("detail");
            String message = detail instanceof JSONObject ? detail.toString() : detail.toString();
            throw new IllegalStateException("fal.ai music-3 error: " + message);
        }
        String url = null;
        JSONObject audio = root.optJSONObject("audio");
        if (audio != null) {
            url = audio.optString("url", null);
        }
        if (url == null || url.isEmpty()) {
            url = root.optString("audio", null);
        }
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException("fal.ai music-3 response has no audio url");
        }
        JSONObject extra = new JSONObject();
        if (root.has("seed")) extra.put("seed", root.get("seed"));
        if (root.has("duration")) extra.put("duration", root.get("duration"));
        return new MusicResponse(null, url, null, null, extra, root);
    }

    public String endpointId(MusicOptions opts) {
        if (opts.getModel() != null && opts.getModel().contains("/")) {
            return opts.getModel();
        }
        return apiUrl != null && apiUrl.startsWith(FAL_HOST)
                ? apiUrl.substring(FAL_HOST.length()) : DEFAULT_ENDPOINT;
    }

    private String runUrl(MusicOptions opts) {
        return FAL_HOST + endpointId(opts);
    }

    private String queueUrl(MusicOptions opts) {
        return QUEUE_HOST + endpointId(opts);
    }

    private JSONObject getJson(String url) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("fal.ai music-3 requires an API key (FAL_KEY)");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();
        HttpResponse<String> response = HttpUtils.createHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return new JSONObject(response.body());
    }

    private JSONObject postJson(String url, JSONObject body) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("fal.ai music-3 requires an API key (FAL_KEY)");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Key " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HttpUtils.createHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return new JSONObject(response.body());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String curl(String url, JSONObject body) {
        return "curl -X POST '" + url + "' \\\n"
                + "  -H 'Authorization: Key ****' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }
}
