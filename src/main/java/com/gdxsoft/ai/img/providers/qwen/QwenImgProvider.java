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

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;
import com.gdxsoft.ai.img.ImgResponse.GeneratedImage;

/**
 * Qwen (Tongyi Wanxiang / 通义万相) image generation provider.
 * <p>
 * POSTs JSON to {@code https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis}.
 * <p>
 * Qwen's API differs from OpenAI in several ways:
 * <ul>
 *   <li>Prompt is nested under {@code input.prompt} (not top-level)</li>
 *   <li>Size uses {@code *} separator (e.g. {@code 1024*1024})</li>
 *   <li>Response uses {@code output.results[].url} (not {@code data[].url})</li>
 *   <li>Supports async task mode via {@code X-DashScope-Async} header</li>
 *   <li>Supports negative_prompt, seed, and prompt_extend</li>
 * </ul>
 * <p>
 * Supported models: {@code wanx2.1-t2i-turbo}, {@code wanx2.0-t2i-turbo}, {@code wanx-v1}
 *
 * @since 1.2.0
 */
public class QwenImgProvider extends ImgProviderBase {
    public static final String DEFAULT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    public static final String DEFAULT_MODEL = "wanx2.1-t2i-turbo";

    /** Max polling attempts for async tasks. */
    private static final int MAX_POLL_COUNT = 60;
    /** Delay between polls in milliseconds. */
    private static final long POLL_DELAY_MS = 2000;

    /**
     * Qwen now requires async mode for most accounts. Default is true.
     * When sync fails with "does not support synchronous calls", auto-falls back to async.
     */
    private boolean asyncMode = true;

    public QwenImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.QWEN; }

    /**
     * Enable/disable async mode. When enabled, the provider submits a task and polls for
     * completion. Default is true because Qwen has deprecated sync API for most accounts.
     */
    public void setAsyncMode(boolean asyncMode) { this.asyncMode = asyncMode; }
    public boolean isAsyncMode() { return asyncMode; }

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Qwen image generation requires an API key (DashScope API Key)");
        }
        JSONObject body = buildRequestBody(request.getOptions());

        if (asyncMode) {
            return generateAsync(body, request.getOptions());
        }

        // Try sync first, auto-fallback to async if sync is not supported
        try {
            String responseBody = postJson(body, false);
            return parseResponse(new JSONObject(responseBody), request.getOptions());
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg.contains("does not support synchronous calls")
                    || msg.contains("AccessDenied")) {
                LOGGER.info("Qwen sync not supported for this account, auto-falling back to async mode");
                return generateAsync(body, request.getOptions());
            }
            throw e;
        }
    }

    @Override
    public String curl(ImgRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        if (asyncMode) {
            sb.append("  -H 'X-DashScope-Async: enable' \\\n");
        }
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    /**
     * Build the JSON request body in Qwen DashScope format.
     * Visible for testing.
     */
    public JSONObject buildRequestBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        // input block
        JSONObject input = new JSONObject();
        input.put("prompt", opts.getPrompt());
        if (opts.getNegativePrompt() != null) {
            input.put("negative_prompt", opts.getNegativePrompt());
        }
        // Image-to-image: reference image URL
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
            input.put("ref_image", opts.getRefImageUrl());
        }
        body.put("input", input);

        // parameters block
        JSONObject params = new JSONObject();
        if (opts.getSize() != null) {
            // Qwen uses * as separator (e.g., 1024*1024)
            params.put("size", opts.getSize().replace("x", "*"));
        }
        if (opts.getN() != null && opts.getN() > 0) {
            params.put("n", opts.getN());
        }
        if (opts.getSeed() != null) {
            params.put("seed", opts.getSeed());
        }
        if (opts.getSteps() != null) {
            params.put("steps", opts.getSteps());
        }
        if (opts.getStyle() != null) {
            params.put("style", opts.getStyle());
        }
        // Image-to-image: ref strength and mode
        if (opts.getRefStrength() != null) {
            params.put("ref_strength", opts.getRefStrength());
        }
        if (opts.getRefMode() != null) {
            params.put("ref_mode", opts.getRefMode());
        }
        body.put("parameters", params);

        return body;
    }

    /**
     * Parse the Qwen DashScope JSON response into a unified {@link ImgResponse}.
     * <p>
     * Handles both sync responses and async task responses.
     *
     * @param root Qwen API response JSON
     * @param opts original options for model context
     */
    public ImgResponse parseResponse(JSONObject root, ImgOptions opts) {
        // Check for async task status
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            String taskStatus = output.optString("task_status", null);
            if ("FAILED".equals(taskStatus) || "CANCELED".equals(taskStatus)
                    || "UNKNOWN".equals(taskStatus)) {
                String msg = output.optString("message", "Unknown error");
                String code = output.optString("code", "");
                throw new RuntimeException(
                        "Qwen image generation " + taskStatus + ": [" + code + "] " + msg);
            }
            if ("PENDING".equals(taskStatus) || "RUNNING".equals(taskStatus)) {
                // Async task not yet complete — return empty response with raw data
                return new ImgResponse(new ArrayList<>(), null, null,
                        opts.getModel(), null, root);
            }
        }

        String model = root.optString("model", opts.getModel());
        String requestId = root.optString("request_id", null);

        List<GeneratedImage> images = new ArrayList<>();
        if (output != null) {
            JSONArray results = output.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String url = item.optString("url", null);
                    String code = item.optString("code", null);
                    // Qwen partial failure: individual result may have code but no url
                    if (url != null && !url.isEmpty()) {
                        String revisedPrompt = item.optString("revised_prompt", null);
                        images.add(new GeneratedImage(url, null, revisedPrompt));
                    } else if (code != null) {
                        LOGGER.warn("Qwen image {} failed: code={}, message={}",
                                i, code, item.optString("message", ""));
                    }
                }
            }
        }

        JSONObject usage = root.optJSONObject("usage");
        return new ImgResponse(images, null, null, model, usage, root);
    }

    // --- Async task support ---

    /**
     * Submit a task in async mode, then poll until completion.
     */
    private ImgResponse generateAsync(JSONObject body, ImgOptions opts)
            throws IOException, InterruptedException {
        // 1) Submit task
        String submitBody = postJson(body, true);
        JSONObject submitJson = new JSONObject(submitBody);
        JSONObject output = submitJson.optJSONObject("output");
        if (output == null) {
            throw new IOException("Qwen async: no output in response: " + submitBody);
        }
        String taskId = output.optString("task_id", null);
        if (taskId == null || taskId.isEmpty()) {
            // Might already be complete
            return parseResponse(submitJson, opts);
        }

        // 2) Poll for result — derive task URL from the same base as apiUrl
        String taskUrl = deriveTaskUrl(taskId);
        for (int i = 0; i < MAX_POLL_COUNT; i++) {
            Thread.sleep(POLL_DELAY_MS);

            HttpClient client = HttpUtils.createHttpClient();
            HttpRequest pollReq = HttpRequest.newBuilder(URI.create(taskUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> pollResp = client.send(pollReq, HttpResponse.BodyHandlers.ofString());
            if (pollResp.statusCode() / 100 != 2) {
                throw new IOException("Qwen poll HTTP " + pollResp.statusCode() + ": " + pollResp.body());
            }

            JSONObject pollJson = new JSONObject(pollResp.body());
            JSONObject pollOutput = pollJson.optJSONObject("output");
            if (pollOutput != null) {
                String status = pollOutput.optString("task_status", "");
                if ("SUCCEEDED".equals(status)) {
                    return parseResponse(pollJson, opts);
                }
                if ("FAILED".equals(status)) {
                    String msg = pollOutput.optString("message", "Unknown");
                    throw new IOException("Qwen task failed: " + msg);
                }
                // PENDING / RUNNING — continue polling
            }
        }
        throw new IOException("Qwen async task timed out after " +
                (MAX_POLL_COUNT * POLL_DELAY_MS / 1000) + "s, taskId=" + taskId);
    }

    /**
     * Derive the task poll URL from the same base domain as apiUrl.
     * e.g. {@code https://dashscope.aliyuncs.com/api/v1/tasks/xxx}
     * or   {@code https://xxx.cn-beijing.maas.aliyuncs.com/api/v1/tasks/xxx}
     */
    private String deriveTaskUrl(String taskId) {
        try {
            URI uri = URI.create(apiUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String base = port > 0
                    ? scheme + "://" + host + ":" + port
                    : scheme + "://" + host;
            return base + "/api/v1/tasks/" + taskId;
        } catch (Exception e) {
            // Fallback to default
            return "https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId;
        }
    }

    private String postJson(JSONObject body, boolean async) throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        if (async) {
            builder.header("X-DashScope-Async", "enable");
        }

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
