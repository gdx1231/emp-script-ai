package com.gdxsoft.ai.img.providers.doubao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;

/**
 * Doubao (豆包 / 火山引擎 Ark) image generation provider.
 * <p>
 * POSTs JSON to {@code https://ark.cn-beijing.volces.com/api/v3/images/generations}.
 * <p>
 * Doubao's API is OpenAI-compatible in response shape ({@code data[].url})
 * but has its own request parameters:
 * <ul>
 *   <li>{@code sequential_image_generation} — "disabled" (default) or "auto"</li>
 *   <li>{@code watermark} — whether to add watermark (default true)</li>
 *   <li>Size presets like {@code "2K"}, {@code "4K"} in addition to exact dimensions</li>
 * </ul>
 * <p>
 * Supported models: {@code doubao-seedream-5-0-260128}, {@code doubao-seedream-4.0-250828}, {@code doubao-seedream-3.0-t2i-250828}
 *
 * @since 1.2.0
 */
public class DoubaoImgProvider extends ImgProviderBase {
    public static final String DEFAULT_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    public static final String DEFAULT_MODEL = "doubao-seedream-5-0-260128";

    /** Default: generate images independently (for n>1). */
    private String sequentialImageGeneration = "disabled";
    /** Whether the API adds a watermark. */
    private boolean watermark = true;
    /** Whether to use SSE streaming response. */
    private boolean stream = false;

    public DoubaoImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.DOUBAO; }

    // === Doubao-specific config ===

    /**
     * Set sequential image generation mode.
     * <ul>
     *   <li>{@code "disabled"} — generate n images independently (default)</li>
     *   <li>{@code "auto"} — generate a group of related images sequentially</li>
     * </ul>
     */
    public void setSequentialImageGeneration(String mode) {
        this.sequentialImageGeneration = mode;
    }
    public String getSequentialImageGeneration() { return sequentialImageGeneration; }

    /** Whether the generated image includes a watermark (default true). */
    public void setWatermark(boolean watermark) { this.watermark = watermark; }
    public boolean isWatermark() { return watermark; }

    /** Whether to use SSE streaming response (default false). */
    public void setStream(boolean stream) { this.stream = stream; }
    public boolean isStream() { return stream; }

    // === Core API ===

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Doubao image generation requires an API key (Volcengine Ark API Key)");
        }
        JSONObject body = buildRequestBody(request.getOptions());

        if (stream) {
            return generateStreaming(body);
        }
        String responseBody = postJson(body);
        return parseResponse(new JSONObject(responseBody));
    }

    @Override
    public String curl(ImgRequest request) {
        JSONObject body = buildRequestBody(request.getOptions());
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    /**
     * Build the JSON request body for Doubao.
     * <p>
     * Follows the OpenAI-compatible shape but adds Doubao-specific fields.
     * Visible for testing.
     */
    public JSONObject buildRequestBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);
        body.put("prompt", opts.getPrompt());
        body.put("n", opts.getN() != null ? opts.getN() : 1);
        body.put("size", opts.getSize() != null ? opts.getSize() : "1024x1024");
        if (opts.getResponseFormat() != null) {
            body.put("response_format", opts.getResponseFormat());
        }
        if (opts.getUser() != null) {
            body.put("user", opts.getUser());
        }
        // Doubao-specific parameters
        body.put("sequential_image_generation", sequentialImageGeneration);
        body.put("watermark", watermark);
        body.put("stream", stream);

        // Image-to-image: reference image
        if (opts.getRefImageUrl() != null && !opts.getRefImageUrl().isEmpty()) {
            body.put("image", opts.getRefImageUrl());
            if (opts.getRefStrength() != null) {
                body.put("strength", opts.getRefStrength());
            }
        }
        return body;
    }

    /**
     * Parse the Doubao JSON response into a unified {@link ImgResponse}.
     * <p>
     * Doubao's response is OpenAI-shaped: {@code {"created":..., "data":[{"url":"..."}]}}.
     * We delegate to the OpenAI parser.
     */
    public ImgResponse parseResponse(JSONObject root) {
        return new com.gdxsoft.ai.img.providers.openai.OpenAiImgProvider().parseResponse(root);
    }

    /**
     * SSE streaming mode — reads events line by line without buffering the full response.
     * Format: {@code data: {"created":..., "data":[...]}\n\n}
     * Terminates with: {@code data: [DONE]}
     */
    private ImgResponse generateStreaming(JSONObject body) throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        HttpResponse<InputStream> resp = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() / 100 != 2) {
            String errBody;
            try (InputStream errIn = resp.body()) {
                errBody = new String(errIn.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IOException("HTTP " + resp.statusCode() + ": " + errBody);
        }

        ImgResponse lastResponse = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder dataBuf = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String payload = line.substring(6).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    dataBuf.append(payload);
                } else if (line.isEmpty() && dataBuf.length() > 0) {
                    // End of event — parse accumulated data
                    JSONObject event = new JSONObject(dataBuf.toString());
                    lastResponse = parseResponse(event);
                    dataBuf.setLength(0);
                }
            }
        }

        if (lastResponse == null) {
            throw new IOException("Doubao stream ended without valid image data");
        }
        return lastResponse;
    }

    private String postJson(JSONObject body) throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
