package com.gdxsoft.ai.stt.providers.google;

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
 * Google Cloud Speech-to-Text provider (sync, short audio, ≤ 1 minute).
 * <p>
 * Sends a JSON body with base64 audio + config. Use {@code ?key=...} for API-key auth.
 */
public class GoogleSttProvider extends SttProviderBase {

    public GoogleSttProvider() {
        this.apiUrl = "https://speech.googleapis.com/v1/speech:recognize";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.GOOGLE; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Google STT requires an API key (setApiKey)");
        }
        String body = buildJsonBody(request);
        String url = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "key=" + apiKey;

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Google STT HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    @Override
    public String curl(SttRequest request) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("curl -X POST '").append(apiUrl).append("?key=****' \\\n");
            sb.append("  -H 'Content-Type: application/json; charset=utf-8' \\\n");
            sb.append("  -d '").append(buildJsonBody(request).replace("'", "'\\''")).append("'");
            return sb.toString();
        } catch (IOException e) {
            return "curl (failed to render body): " + e.getMessage();
        }
    }

    /** Build the Google Speech-to-Text sync recognize JSON body. */
    String buildJsonBody(SttRequest req) throws IOException {
        AudioSource audio = req.getAudio();
        SttOptions opts = req.getOptions();

        JSONObject body = new JSONObject();
        JSONObject config = new JSONObject();
        config.put("encoding", mapEncoding(audio.mimeType()));
        config.put("languageCode", opts.getLanguage() != null ? opts.getLanguage() : "en-US");
        String model = opts.getModel() != null && !opts.getModel().isEmpty() ? opts.getModel() : "latest_long";
        config.put("model", model);
        body.put("config", config);

        JSONObject audioObj = new JSONObject();
        audioObj.put("content", Base64.getEncoder().encodeToString(audio.materialize()));
        body.put("audio", audioObj);
        return body.toString();
    }

    /** Map a MIME type to a Google Speech encoding name. */
    public static String mapEncoding(String mime) {
        if (mime == null) return "ENCODING_UNSPECIFIED";
        switch (mime.toLowerCase()) {
            case "audio/wav":
            case "audio/x-wav":
                return "LINEAR16";
            case "audio/mpeg":
            case "audio/mp3":
                return "MP3";
            case "audio/flac":
            case "audio/x-flac":
                return "FLAC";
            case "audio/ogg":
            case "audio/opus":
                return "OGG_OPUS";
            case "audio/amr-wb":
                return "AMR_WB";
            default:
                return "ENCODING_UNSPECIFIED";
        }
    }

    /** Parse a Google Speech-to-Text sync response. Package-private for unit testing. */
    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        StringBuilder text = new StringBuilder();
        var results = root.optJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                var alts = results.getJSONObject(i).optJSONArray("alternatives");
                if (alts == null || alts.length() == 0) continue;
                var first = alts.getJSONObject(0);
                if (text.length() > 0) text.append(' ');
                text.append(first.optString("transcript", ""));
            }
        }
        String language = req.getOptions().getLanguage();
        return new SttResponse(text.toString(), language, null, null, root);
    }
}