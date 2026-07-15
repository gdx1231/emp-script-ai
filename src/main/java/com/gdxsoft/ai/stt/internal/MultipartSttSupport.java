package com.gdxsoft.ai.stt.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.HttpUtils.MultipartBody;
import com.gdxsoft.ai.HttpUtils.MultipartPart;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.stt.SttOptions;
import com.gdxsoft.ai.stt.SttRequest;

/**
 * Package-private helper shared by {@code OpenAiSttProvider} and
 * {@code OpenAiCompatSttProvider}. Both speak OpenAI's
 * {@code multipart/form-data} transcription API.
 */
public final class MultipartSttSupport {

    private MultipartSttSupport() {}

    /**
     * Build the multipart body from a request, including the file part and any
     * option-derived text fields (model, language, response_format, prompt, temperature).
     */
    public static MultipartBody buildBody(SttRequest req) throws IOException {
        AudioSource audio = req.getAudio();
        byte[] data = audio.materialize();
        List<MultipartPart> parts = new ArrayList<>();
        parts.add(MultipartPart.file("file", data, audio.filenameHint(), audio.mimeType()));

        SttOptions opts = req.getOptions();
        parts.add(MultipartPart.text("model", opts.getModel()));
        if (opts.getLanguage() != null) {
            parts.add(MultipartPart.text("language", opts.getLanguage()));
        }
        if (opts.getResponseFormat() != null) {
            parts.add(MultipartPart.text("response_format", opts.getResponseFormat()));
        }
        if (opts.getPrompt() != null) {
            parts.add(MultipartPart.text("prompt", opts.getPrompt()));
        }
        if (opts.getTemperature() != null) {
            parts.add(MultipartPart.text("temperature", String.valueOf(opts.getTemperature())));
        }
        return HttpUtils.buildMultipart(parts);
    }

    /**
     * POST the multipart body to {@code apiUrl} with the given auth header.
     * Returns the raw response body string.
     */
    public static String send(MultipartBody body, String apiUrl, String authorizationHeaderValue)
            throws IOException, InterruptedException {
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.bytes()));
        if (authorizationHeaderValue != null && !authorizationHeaderValue.isEmpty()) {
            builder.header("Authorization", authorizationHeaderValue);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    /** Render a debug curl snippet for a multipart upload. The Authorization header is masked. */
    public static String curl(String apiUrl, SttRequest req, String authHeaderLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
        if (authHeaderLabel != null) {
            sb.append("  -H 'Authorization: Bearer ****' \\\n");
        }
        sb.append("  -F 'model=").append(req.getOptions().getModel()).append("' \\\n");
        if (req.getOptions().getLanguage() != null) {
            sb.append("  -F 'language=").append(req.getOptions().getLanguage()).append("' \\\n");
        }
        if (req.getOptions().getResponseFormat() != null) {
            sb.append("  -F 'response_format=").append(req.getOptions().getResponseFormat()).append("' \\\n");
        }
        sb.append("  -F 'file=@").append(req.getAudio().filenameHint())
                .append(";type=").append(req.getAudio().mimeType()).append("'");
        return sb.toString();
    }

    /**
     * Default headers for tests / override hooks.
     */
    public static Map<String, String> defaultHeaders() {
        return new HashMap<>();
    }
}