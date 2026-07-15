package com.gdxsoft.ai.stt.providers.openai;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import com.gdxsoft.ai.stt.AudioSource;
import com.gdxsoft.ai.stt.SttProviderBase;
import com.gdxsoft.ai.stt.SttProviderType;
import com.gdxsoft.ai.stt.SttRequest;
import com.gdxsoft.ai.stt.SttResponse;
import com.gdxsoft.ai.stt.internal.MultipartSttSupport;

/**
 * OpenAI Whisper / gpt-4o-transcribe provider.
 * <p>
 * POSTs {@code multipart/form-data} to {@code https://api.openai.com/v1/audio/transcriptions}.
 */
public class OpenAiSttProvider extends SttProviderBase {
    public static final String DEFAULT_URL = "https://api.openai.com/v1/audio/transcriptions";
    public static final String DEFAULT_MODEL = "whisper-1";

    public OpenAiSttProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.OPENAI; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "OpenAI STT requires an API key (setApiKey)");
        }
        var body = MultipartSttSupport.buildBody(request);
        String responseBody = MultipartSttSupport.send(body, apiUrl, "Bearer " + apiKey);
        return parseResponse(new JSONObject(responseBody), request);
    }

    @Override
    public String curl(SttRequest request) {
        return MultipartSttSupport.curl(apiUrl, request, "Authorization");
    }

    /**
     * Parse the provider JSON response into an {@link SttResponse}.
     * <p>
     * Public so callers (and tests) can reuse the parser without an HTTP layer.
     *
     * @param root response JSON
     * @param req  original request (used to decide whether to parse verbose_json segments)
     */
    public SttResponse parseResponse(JSONObject root, SttRequest req) {
        try {
            String fmt = req.getOptions().getResponseFormat();
            boolean verbose = "verbose_json".equalsIgnoreCase(fmt);
            return SttResponse.fromOpenAiVerboseJson(root, verbose);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse OpenAI STT response: " + e.getMessage(), e);
        }
    }

    /** Sanity helper: default AudioSource factory used when no source is supplied by caller. */
    public static SttRequest reqFor(AudioSource source, String model) {
        return new SttRequest(source, new com.gdxsoft.ai.stt.SttOptions().withModel(model == null ? DEFAULT_MODEL : model));
    }
}