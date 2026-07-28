package com.gdxsoft.ai.stt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.stt.providers.doubao.DoubaoSttProvider;

class DoubaoSttProviderParseTest {

    @Test
    void buildBodyUsesInputAudioDataAndFormat() throws IOException {
        DoubaoSttProvider p = new DoubaoSttProvider();
        p.setApiKey("dummy");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1, 2, 3}, "audio/webm", "x.webm"));
        JSONObject body = new JSONObject(p.buildJsonBody(req));

        assertEquals(DoubaoSttProvider.DEFAULT_MODEL, body.getString("model"));
        assertFalse(body.getBoolean("stream"));

        JSONObject msg = body.getJSONArray("messages").getJSONObject(0);
        assertEquals("user", msg.getString("role"));
        var content = msg.getJSONArray("content");

        JSONObject audioItem = content.getJSONObject(0);
        assertEquals("input_audio", audioItem.getString("type"));
        JSONObject inputAudio = audioItem.getJSONObject("input_audio");
        assertEquals("webm", inputAudio.getString("format"));
        assertArrayEquals(new byte[]{1, 2, 3},
                Base64.getDecoder().decode(inputAudio.getString("data")));

        JSONObject textItem = content.getJSONObject(1);
        assertEquals("text", textItem.getString("type"));
        assertEquals(DoubaoSttProvider.DEFAULT_PROMPT, textItem.getString("text"));
    }

    @Test
    void explicitModelAndPromptWin() throws IOException {
        DoubaoSttProvider p = new DoubaoSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/x-wav", "x.wav"),
                new SttOptions().withModel("doubao-seed-asr-x").withPrompt("转写为简体中文"));
        JSONObject body = new JSONObject(p.buildJsonBody(req));
        assertEquals("doubao-seed-asr-x", body.getString("model"));
        var content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals("wav", content.getJSONObject(0).getJSONObject("input_audio").getString("format"));
        assertEquals("转写为简体中文", content.getJSONObject(1).getString("text"));
    }

    @Test
    void whisperDefaultFallsBackToDoubaoModel() throws IOException {
        DoubaoSttProvider p = new DoubaoSttProvider();
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/mpeg", "x.mp3")); // SttOptions 默认 model=whisper-1
        JSONObject body = new JSONObject(p.buildJsonBody(req));
        assertEquals(DoubaoSttProvider.DEFAULT_MODEL, body.getString("model"));
        var content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals("mp3", content.getJSONObject(0).getJSONObject("input_audio").getString("format"));
    }

    @Test
    void parseSuccessResponse() throws IOException {
        DoubaoSttProvider p = new DoubaoSttProvider();
        JSONObject resp = new JSONObject(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好世界\"}}]}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        SttResponse r = p.parseResponse(resp, req);
        assertEquals("你好世界", r.getText());
    }

    @Test
    void parseErrorResponseThrows() throws IOException {
        DoubaoSttProvider p = new DoubaoSttProvider();
        JSONObject resp = new JSONObject(
                "{\"error\":{\"code\":\"InvalidApiKey\",\"message\":\"Invalid API-key provided.\"}}");
        SttRequest req = new SttRequest(
                AudioSource.fromBytes(new byte[]{1}, "audio/webm", "x.webm"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> p.parseResponse(resp, req));
        assertTrue(ex.getMessage().contains("Invalid API-key"));
    }

    @Test
    void missingApiKeyThrows() {
        DoubaoSttProvider p = new DoubaoSttProvider();
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
    void factoryResolvesDoubaoStt() {
        assertTrue(SttProviderFactory.isSupported("doubao_stt"));
        assertEquals(SttProviderType.DOUBAO, SttProviderType.fromName("DOUBAO_STT"));
        assertTrue(SttProviderFactory.create("doubao_stt") instanceof DoubaoSttProvider);
    }
}
