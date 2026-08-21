package com.gdxsoft.ai.music;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.music.providers.fai.FaiMusicProvider;

class FaiMusicProviderTest {
    @Test
    void buildsRequestBodyWithRequiredAndOptional() {
        FaiMusicProvider provider = new FaiMusicProvider();
        MusicOptions options = new MusicOptions()
                .lyrics("[verse]\n晨光穿过松林")
                .duration(120)
                .seed(42L)
                .numInferenceSteps(50)
                .guidanceScale(1.7);
        JSONObject body = provider.buildRequestBody(
                new MusicRequest("Genre: acoustic pop. BPM: 96. Warm and intimate.", options));
        assertEquals("Genre: acoustic pop. BPM: 96. Warm and intimate.", body.getString("prompt"));
        assertEquals("[verse]\n晨光穿过松林", body.getString("lyrics"));
        assertEquals(120, body.getInt("duration"));
        assertEquals(42, body.getInt("seed"));
        assertEquals(50, body.getInt("num_inference_steps"));
        assertEquals(1.7, body.getDouble("guidance_scale"));
    }

    @Test
    void buildRequestBodyRequiresLyrics() {
        FaiMusicProvider provider = new FaiMusicProvider();
        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(new MusicRequest("风格描述", new MusicOptions())));
    }

    @Test
    void buildRequestBodyValidatesRanges() {
        FaiMusicProvider provider = new FaiMusicProvider();
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new MusicRequest("风格", new MusicOptions().lyrics("歌词").duration(400))));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new MusicRequest("风格", new MusicOptions().lyrics("歌词").numInferenceSteps(200))));
        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new MusicRequest("风格", new MusicOptions().lyrics("歌词").guidanceScale(30.0))));
    }

    @Test
    void parsesUrlAudioResponse() {
        FaiMusicProvider provider = new FaiMusicProvider();
        JSONObject root = new JSONObject("""
                {"audio":{"url":"https://fal.media/a.wav","content_type":"audio/wav",
                  "file_name":"a.wav","file_size":123},"seed":42,"duration":60}
                """);
        MusicResponse response = provider.parseResponse(root);
        assertEquals("https://fal.media/a.wav", response.getAudioUrl());
        assertFalse(response.hasAudioBytes());
        assertEquals(42, response.getExtraInfo().getInt("seed"));
        assertEquals(60, response.getExtraInfo().getDouble("duration"));
    }

    @Test
    void parsesErrorDetail() {
        FaiMusicProvider provider = new FaiMusicProvider();
        JSONObject root = new JSONObject("""
                {"detail":"Invalid lyrics"}
                """);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.parseResponse(root));
        assertTrue(ex.getMessage().contains("Invalid lyrics"));
    }

    @Test
    void unsupportedOperationsThrow() {
        FaiMusicProvider provider = new FaiMusicProvider();
        assertThrows(UnsupportedOperationException.class,
                () -> provider.generateLyrics(MusicLyricsRequest.writeFullSong("一首歌")));
        assertThrows(UnsupportedOperationException.class,
                () -> provider.preprocessCover(MusicCoverPreprocessRequest.audioUrl("https://x/a.mp3")));
    }

    @Test
    void factoryCreatesFaiProvider() {
        IMusicProvider provider = MusicProviderFactory.create("fai");
        assertInstanceOf(FaiMusicProvider.class, provider);
        assertEquals(MusicProviderType.FAI, provider.getProviderType());
    }

    @Test
    void mapResultHandlesCompletedAndFailed() {
        FaiMusicProvider provider = new FaiMusicProvider();

        JSONObject ok = new JSONObject("""
                {"audio":{"url":"https://fal.media/a.wav"},"seed":7,"duration":60}
                """);
        MusicTaskStatus succeeded = provider.mapResult(ok);
        assertTrue(succeeded.isSucceeded());
        assertEquals("https://fal.media/a.wav", succeeded.getResponse().getAudioUrl());

        JSONObject failed = new JSONObject("""
                {"detail":"invalid lyrics"}
                """);
        MusicTaskStatus failedStatus = provider.mapResult(failed);
        assertTrue(failedStatus.isFailed());
        assertTrue(failedStatus.getError().contains("invalid lyrics"));
    }

    @Test
    void endpointIdFallsBackToDefault() {
        FaiMusicProvider provider = new FaiMusicProvider();
        assertEquals("minimax/music-3", provider.endpointId(new MusicOptions()));
        assertEquals("other/model", provider.endpointId(new MusicOptions().model("other/model")));
    }
}
