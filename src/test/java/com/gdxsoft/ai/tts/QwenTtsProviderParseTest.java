package com.gdxsoft.ai.tts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.tts.providers.qwen.QwenTtsProvider;

class QwenTtsProviderParseTest {

    @Test
    void buildBodyDefaults() {
        QwenTtsProvider p = new QwenTtsProvider();
        p.setApiKey("dummy");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("你好")));

        assertEquals("qwen3-tts-flash", body.getString("model"));
        JSONObject input = body.getJSONObject("input");
        assertEquals("你好", input.getString("text"));
        assertEquals("Cherry", input.getString("voice"));
        assertFalse(input.has("language_type"));
    }

    @Test
    void buildBodyExplicitOptions() {
        QwenTtsProvider p = new QwenTtsProvider();
        TtsOptions opts = new TtsOptions().withModel("qwen-tts").withVoice("Ethan")
                .withLanguageType("English");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("hello", opts)));

        assertEquals("qwen-tts", body.getString("model"));
        JSONObject input = body.getJSONObject("input");
        assertEquals("Ethan", input.getString("voice"));
        assertEquals("English", input.getString("language_type"));
    }

    @Test
    void configOverridesDefaults() {
        QwenTtsProvider p = new QwenTtsProvider();
        p.setConfig("model", "qwen3-tts-instruct-flash");
        p.setConfig("voice", "Dylan");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("你好")));

        assertEquals("qwen3-tts-instruct-flash", body.getString("model"));
        assertEquals("Dylan", body.getJSONObject("input").getString("voice"));
    }

    @Test
    void parseDataResponse() {
        QwenTtsProvider p = new QwenTtsProvider();
        String b64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        JSONObject resp = new JSONObject(
                "{\"status_code\":200,\"output\":{\"audio\":{\"data\":\"" + b64 + "\",\"url\":\"\"}}}");
        TtsResponse r = p.parseResponse(resp, new TtsRequest("你好"));

        assertArrayEquals(new byte[]{1, 2, 3}, r.getAudio());
        assertEquals("audio/wav", r.getMimeType());
        assertNull(r.getAudioUrl());
    }

    @Test
    void parseErrorResponseThrows() {
        QwenTtsProvider p = new QwenTtsProvider();
        JSONObject resp = new JSONObject(
                "{\"status_code\":400,\"code\":\"InvalidParameter\",\"message\":\"bad voice\"}");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> p.parseResponse(resp, new TtsRequest("你好")));
        assertTrue(ex.getMessage().contains("bad voice"));
    }

    @Test
    void parseNoAudioThrows() {
        QwenTtsProvider p = new QwenTtsProvider();
        assertThrows(RuntimeException.class,
                () -> p.parseResponse(new JSONObject("{\"output\":{}}"), new TtsRequest("你好")));
    }

    @Test
    void missingApiKeyThrows() {
        QwenTtsProvider p = new QwenTtsProvider();
        assertThrows(IllegalStateException.class, () -> {
            try {
                p.synthesize(new TtsRequest("你好"));
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        });
    }

    @Test
    void factoryResolvesQwenTts() {
        assertTrue(TtsProviderFactory.isSupported("qwen_tts"));
        assertEquals(TtsProviderType.QWEN, TtsProviderType.fromName("QWEN_TTS"));
        assertTrue(TtsProviderFactory.create("qwen_tts") instanceof QwenTtsProvider);
    }
}
