package com.gdxsoft.ai.img.providers.openai;

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
 * OpenAI DALL-E image generation provider.
 * <p>
 * POSTs JSON to {@code https://api.openai.com/v1/images/generations}.
 *
 * @since 1.2.0
 */
public class OpenAiImgProvider extends ImgProviderBase {
    public static final String DEFAULT_URL = "https://api.openai.com/v1/images/generations";
    public static final String DEFAULT_MODEL = "dall-e-3";

    public OpenAiImgProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.OPENAI; }

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI image generation requires an API key (setApiKey)");
        }
        JSONObject body = buildRequestBody(request.getOptions());
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
     * Build the JSON request body from the unified options.
     * Visible for testing.
     */
    public JSONObject buildRequestBody(ImgOptions opts) {
        JSONObject body = new JSONObject();
        body.put("model", opts.getModel() != null ? opts.getModel() : DEFAULT_MODEL);
        body.put("prompt", opts.getPrompt());
        body.put("n", opts.getN() != null ? opts.getN() : 1);
        body.put("size", opts.getSize() != null ? opts.getSize() : "1024x1024");
        if (opts.getQuality() != null) body.put("quality", opts.getQuality());
        if (opts.getStyle() != null) body.put("style", opts.getStyle());
        if (opts.getResponseFormat() != null) body.put("response_format", opts.getResponseFormat());
        if (opts.getUser() != null) body.put("user", opts.getUser());
        return body;
    }

    /**
     * Parse the OpenAI JSON response into a unified {@link ImgResponse}.
     * <p>
     * Public so callers (and tests) can reuse the parser without an HTTP layer.
     */
    public ImgResponse parseResponse(JSONObject root) {
        Long created = root.has("created") ? root.getLong("created") : null;
        String revisedPrompt = root.optString("revised_prompt", null);
        String model = root.optString("model", null);

        JSONArray dataArr = root.optJSONArray("data");
        List<GeneratedImage> images = new ArrayList<>();
        if (dataArr != null) {
            for (int i = 0; i < dataArr.length(); i++) {
                JSONObject item = dataArr.getJSONObject(i);
                String url = item.optString("url", null);
                String b64 = item.optString("b64_json", null);
                String itemRevisedPrompt = item.optString("revised_prompt", revisedPrompt);
                images.add(new GeneratedImage(url, b64, itemRevisedPrompt));
            }
        }

        JSONObject usage = root.optJSONObject("usage");
        return new ImgResponse(images, created, revisedPrompt, model, usage, root);
    }

    private String postJson(JSONObject body) throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

        // OpenAI organization header (optional)
        String org = extras.get("organization");
        if (org != null && !org.isEmpty()) {
            builder.header("OpenAI-Organization", org);
        }

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }
}
