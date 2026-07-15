package com.gdxsoft.ai.stt.providers.openaiCompat;

import java.io.IOException;

import org.json.JSONObject;

import com.gdxsoft.ai.stt.SttProviderBase;
import com.gdxsoft.ai.stt.SttProviderType;
import com.gdxsoft.ai.stt.SttRequest;
import com.gdxsoft.ai.stt.SttResponse;
import com.gdxsoft.ai.stt.internal.MultipartSttSupport;
import com.gdxsoft.ai.stt.providers.openai.OpenAiSttProvider;

/**
 * Any OpenAI-compatible STT endpoint (e.g. Groq, Together, local OpenAI-compatible server).
 * <p>
 * Wire-compatible with OpenAI Whisper: same multipart shape, same response keys.
 */
public class OpenAiCompatSttProvider extends SttProviderBase {

    public OpenAiCompatSttProvider() {
        // Default URL is empty; user must call setApiUrl(...) before transcribe.
        this.apiUrl = "";
    }

    @Override
    public SttProviderType getProviderType() { return SttProviderType.OPENAI_COMPAT; }

    @Override
    public SttResponse transcribe(SttRequest request) throws IOException, InterruptedException {
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalStateException(
                    "openai_compat_stt requires apiUrl (call setApiUrl)");
        }
        var body = MultipartSttSupport.buildBody(request);
        // Authorization is optional for local servers; pass null when apiKey is blank.
        String auth = (apiKey == null || apiKey.isEmpty()) ? null : "Bearer " + apiKey;
        String responseBody = MultipartSttSupport.send(body, apiUrl, auth);
        return new OpenAiSttProvider().parseResponse(new JSONObject(responseBody), request);
    }

    @Override
    public String curl(SttRequest request) {
        return MultipartSttSupport.curl(apiUrl, request, apiKey == null || apiKey.isEmpty() ? null : "Authorization");
    }
}