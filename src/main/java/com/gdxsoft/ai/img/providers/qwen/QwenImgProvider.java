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
 * Qwen-image-3.0 multimodal-generation provider.
 * <p>
 * Uses the DashScope multimodal-generation API:
 * {@code https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation}
 * <p>
 * Supports both T2I (text-to-image) and I2I (image-to-image, 1-3 reference images).
 * <p>
 * Supported models: {@code qwen-image-3.0-pro}, {@code qwen-image-3.0}
 * <p>
 * For the legacy wanx (通义万相) text2image API, use {@link QwenImgWanxProvider}.
 *
 * @since 1.2.0
 */
public class QwenImgProvider extends ImgProviderBase {

    public static final String DEFAULT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    public static final String DEFAULT_MODEL = "qwen-image-3.0-pro";

    /**
     * Workspace ID for optimized domain.
     * When set, calls use {@code https://{workspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/...}
     */
    private String workspaceId;

    /** API region: "cn-beijing" (default) or "ap-southeast-1". */
    private String region = "cn-beijing";

    public QwenImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.QWEN; }

    // ======================== Configuration ========================

    /** Set workspace ID for optimized domain. */
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getWorkspaceId() { return workspaceId; }

    /** Set region for workspace domain (default "cn-beijing"). */
    public void setRegion(String region) { this.region = region; }
    public String getRegion() { return region; }

    // ======================== Generate (sync) ========================

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Qwen-image generation requires an API key (DashScope API Key)");
        }
        JSONObject body = buildRequestBody(request.getOptions());
        String url = resolveUrl();
        String responseBody = postJson(url, body);
        return parseResponse(new JSONObject(responseBody), request.getOptions());
    }

    // ======================== Request body ========================

    /**
     * Build request body for qwen-image-3.0 multimodal-generation API.
     *
     * <pre>{@code
     * // T2I
     * {
     *   "model": "qwen-image-3.0-pro",
     *   "input": {
     *     "messages": [{
     *       "role": "user",
     *       "content": [{"text": "prompt"}]
     *     }]
     *   },
     *   "parameters": { "prompt_extend": true, "size": "1024*1024", "n": 1 }
     * }
     *
     * // I2I (with reference images)
     * {
     *   ...,
     *   "input": { "messages": [{ "role": "user", "content": [
     *     {"image": "url1"}, {"image": "url2"}, {"text": "instruction"}
     *   ]}]}
     * }
     * }</pre>
     */
    public JSONObject buildRequestBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        // ---- input.messages ----
        JSONObject input = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");

        JSONArray content = new JSONArray();

        // Reference images (I2I): 1-3 images
        List<String> refs = resolveRefImages(opts, 3);
        if (refs != null) {
            for (String refUrl : refs) {
                JSONObject imgObj = new JSONObject();
                imgObj.put("image", refUrl);
                content.put(imgObj);
            }
        }

        // Text prompt (required)
        JSONObject textObj = new JSONObject();
        textObj.put("text", opts.getPrompt());
        content.put(textObj);

        userMsg.put("content", content);
        messages.put(userMsg);
        input.put("messages", messages);
        body.put("input", input);

        // ---- parameters ----
        JSONObject params = new JSONObject();

        if (opts.getPromptExtend() != null) {
            params.put("prompt_extend", opts.getPromptExtend());
        } else {
            params.put("prompt_extend", true);
        }
        if (opts.getPromptExtendMode() != null) {
            params.put("prompt_extend_mode", opts.getPromptExtendMode());
        }
        if (opts.getSize() != null && !opts.getSize().isBlank()) {
            String size = opts.getSize().trim()
                    .replace("x", "*").replace("X", "*");
            if (!size.matches("\\d+\\*\\d+")) {
                throw new IllegalArgumentException(
                        "size must be '<width>*<height>', got: '" + opts.getSize() + "'");
            }
            params.put("size", size);
        }
        if (opts.getN() != null && opts.getN() > 0) {
            params.put("n", opts.getN());
        }
        if (opts.getNegativePrompt() != null) {
            params.put("negative_prompt", opts.getNegativePrompt());
        }
        if (opts.getSeed() != null) {
            long seed = opts.getSeed();
            if (seed < 0 || seed > 2147483647L) {
                throw new IllegalArgumentException(
                        "seed must be in [0, 2147483647], got: " + seed);
            }
            params.put("seed", seed);
        }
        if (opts.getWatermark() != null) {
            params.put("watermark", opts.getWatermark());
        }
        body.put("parameters", params);

        return body;
    }

    // ======================== Response parsing ========================

    /**
     * Parse qwen-image-3.0 response.
     *
     * <pre>{@code
     * {
     *   "output": {
     *     "choices": [{
     *       "finish_reason": "stop",
     *       "message": {
     *         "content": [{"image": "url"}], "role": "assistant"
     *       }
     *     }]
     *   },
     *   "usage": { "output_height": 1024, "output_width": 1024, ... },
     *   "request_id": "..."
     * }
     * }</pre>
     */
    public ImgResponse parseResponse(JSONObject root, ImgOptions opts) {
        // Error response
        if (root.has("code") && root.has("message")) {
            String code = root.optString("code", "");
            String message = root.optString("message", "Unknown error");
            throw new RuntimeException("Qwen-image error: [" + code + "] " + message);
        }

        String model = root.optString("model", opts.getModel());

        List<GeneratedImage> images = new ArrayList<>();
        JSONObject output = root.optJSONObject("output");
        if (output != null) {
            JSONArray choices = output.optJSONArray("choices");
            if (choices != null) {
                for (int i = 0; i < choices.length(); i++) {
                    JSONObject choice = choices.getJSONObject(i);
                    JSONObject message = choice.optJSONObject("message");
                    if (message != null) {
                        JSONArray content = message.optJSONArray("content");
                        if (content != null) {
                            for (int j = 0; j < content.length(); j++) {
                                JSONObject item = content.getJSONObject(j);
                                String imgUrl = item.optString("image", null);
                                if (imgUrl != null && !imgUrl.isEmpty()) {
                                    images.add(new GeneratedImage(imgUrl, null, null));
                                }
                            }
                        }
                    }
                }
            }
        }

        JSONObject usage = root.optJSONObject("usage");
        return new ImgResponse(images, null, null, model, usage, root);
    }

    // ======================== URL resolution ========================

    /**
     * Resolve API URL, preferring workspace-specific domain when configured.
     */
    private String resolveUrl() {
        if (apiUrl != null && !apiUrl.equals(DEFAULT_URL)) {
            return apiUrl;
        }
        if (workspaceId != null && !workspaceId.isEmpty()) {
            String r = region != null ? region : "cn-beijing";
            return "https://" + workspaceId + "." + r + ".maas.aliyuncs.com"
                    + "/api/v1/services/aigc/multimodal-generation/generation";
        }
        return DEFAULT_URL;
    }

    // ======================== HTTP ========================

    private String postJson(String url, JSONObject body) throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
        String url = resolveUrl();
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(url).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }
}
