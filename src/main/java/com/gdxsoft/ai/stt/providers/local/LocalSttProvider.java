package com.gdxsoft.ai.stt.providers.local;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.stt.SttOptions;
import com.gdxsoft.ai.stt.SttProviderBase;
import com.gdxsoft.ai.stt.SttProviderType;
import com.gdxsoft.ai.stt.SttRequest;
import com.gdxsoft.ai.stt.SttResponse;

/**
 * Pluggable local STT provider (Vosk HTTP server, whisper.cpp shim, etc.).
 * <p>
 * Sends JSON {@code {audio_base64, mime_type, language, filename}} by default.
 * If the user's wrapper expects a different shape they can override via subclasses.
 */
public class LocalSttProvider extends SttProviderBase {

    public LocalSttProvider() {
        this.apiUrl = "http://localhost:8080/infer";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.LOCAL; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalStateException("Local STT requires apiUrl (call setApiUrl)");
        }
        String body = buildJsonBody(request);
        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Local STT HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    @Override
    public String curl(SttRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(apiUrl).append("' \\\n");
            if (apiKey != null && !apiKey.isEmpty()) {
                sb.append("  -H 'Authorization: Bearer ****' \\\n");
            }
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(buildJsonBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (failed to render body): " + e.getMessage();
        }
    }

    /** Build the JSON body for the local wrapper. */
    public String buildJsonBody(SttRequest req) throws IOException {
        AudioSource audio = req.getAudio();
        SttOptions opts = req.getOptions();
        JSONObject body = new JSONObject();
        body.put("audio_base64", Base64.getEncoder().encodeToString(audio.materialize()));
        body.put("mime_type", audio.mimeType());
        body.put("filename", audio.filenameHint());
        if (opts.getLanguage() != null) body.put("language", opts.getLanguage());
        if (opts.getModel() != null) body.put("model", opts.getModel());
        return body.toString();
    }

    /** Parse a wrapper response. Default shape: {@code {"text":"…","language":"…"}}. */
    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        String text = root.optString("text", "");
        String language = root.optString("language",
                req.getOptions().getLanguage() == null ? null : req.getOptions().getLanguage());
        Double duration = root.has("duration") ? root.optDouble("duration") : null;
        return new SttResponse(text, language, duration, null, root);
    }
}