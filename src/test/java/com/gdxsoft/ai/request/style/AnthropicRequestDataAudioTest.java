package com.gdxsoft.ai.request.style;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.providers.anthropic.RequestData;
import com.gdxsoft.ai.request.AiAudioContent;

/**
 * Verify Anthropic audio is serialised as a real {@code input_audio} content block
 * instead of the legacy placeholder text.
 */
class AnthropicRequestDataAudioTest {

    @Test
    void audioBase64_emitsInputAudioBlock() {
        RequestData rd = new RequestData();
        AiAudioContent audio = AiAudioContent.audioBase64("audio/mp3", "AAAA");
        rd.addUserMultiPart(audio);

        JSONObject body = rd.build();
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(1, messages.length());
        JSONArray parts = messages.getJSONObject(0).getJSONArray("content");
        assertEquals(1, parts.length());

        JSONObject part = parts.getJSONObject(0);
        assertEquals("input_audio", part.getString("type"));
        JSONObject src = part.getJSONObject("source");
        assertEquals("base64", src.getString("type"));
        assertEquals("audio/mp3", src.getString("media_type"));
        assertEquals("AAAA", src.getString("data"));
    }

    @Test
    void audioUrl_emitsInputAudioBlock() {
        RequestData rd = new RequestData();
        AiAudioContent audio = AiAudioContent.audioUrl("https://example.com/clip.wav");
        rd.addUserMultiPart(audio);

        JSONArray parts = rd.build().getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content");
        JSONObject src = parts.getJSONObject(0).getJSONObject("source");
        assertEquals("input_audio", parts.getJSONObject(0).getString("type"));
        assertEquals("https://example.com/clip.wav", src.getString("data"));
        // mimeType is null for audioUrl factory; AnthropicRequestData defaults to audio/wav
        assertEquals("audio/wav", src.getString("media_type"));
    }

    @Test
    void audioMimeDefaultsToWavWhenAbsent() {
        RequestData rd = new RequestData();
        AiAudioContent audio = AiAudioContent.audioBase64(null, "BBBB");
        rd.addUserMultiPart(audio);

        JSONObject src = rd.build().getJSONArray("messages")
                .getJSONObject(0).getJSONArray("content")
                .getJSONObject(0).getJSONObject("source");
        assertEquals("audio/wav", src.getString("media_type"));
        assertEquals("BBBB", src.getString("data"));
    }
}