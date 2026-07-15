package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.local.LocalSttProvider;

class LocalSttProviderParseTest {

    @Test
    void parseTextAndLanguage() throws IOException {
        LocalSttProvider p = new LocalSttProvider();
        JSONObject resp = new JSONObject("{\"text\":\"hi there\",\"language\":\"en\"}");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("hi there", r.getText());
        assertEquals("en", r.getLanguage());
    }

    @Test
    void parseFallsBackToRequestLanguage() throws IOException {
        LocalSttProvider p = new LocalSttProvider();
        JSONObject resp = new JSONObject("{\"text\":\"hi\"}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("zh"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("zh", r.getLanguage());
    }

    @Test
    void buildJsonBodyIncludesAudioBase64AndLanguage() throws IOException {
        LocalSttProvider p = new LocalSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{'A', 'B'}, "audio/wav", "x.wav"),
                new SttOptions().withLanguage("en").withModel("base"));
        String body = p.buildJsonBody(req);
        assertTrue(body.contains("\"audio_base64\":\"QUI=\""), "expected base64-encoded AB");
        assertTrue(body.contains("\"mime_type\":\"audio/wav\""));
        assertTrue(body.contains("\"language\":\"en\""));
        assertTrue(body.contains("\"model\":\"base\""));
    }
}