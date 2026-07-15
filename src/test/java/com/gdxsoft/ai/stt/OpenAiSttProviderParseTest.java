package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.openai.OpenAiSttProvider;

class OpenAiSttProviderParseTest {

    @Test
    void simpleJsonResponse() {
        OpenAiSttProvider p = new OpenAiSttProvider();
        JSONObject resp = new JSONObject("{\"text\":\"Hello world.\"}");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1,2,3}, "audio/wav", "x.wav"));
        SttResponse r = p.parseResponse(resp, req);

        assertEquals("Hello world.", r.getText());
        assertNull(r.getLanguage());
        assertNull(r.getDuration());
        assertNull(r.getSegments());
    }

    @Test
    void verboseJsonPopulatesLanguageAndDuration() {
        OpenAiSttProvider p = new OpenAiSttProvider();
        JSONObject resp = new JSONObject(
                "{\"text\":\"hi\",\"language\":\"en\",\"duration\":1.23}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withResponseFormat("verbose_json"));
        SttResponse r = p.parseResponse(resp, req);

        assertEquals("hi", r.getText());
        assertEquals("en", r.getLanguage());
        assertEquals(1.23, r.getDuration(), 1e-9);
    }

    @Test
    void verboseJsonParsesSegments() {
        OpenAiSttProvider p = new OpenAiSttProvider();
        JSONObject resp = new JSONObject(
                "{\"text\":\"Hello world. Goodbye world.\",\"language\":\"en\",\"duration\":3.0," +
                "\"segments\":[" +
                "  {\"start\":0.0,\"end\":1.5,\"text\":\"Hello world.\"}," +
                "  {\"start\":1.5,\"end\":3.0,\"text\":\"Goodbye world.\"}" +
                "]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withResponseFormat("verbose_json"));
        SttResponse r = p.parseResponse(resp, req);

        assertNotNull(r.getSegments());
        assertEquals(2, r.getSegments().size());
        assertEquals(0.0, r.getSegments().get(0).start(), 1e-9);
        assertEquals(1.5, r.getSegments().get(0).end(), 1e-9);
        assertEquals("Hello world.", r.getSegments().get(0).text());
        assertEquals("Goodbye world.", r.getSegments().get(1).text());
    }

    @Test
    void nonVerboseJsonIgnoresSegments() {
        OpenAiSttProvider p = new OpenAiSttProvider();
        // Even if segments are present in the body, we only parse them when verbose_json is requested.
        JSONObject resp = new JSONObject(
                "{\"text\":\"hi\",\"segments\":[{\"start\":0,\"end\":1,\"text\":\"hi\"}]}");
        SttRequest req = new SttRequest(AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"));
        SttResponse r = p.parseResponse(resp, req);

        assertEquals("hi", r.getText());
        assertNull(r.getSegments());
    }

    @Test
    void defaultUrlAndType() {
        OpenAiSttProvider p = new OpenAiSttProvider();
        assertEquals(SttProviderType.OPENAI, p.getProviderType());
        assertEquals(OpenAiSttProvider.DEFAULT_URL, p.getApiUrl());
        assertEquals("whisper-1", new SttOptions().getModel());
    }
}