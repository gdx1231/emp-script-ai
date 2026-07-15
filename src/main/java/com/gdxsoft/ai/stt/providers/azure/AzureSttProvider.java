package com.gdxsoft.ai.stt.providers.azure;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.gdxsoft.ai.ChatManagerI18nConstants;
import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.stt.SttOptions;
import com.gdxsoft.ai.stt.SttProviderBase;
import com.gdxsoft.ai.stt.SttProviderType;
import com.gdxsoft.ai.stt.SttRequest;
import com.gdxsoft.ai.stt.SttResponse;

/**
 * Azure Speech Services STT provider (short-audio REST API, synchronous, ≤ 1 minute).
 * <p>
 * Required configuration:
 * <ul>
 *   <li>{@code apiKey} — Azure subscription key ({@code setApiKey})</li>
 *   <li>{@code region} — Azure region ({@code setConfig("region", "eastus")})</li>
 * </ul>
 * Optional:
 * <ul>
 *   <li>{@code language} — default recognition language if not supplied via {@link SttOptions}</li>
 * </ul>
 */
public class AzureSttProvider extends SttProviderBase {

    public AzureSttProvider() {
        // apiUrl may be set explicitly to override the default URL template.
        this.apiUrl = "";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.AZURE; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_STT_NO_API_KEY", false, "azure_stt"));
        }
        String region = getConfig("region");
        if (region == null || region.isEmpty()) {
            throw new IllegalStateException(
                    ChatManagerI18nConstants.getText("ERROR_STT_AZURE_REGION_MISSING", false));
        }

        String url = buildUrl(request, region);
        byte[] audioBytes = request.getAudio().materialize();

        HttpClient client = HttpUtils.createHttpClient();
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", request.getAudio().mimeType() != null
                        ? request.getAudio().mimeType() : "audio/wav")
                .header("Accept", "application/json;text/xml")
                .POST(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                .build();
        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Azure STT HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return parseResponse(new JSONObject(resp.body()), request);
    }

    @Override
    public String curl(SttRequest request) {
        String region = getConfig("region");
        String url = buildUrl(request, region);
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X POST '").append(url).append("' \\\n");
        sb.append("  -H 'Ocp-Apim-Subscription-Key: ****' \\\n");
        sb.append("  -H 'Content-Type: ").append(request.getAudio().mimeType()).append("' \\\n");
        sb.append("  -H 'Accept: application/json;text/xml' \\\n");
        sb.append("  --data-binary @<audio-file>");
        return sb.toString();
    }

    /**
     * Construct the short-audio REST endpoint URL.
     * <p>
     * Public so callers and tests can inspect the resolved URL.
     */
    public String buildUrl(SttRequest req, String region) {
        SttOptions opts = req.getOptions();
        String lang = opts.getLanguage();
        if (lang == null) lang = getConfig("language");
        if (lang == null) lang = "en-US";
        String base;
        if (apiUrl != null && !apiUrl.isEmpty()) {
            base = apiUrl;
        } else {
            if (region == null || region.isEmpty()) {
                throw new IllegalStateException(
                        ChatManagerI18nConstants.getText("ERROR_STT_AZURE_REGION_MISSING", false));
            }
            base = "https://" + region + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1";
        }
        return base + "?language=" + URLEncoder.encode(lang, StandardCharsets.UTF_8);
    }

    /**
     * Parse Azure's short-audio recognition response.
     * <p>
     * Shape: {@code {"RecognitionStatus":"Success","DisplayText":"hello","Offset":...,"Duration":...}}
     */
    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        String status = root.optString("RecognitionStatus", "");
        if (!"Success".equalsIgnoreCase(status)) {
            throw new RuntimeException("Azure STT recognition status: " + status);
        }
        String text = root.optString("DisplayText", "");
        String language = req.getOptions().getLanguage();
        if (language == null) language = getConfig("language");
        Double duration = root.has("Duration") ? root.optDouble("Duration") / 10_000_000.0 : null;
        return new SttResponse(text, language, duration, null, root);
    }
}