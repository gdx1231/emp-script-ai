package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.qwen.QwenSttProvider;

class QwenSttProviderParseTest {

    @Test
    void buildBodyUsesInputAudioDataUri() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        p.setApiKey("dummy");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1, 2, 3}, "audio/webm", "x.webm"));
        JSONObject body = new JSONObject(p.buildJsonBody(req));

        assertEquals("qwen3-asr-flash", body.getString("model"));
        assertFalse(body.getBoolean("stream"));

        JSONObject msg = body.getJSONArray("messages").getJSONObject(0);
        assertEquals("user", msg.getString("role"));
        JSONObject item = msg.getJSONArray("content").getJSONObject(0);
        assertEquals("input_audio", item.getString("type"));
        String data = item.getJSONObject("input_audio").getString("data");
        assertTrue(data.startsWith("data:audio/webm;base64,"));
        assertArrayEquals(new byte[]{1, 2, 3},
                Base64.getDecoder().decode(data.substring("data:audio/webm;base64,".length())));
    }

    @Test
    void explicitModelWinsOverDefault() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav"),
                new SttOptions().withModel("qwen3-asr-flash-filetrans"));
        JSONObject body = new JSONObject(p.buildJsonBody(req));
        assertEquals("qwen3-asr-flash-filetrans", body.getString("model"));
    }

    @Test
    void whisperDefaultFallsBackToQwenModel() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/wav", "x.wav")); // SttOptions 默认 model=whisper-1
        JSONObject body = new JSONObject(p.buildJsonBody(req));
        assertEquals("qwen3-asr-flash", body.getString("model"));
    }

    @Test
    void parseSuccessResponse() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        JSONObject resp = new JSONObject(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好世界\"}}]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("你好世界", r.getText());
    }

    @Test
    void parseErrorResponseThrows() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        JSONObject resp = new JSONObject(
                "{\"error\":{\"code\":\"InvalidApiKey\",\"message\":\"Invalid API-key provided.\"}}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> p.parseResponse(resp, req));
        assertTrue(ex.getMessage().contains("Invalid API-key"));
    }

    @Test
    void parseNoChoicesThrows() throws IOException {
        QwenSttProvider p = new QwenSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        assertThrows(RuntimeException.class, () -> p.parseResponse(new JSONObject("{}"), req));
    }

    @Test
    void missingApiKeyThrows() {
        QwenSttProvider p = new QwenSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        assertThrows(IllegalStateException.class, () -> {
            try {
                p.transcribe(req);
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        });
    }

    @Test
    void factoryResolvesQwenStt() {
        assertTrue(SttProviderFactory.isSupported("qwen_stt"));
        assertEquals(SttProviderType.QWEN, SttProviderType.fromName("QWEN_STT"));
        assertTrue(SttProviderFactory.create("qwen_stt") instanceof QwenSttProvider);
    }
}
