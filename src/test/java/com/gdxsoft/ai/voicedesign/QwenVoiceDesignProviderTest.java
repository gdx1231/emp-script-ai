package com.gdxsoft.ai.voicedesign;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.voicedesign.providers.qwen.QwenVoiceDesignProvider;

class QwenVoiceDesignProviderTest {

    @Test
    void buildBodyDefaultsQwenTts() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        p.setApiKey("dummy");
        JSONObject body = new JSONObject(p.buildCreateBody(
                new VoiceDesignRequest("年轻活泼的女性声音，语速较快，带有明显的上扬语调，适合介绍时尚产品。")));

        assertEquals("qwen-voice-design", body.getString("model"));
        JSONObject input = body.getJSONObject("input");
        assertEquals("create", input.getString("action"));
        assertEquals("qwen3-tts-vd-2026-01-26", input.getString("target_model"));
        assertEquals("年轻活泼的女性声音，语速较快，带有明显的上扬语调，适合介绍时尚产品。",
                input.getString("voice_prompt"));
        assertEquals("custom_voice", input.getString("preferred_name"));
        assertFalse(input.has("prefix"));
        JSONObject parameters = body.getJSONObject("parameters");
        assertEquals(24000, parameters.getInt("sample_rate"));
        assertEquals("wav", parameters.getString("response_format"));
    }

    @Test
    void buildBodyExplicitOptions() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        VoiceDesignOptions opts = new VoiceDesignOptions()
                .setTargetModel("qwen3-tts-vd-realtime-2026-01-15")
                .setPrefix("announcer")
                .setSampleRate(48000)
                .setResponseFormat("mp3");
        VoiceDesignRequest req = new VoiceDesignRequest("沉稳的中年男性播音员", opts)
                .setPreviewText("各位听众朋友，大家好。");

        JSONObject body = new JSONObject(p.buildCreateBody(req));
        JSONObject input = body.getJSONObject("input");
        assertEquals("qwen3-tts-vd-realtime-2026-01-15", input.getString("target_model"));
        assertEquals("announcer", input.getString("preferred_name"));
        assertEquals("各位听众朋友，大家好。", input.getString("preview_text"));
        JSONObject parameters = body.getJSONObject("parameters");
        assertEquals(48000, parameters.getInt("sample_rate"));
        assertEquals("mp3", parameters.getString("response_format"));
    }

    @Test
    void buildBodyCosyVoice() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        p.setConfig("model", "voice-enrollment");
        p.setConfig("targetModel", "cosyvoice-v3.5-plus");
        VoiceDesignOptions opts = new VoiceDesignOptions().setPrefix("announcer");
        JSONObject body = new JSONObject(p.buildCreateBody(
                new VoiceDesignRequest("沉稳的中年男性播音员，音色低沉浑厚，富有磁性。", opts)));

        assertEquals("voice-enrollment", body.getString("model"));
        JSONObject input = body.getJSONObject("input");
        assertEquals("create_voice", input.getString("action"));
        assertEquals("cosyvoice-v3.5-plus", input.getString("target_model"));
        assertEquals("announcer", input.getString("prefix"));
        assertFalse(input.has("preferred_name"));
    }

    @Test
    void buildApiUrlDefaultsAndCosyVoice() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        // Qwen-TTS 默认端点
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization",
                p.buildApiUrl());

        // CosyVoice 需要 workspaceId
        p.setConfig("model", "voice-enrollment");
        assertThrows(IllegalStateException.class, p::buildApiUrl);
        p.setConfig("workspaceId", "my-workspace");
        assertEquals("https://my-workspace.cn-beijing.maas.aliyuncs.com/api/v1/services/audio/tts/customization",
                p.buildApiUrl());

        // 显式 apiUrl 优先
        p.setApiUrl("https://example.com/custom");
        assertEquals("https://example.com/custom", p.buildApiUrl());
    }

    @Test
    void parseCreateResponseQwenTts() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        String b64 = Base64.getEncoder().encodeToString(new byte[]{9, 8, 7});
        JSONObject resp = new JSONObject("{\"output\":{\"voice\":\"my-voice-id\","
                + "\"preview_audio\":{\"data\":\"" + b64 + "\",\"format\":\"wav\"}}}");

        VoiceDesignResponse r = p.parseCreateResponse(resp);
        assertTrue(r.isSuccess());
        assertEquals("my-voice-id", r.getVoiceId());
        assertArrayEquals(new byte[]{9, 8, 7}, r.getPreviewAudio());
        assertEquals("audio/wav", r.getPreviewMimeType());
    }

    @Test
    void parseCreateResponseCosyVoice() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        JSONObject resp = new JSONObject("{\"output\":{\"voice_id\":\"cosy-voice-id\"}}");
        VoiceDesignResponse r = p.parseCreateResponse(resp);
        assertTrue(r.isSuccess());
        assertEquals("cosy-voice-id", r.getVoiceId());
        assertNull(r.getPreviewAudio());
    }

    @Test
    void parseCreateResponseError() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        JSONObject resp = new JSONObject("{\"code\":\"InvalidParameter\",\"message\":\"bad prompt\"}");
        VoiceDesignResponse r = p.parseCreateResponse(resp);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("bad prompt"));
    }

    @Test
    void parseCreateResponseNoVoice() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        VoiceDesignResponse r = p.parseCreateResponse(new JSONObject("{\"output\":{}}"));
        assertFalse(r.isSuccess());
    }

    @Test
    void missingApiKeyThrows() {
        QwenVoiceDesignProvider p = new QwenVoiceDesignProvider();
        assertThrows(IllegalStateException.class, () -> {
            try {
                p.create(new VoiceDesignRequest("女声"));
            } catch (Exception e) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
        });
    }

    @Test
    void requestRejectsBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceDesignRequest("   "));
    }

    @Test
    void factoryResolvesQwenVoiceDesign() {
        assertTrue(VoiceDesignProviderFactory.isSupported("qwen_voice_design"));
        assertEquals(VoiceDesignProviderType.QWEN, VoiceDesignProviderType.fromName("QWEN_VOICE_DESIGN"));
        assertTrue(VoiceDesignProviderFactory.create("qwen_voice_design") instanceof QwenVoiceDesignProvider);
    }
}
