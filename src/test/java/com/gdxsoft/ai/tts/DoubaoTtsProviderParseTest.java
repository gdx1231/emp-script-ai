package com.gdxsoft.ai.tts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.tts.providers.doubao.DoubaoTtsProvider;

class DoubaoTtsProviderParseTest {

    @Test
    void buildBodyDefaults() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        p.setApiKey("key123");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("你好")));

        assertEquals("seed-audio-1.0", body.getString("model"));
        assertEquals("你好", body.getString("text_prompt"));
        assertFalse(body.has("speaker")); // 音色可选
        JSONObject audioConfig = body.getJSONObject("audio_config");
        assertEquals("mp3", audioConfig.getString("format")); // TtsOptions 框架默认 mp3
        assertFalse(audioConfig.has("speech_rate"));
    }

    @Test
    void buildBodyExplicitOptions() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        TtsOptions opts = new TtsOptions().withVoice("zh_female_cancan_mars_bigtts")
                .withFormat("mp3").withSpeed(1.5).withModel("seed-audio-1.0-multilingual");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("你好", opts)));

        assertEquals("seed-audio-1.0-multilingual", body.getString("model"));
        assertEquals("zh_female_cancan_mars_bigtts", body.getString("speaker"));
        JSONObject audioConfig = body.getJSONObject("audio_config");
        assertEquals("mp3", audioConfig.getString("format"));
        assertEquals(50, audioConfig.getInt("speech_rate")); // 1.5x → 50
    }

    @Test
    void configOverridesDefaults() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        p.setConfig("model", "seed-audio-1.0-multilingual");
        p.setConfig("voice", "BV001");
        JSONObject body = new JSONObject(p.buildJsonBody(new TtsRequest("你好")));

        assertEquals("seed-audio-1.0-multilingual", body.getString("model"));
        assertEquals("BV001", body.getString("speaker"));
    }

    @Test
    void speedToSpeechRateMapping() {
        assertEquals(0, DoubaoTtsProvider.speedToSpeechRate(1.0));
        assertEquals(100, DoubaoTtsProvider.speedToSpeechRate(2.0));
        assertEquals(-50, DoubaoTtsProvider.speedToSpeechRate(0.5));
        assertEquals(100, DoubaoTtsProvider.speedToSpeechRate(9.9)); // 裁剪上限
        assertEquals(-50, DoubaoTtsProvider.speedToSpeechRate(0.0)); // 裁剪下限
    }

    @Test
    void parseSuccessResponse() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        String b64 = Base64.getEncoder().encodeToString(new byte[]{9, 8, 7});
        JSONObject resp = new JSONObject("{\"code\":0,\"message\":\"success\",\"audio\":\"" + b64
                + "\",\"duration\":1.5,\"url\":\"https://example.com/a.mp3\"}");
        TtsResponse r = p.parseResponse(resp, new TtsRequest("你好", new TtsOptions().withFormat("mp3")));

        assertArrayEquals(new byte[]{9, 8, 7}, r.getAudio());
        assertEquals("audio/mpeg", r.getMimeType());
        assertEquals("https://example.com/a.mp3", r.getAudioUrl());
    }

    @Test
    void parseSuccessWithoutCodeField() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        String b64 = Base64.getEncoder().encodeToString(new byte[]{5, 6});
        // 实测：成功响应没有 code 字段
        JSONObject resp = new JSONObject("{\"audio\":\"" + b64 + "\",\"duration\":3}");
        TtsResponse r = p.parseResponse(resp, new TtsRequest("你好"));

        assertArrayEquals(new byte[]{5, 6}, r.getAudio());
    }

    @Test
    void parseErrorResponseThrows() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        JSONObject resp = new JSONObject("{\"code\":3001,\"message\":\"Invalid api key\"}");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> p.parseResponse(resp, new TtsRequest("你好")));
        assertTrue(ex.getMessage().contains("Invalid api key"));
    }

    @Test
    void missingApiKeyThrows() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        assertThrows(IllegalStateException.class, () -> {
            try {
                p.synthesize(new TtsRequest("你好"));
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        });
    }

    @Test
    void factoryResolvesDoubaoTts() {
        assertTrue(TtsProviderFactory.isSupported("doubao_tts"));
        assertEquals(TtsProviderType.DOUBAO, TtsProviderType.fromName("DOUBAO_TTS"));
        assertTrue(TtsProviderFactory.create("doubao_tts") instanceof DoubaoTtsProvider);
    }

    // ------------------------------------------------------------
    // seed-tts-*（unidirectional 流式接口）
    // ------------------------------------------------------------

    @Test
    void streamModelDetection() {
        assertTrue(DoubaoTtsProvider.isStreamModel("seed-tts-2.0"));
        assertTrue(DoubaoTtsProvider.isStreamModel("SEED-TTS-1.0"));
        assertFalse(DoubaoTtsProvider.isStreamModel("seed-audio-1.0"));
        assertFalse(DoubaoTtsProvider.isStreamModel(null));
    }

    @Test
    void buildStreamBodyShape() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        p.setApiKey("key123");
        TtsOptions opts = new TtsOptions().withVoice("zh_female_xiaohe_uranus_bigtts").withSpeed(2.0);
        JSONObject body = new JSONObject(p.buildStreamJsonBody(new TtsRequest("你好", opts)));

        assertEquals("emp-script-ai", body.getJSONObject("user").getString("uid"));
        JSONObject reqParams = body.getJSONObject("req_params");
        assertEquals("你好", reqParams.getString("text"));
        assertEquals("zh_female_xiaohe_uranus_bigtts", reqParams.getString("speaker"));
        JSONObject audioParams = reqParams.getJSONObject("audio_params");
        assertEquals("mp3", audioParams.getString("format"));
        assertEquals(24000, audioParams.getInt("sample_rate"));
        assertEquals(100, audioParams.getInt("speech_rate"));
    }

    @Test
    void streamVoiceDefaultsToUranus() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        JSONObject body = new JSONObject(p.buildStreamJsonBody(new TtsRequest("你好")));
        assertEquals(DoubaoTtsProvider.DEFAULT_STREAM_VOICE,
                body.getJSONObject("req_params").getString("speaker"));
    }

    @Test
    void parseChunkedResponse() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        String b64a = Base64.getEncoder().encodeToString(new byte[]{1, 2});
        String b64b = Base64.getEncoder().encodeToString(new byte[]{3});
        String body = "{\"code\":0,\"message\":\"\",\"data\":\"" + b64a + "\"}\n"
                + "{\"code\":0,\"message\":\"\",\"data\":\"" + b64b + "\"}\n"
                + "{\"code\":0,\"message\":\"\",\"data\":null,\"sentence\":{\"text\":\"你好\",\"words\":[]}}\n"
                + "{\"code\":20000000,\"message\":\"OK\",\"data\":null}\n";
        TtsResponse r = p.parseChunked(body, new TtsRequest("你好", new TtsOptions().withFormat("mp3")));

        assertArrayEquals(new byte[]{1, 2, 3}, r.getAudio());
        assertEquals("audio/mpeg", r.getMimeType());
    }

    @Test
    void parseChunkedErrorThrows() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        String body = "{\"reqid\":\"\",\"code\":55000000,\"message\":\"resource ID is mismatched\"}\n";
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> p.parseChunked(body, new TtsRequest("你好")));
        assertTrue(ex.getMessage().contains("resource ID is mismatched"));
    }

    @Test
    void parseChunkedNoAudioThrows() {
        DoubaoTtsProvider p = new DoubaoTtsProvider();
        String body = "{\"code\":20000000,\"message\":\"OK\",\"data\":null}\n";
        assertThrows(RuntimeException.class, () -> p.parseChunked(body, new TtsRequest("你好")));
    }
}
