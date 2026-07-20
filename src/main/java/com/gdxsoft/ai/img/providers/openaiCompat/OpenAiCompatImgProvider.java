package com.gdxsoft.ai.img.providers.openaiCompat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.img.ImgOptions;
import com.gdxsoft.ai.img.ImgProviderBase;
import com.gdxsoft.ai.img.ImgProviderType;
import com.gdxsoft.ai.img.ImgRequest;
import com.gdxsoft.ai.img.ImgResponse;

/**
 * OpenAI-compatible image generation provider.
 * <p>
 * Works with any API that speaks the OpenAI {@code /v1/images/generations}
 * contract (local proxies, LiteLLM, etc.). The apiUrl must be set to the
 * provider's endpoint.
 *
 * @since 1.2.0
 */
public class OpenAiCompatImgProvider extends ImgProviderBase {
    public static final String DEFAULT_MODEL = "dall-e-3";

    public OpenAiCompatImgProvider() {
        // No default URL — caller must set one
    }

    @Override
    public ImgProviderType getProviderType() { return ImgProviderType.OPENAI_COMPAT; }

    @Override
    public ImgResponse generate(ImgRequest request) throws IOException, InterruptedException {
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible image provider requires an apiUrl (setApiUrl)");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible image provider requires an API key (setApiKey)");
        }

        JSONObject body = OpenAiCompatImgBodyBuilder.build(request.getOptions());
        String responseBody = postJson(body);
        return parseResponse(new JSONObject(responseBody));
    }

    @Override
    public String curl(ImgRequest request) {
        JSONObject body = OpenAiCompatImgBodyBuilder.build(request.getOptions());
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        sb.append("  -H 'Authorization: Bearer ****' \\\n");
        sb.append("  -H 'Content-Type: application/json' \\\n");
        sb.append("  -d '").append(body.toString().replace("'", "'\\''")).append("'");
        return sb.toString();
    }

    /**
     * Delegate to the OpenAI parser since the response shape is identical.
     */
    public ImgResponse parseResponse(JSONObject root) {
        // Reuse the OpenAI parser
        return new com.gdxsoft.ai.img.providers.openai.OpenAiImgProvider().parseResponse(root);
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

    /**
     * Shared body builder for OpenAI-compatible image requests.
     * Kept as a separate utility so it can be tested independently.
     */
    public static final class OpenAiCompatImgBodyBuilder {
        private OpenAiCompatImgBodyBuilder() {}

        public static JSONObject build(ImgOptions opts) {
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
    }
}
