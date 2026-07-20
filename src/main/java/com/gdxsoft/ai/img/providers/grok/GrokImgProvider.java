package com.gdxsoft.ai.img.providers.grok;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Grok (xAI) image generation provider.
 * <p>
 * Supports two generation modes:
 * <ul>
 *   <li><b>images</b> (default) — POSTs to {@code /v1/images/generations}
 *       with OpenAI-compatible JSON body</li>
 *   <li><b>chat</b> — POSTs to {@code /v1/chat/completions}, instructing Grok
 *       to generate images via its multimodal output; then extracts image URLs
 *       from the assistant's response</li>
 * </ul>
 * <p>
 * Default endpoint: {@code https://api.x.ai/v1/images/generations}
 * <p>
 * Auth: {@code Authorization: Bearer <api-key>} (xAI API key from console.x.ai)
 *
 * @since 1.2.0
 */
public class GrokImgProvider extends ImgProviderBase {
    public static final String DEFAULT_IMAGES_URL = "https://api.x.ai/v1/images/generations";
    public static final String DEFAULT_CHAT_URL = "https://api.x.ai/v1/chat/completions";
    public static final String DEFAULT_MODEL = "grok-3-mini";

    /** Generation mode: "images" or "chat". */
    private String generationMode = "images";

    /** Pattern to extract markdown image URLs from chat responses. */
    private static final Pattern IMG_URL_PATTERN =
            Pattern.compile("!\\[.*?\\]\\((https?://[^\\s)]+)\\)");

    public GrokImgProvider() {
        this.apiUrl = DEFAULT_IMAGES_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.GROK; }

    // === Mode configuration ===

    /**
     * Set the generation mode.
     * <ul>
     *   <li>{@code "images"} — use xAI's dedicated image generation endpoint (default)</li>
     *   <li>{@code "chat"} — use the chat completions API, Grok generates images in its response</li>
     * </ul>
     */
    public void setGenerationMode(String mode) {
        if (!"images".equals(mode) && !"chat".equals(mode)) {
            throw new IllegalArgumentException("mode must be 'images' or 'chat', got: " + mode);
        }
        this.generationMode = mode;
    }
    public String getGenerationMode() { return generationMode; }

    // === Core API ===

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Grok image generation requires an API key (xAI API key from console.x.ai)");
        }
        if ("chat".equals(generationMode)) {
            return generateViaChat(request.getOptions());
        }
        return generateViaImages(request.getOptions());
    }

    @Override
    public String curl(ImgRequest request) {
        if ("chat".equals(generationMode)) {
            return curlChat(request.getOptions());
        }
        JSONObject body = buildImagesBody(request.getOptions());
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    // === Images mode (standalone endpoint) ===

    private ImgResponse generateViaImages(ImgOptions opts) throws IOException, InterruptedException {
        String url = apiUrl != null ? apiUrl : DEFAULT_IMAGES_URL;
        JSONObject body = buildImagesBody(opts);
        String respBody = postJson(url, body);
        return parseResponse(new JSONObject(respBody));
    }

    public JSONObject buildImagesBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);
        body.put("prompt", opts.getPrompt());
        body.put("n", opts.getN() != null ? opts.getN() : 1);
        body.put("size", opts.getSize() != null ? opts.getSize() : "1024x1024");
        if (opts.getResponseFormat() != null) body.put("response_format", opts.getResponseFormat());
        if (opts.getQuality() != null) body.put("quality", opts.getQuality());
        if (opts.getStyle() != null) body.put("style", opts.getStyle());
        if (opts.getUser() != null) body.put("user", opts.getUser());
        return body;
    }

    // === Chat mode (Grok generates images in chat response) ===

    private ImgResponse generateViaChat(ImgOptions opts) throws IOException, InterruptedException {
        String chatUrl = DEFAULT_CHAT_URL;
        JSONObject body = buildChatBody(opts);
        String respBody = postJson(chatUrl, body);
        return parseChatResponse(new JSONObject(respBody), opts);
    }

    public JSONObject buildChatBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);

        JSONArray messages = new JSONArray();
        // System message to instruct image generation
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content",
                "You are an image generator. When the user describes an image, " +
                "generate it and include the image in your response using markdown " +
                "image syntax: ![description](image_url). Only output the generated image(s), " +
                "no extra text.");
        messages.put(sysMsg);

        // User prompt with image generation instruction
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");

        StringBuilder prompt = new StringBuilder("Generate an image: ");
        prompt.append(opts.getPrompt());
        if (opts.getSize() != null) {
            prompt.append(". Size: ").append(opts.getSize());
        }
        if (opts.getStyle() != null) {
            prompt.append(". Style: ").append(opts.getStyle());
        }
        if (opts.getQuality() != null) {
            prompt.append(". Quality: ").append(opts.getQuality());
        }
        userMsg.put("content", prompt.toString());
        messages.put(userMsg);

        body.put("messages", messages);
        body.put("max_tokens", 4096);
        body.put("n", 1); // chat mode generates one response; images are inline

        return body;
    }

    /**
     * Parse the chat completion response to extract generated image URLs.
     */
    public ImgResponse parseChatResponse(JSONObject root, ImgOptions opts) {
        List<GeneratedImage> images = new ArrayList<>();

        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.optJSONObject("message");
            if (message != null) {
                String content = message.optString("content", "");

                // Extract markdown image URLs: ![desc](url)
                Matcher m = IMG_URL_PATTERN.matcher(content);
                while (m.find()) {
                    images.add(new GeneratedImage(m.group(1), null, null));
                }

                // Also check for direct image URLs without markdown
                if (images.isEmpty()) {
                    // Try extracting any https URLs that look like images
                    Pattern urlPat = Pattern.compile(
                            "https?://[^\\s]+\\.(?:png|jpg|jpeg|gif|webp)[^\\s]*",
                            Pattern.CASE_INSENSITIVE);
                    Matcher urlMat = urlPat.matcher(content);
                    while (urlMat.find()) {
                        images.add(new GeneratedImage(urlMat.group(), null, null));
                    }
                }
            }

            // Check finish_reason for errors
            String finishReason = choice.optString("finish_reason", null);
            if ("content_filter".equals(finishReason)) {
                throw new RuntimeException("Grok image generation blocked by content filter");
            }
        }

        String model = root.optString("model", opts.getModel());
        JSONObject usage = root.optJSONObject("usage");
        return new ImgResponse(images, null, null, model, usage, root);
    }

    private String curlChat(ImgOptions opts) {
        JSONObject body = buildChatBody(opts);
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(DEFAULT_CHAT_URL).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    // === Response parsing ===

    /**
     * Parse images-mode response (OpenAI-compatible shape).
     */
    public ImgResponse parseResponse(JSONObject root) {
        return new com.gdxsoft.ai.img.providers.openai.OpenAiImgProvider().parseResponse(root);
    }

    // === HTTP helper ===

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
}
