package com.gdxsoft.ai.music;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gdxsoft.ai.music.providers.minimax.MiniMaxMusicProvider;

class MiniMaxMusicProviderTest {
    @Test
    void buildRequestBodySupportsMusic3() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        MusicOptions options = new MusicOptions()
                .lyrics("[Verse]\n你好")
                .lyricsOptimizer(false)
                .instrumental(false)
                .sampleRate(44100)
                .bitrate(256000)
                .format("mp3");
        JSONObject body = provider.buildRequestBody(new MusicRequest("流行，明亮", options));
        assertEquals("music-3.0", body.getString("model"));
        assertEquals("hex", body.getString("output_format"));
        assertEquals(44100, body.getJSONObject("audio_setting").getInt("sample_rate"));
        assertEquals("[Verse]\n你好", body.getString("lyrics"));
    }

    @Test
    void buildRequestBodySupportsAutoLyricsAndCoverRules() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        MusicOptions autoLyrics = new MusicOptions().lyricsOptimizer(true);
        assertTrue(provider.buildRequestBody(new MusicRequest("民谣", autoLyrics)).getBoolean("lyrics_optimizer"));

        MusicOptions cover = new MusicOptions().model("music-cover")
                .audioUrl("https://example.com/a.mp3").lyrics("0123456789");
        JSONObject coverBody = provider.buildRequestBody(new MusicRequest("摇滚风格，明亮而且有力", cover));
        assertTrue(coverBody.has("audio_url"));

        assertThrows(IllegalArgumentException.class, () -> provider.buildRequestBody(
                new MusicRequest("摇滚风格，明亮有力", new MusicOptions().model("music-cover")
                        .audioUrl("https://example.com/a.mp3"))));
    }

    @Test
    void parseAndSaveHexAudio(@TempDir Path dir) throws Exception {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        JSONObject root = new JSONObject("""
                {"data":{"audio":"0011ff","status":2},"trace_id":"t","base_resp":{"status_code":0}}
                """);
        MusicResponse response = provider.parseResponse(root);
        assertArrayEquals(new byte[]{0, 17, -1}, response.getAudioBytes());
        assertEquals("t", response.getTraceId());
        assertEquals(3, response.save(dir.resolve("song.mp3")).toFile().length());
    }

    @Test
    void parsesUrlAudioOutput() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        JSONObject root = new JSONObject("""
                {"data":{"audio":"https://example.com/song.mp3","status":2},"base_resp":{"status_code":0}}
                """);
        MusicResponse response = provider.parseResponse(root);
        assertEquals("https://example.com/song.mp3", response.getAudioUrl());
        assertFalse(response.hasAudioBytes());
    }

    @Test
    void buildsAndParsesCoverPreprocess() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        JSONObject request = provider.buildCoverPreprocessRequestBody(
                MusicCoverPreprocessRequest.audioUrl("https://example.com/a.mp3"));
        assertEquals("music-cover", request.getString("model"));
        assertEquals("https://example.com/a.mp3", request.getString("audio_url"));

        JSONObject root = new JSONObject()
                .put("cover_feature_id", "feature")
                .put("formatted_lyrics", "[Verse]\n歌词")
                .put("structure_result", "{\"segments\":[]}")
                .put("audio_duration", 90)
                .put("trace_id", "trace")
                .put("base_resp", new JSONObject().put("status_code", 0));
        MusicCoverPreprocessResponse response = provider.parseCoverPreprocessResponse(root);
        assertEquals("feature", response.getCoverFeatureId());
        assertEquals(90, response.getAudioDuration());
        assertTrue(response.getStructure().has("segments"));

        MusicOptions secondStep = new MusicOptions().model("music-cover")
                .coverFeatureId("feature").lyrics(response.getFormattedLyrics() + "\n0123456789");
        JSONObject musicBody = provider.buildRequestBody(new MusicRequest("流行摇滚风格，保持原曲情绪", secondStep));
        assertEquals("feature", musicBody.getString("cover_feature_id"));
    }

    @Test
    void buildsAndParsesLyricsGeneration() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        JSONObject request = provider.buildLyricsRequestBody(
                MusicLyricsRequest.writeFullSong("一首关于夏日海边的轻快情歌"));
        assertEquals("write_full_song", request.getString("mode"));

        JSONObject edit = provider.buildLyricsRequestBody(
                MusicLyricsRequest.edit("让副歌更明亮", "[Verse]\\n旧歌词"));
        assertEquals("edit", edit.getString("mode"));
        assertTrue(edit.has("lyrics"));

        JSONObject root = new JSONObject("""
                {"song_title":"夏日海风","style_tags":"Mandopop, Summer","lyrics":"[Verse]\\n海边",
                 "base_resp":{"status_code":0}}
                """);
        MusicLyricsResponse response = provider.parseLyricsResponse(root);
        assertEquals("夏日海风", response.getSongTitle());
        assertEquals("Mandopop, Summer", response.getStyleTags());
    }

    @Test
    void composeRunsLyricsThenMusic() throws Exception {
        MusicLyricsResponse lyrics = new MusicLyricsResponse("标题", "Pop, Happy", "[Verse]\\n你好", new JSONObject());
        MusicResponse music = new MusicResponse(null, "https://example.com/song.mp3", 2, null, null, new JSONObject());
        FakeProvider provider = new FakeProvider(lyrics, music);
        MusicComposition result = new MusicClient(provider).compose("夏日海边", new MusicOptions());

        assertEquals("夏日海边", provider.lastLyricsRequest.getPrompt());
        assertEquals("Pop, Happy", provider.lastMusicRequest.getPrompt());
        assertEquals(lyrics.getLyrics(), provider.lastMusicRequest.getOptions().getLyrics());
        assertSame(music, result.getMusic());
    }

    @Test
    void coverRunsPreprocessThenMusic() throws Exception {
        MusicCoverPreprocessResponse preprocess = new MusicCoverPreprocessResponse(
                "feature-1", "[Verse]\n原曲歌词", null, 120.0, "trace-1", new JSONObject());
        MusicResponse music = new MusicResponse("0011ff", null, 2, "trace-2", null, new JSONObject());
        FakeProvider provider = new FakeProvider(null, music);
        provider.nextCoverPreprocess = preprocess;

        MusicClient client = new MusicClient(provider);
        var result = client.cover(
                MusicCoverPreprocessRequest.audioUrl("https://example.com/a.mp3"),
                "流行摇滚风格，保持原曲情绪", new MusicOptions());

        assertEquals("https://example.com/a.mp3", provider.lastCoverRequest.getAudioUrl());
        assertEquals("feature-1", provider.lastMusicRequest.getOptions().getCoverFeatureId());
        assertEquals("[Verse]\n原曲歌词", provider.lastMusicRequest.getOptions().getLyrics());
        assertSame(preprocess, result.getPreprocess());
        assertSame(music, result.getMusic());
    }

    @Test
    void coverSupportsRevisedLyrics() throws Exception {
        MusicCoverPreprocessResponse preprocess = new MusicCoverPreprocessResponse(
                "feature-2", "[Verse]\n原曲歌词", null, 90.0, null, new JSONObject());
        MusicResponse music = new MusicResponse(null, "https://example.com/song.mp3", 2, null, null, new JSONObject());
        FakeProvider provider = new FakeProvider(null, music);
        provider.nextCoverPreprocess = preprocess;

        new MusicClient(provider).cover(
                MusicCoverPreprocessRequest.audioBase64("YXVkaW8="),
                "民谣风格，温柔抒情", new MusicOptions(), "[Verse]\n修改后的歌词");

        assertEquals("YXVkaW8=", provider.lastCoverRequest.getAudioBase64());
        assertEquals("[Verse]\n修改后的歌词", provider.lastMusicRequest.getOptions().getLyrics());
    }

    @Test
    void rendersCurlForLyricsAndCoverPreprocess() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        String lyricsCurl = provider.curl(MusicLyricsRequest.writeFullSong("夏日海边"));
        assertTrue(lyricsCurl.contains("/v1/lyrics_generation"));
        assertTrue(lyricsCurl.contains("Authorization: Bearer ****"));

        String coverCurl = provider.curl(
                MusicCoverPreprocessRequest.audioUrl("https://example.com/a.mp3"));
        assertTrue(coverCurl.contains("/v1/music_cover_preprocess"));
        assertTrue(coverCurl.contains("Authorization: Bearer ****"));
    }

    @Test
    void rejectsStreamOutput() {
        MiniMaxMusicProvider provider = new MiniMaxMusicProvider();
        MusicOptions options = new MusicOptions().lyricsOptimizer(true).stream(true);
        assertThrows(IllegalArgumentException.class,
                () -> provider.buildRequestBody(new MusicRequest("流行", options)));
    }

    private static final class FakeProvider extends MusicProviderBase {
        private final MusicLyricsResponse lyrics;
        private final MusicResponse music;
        private MusicLyricsRequest lastLyricsRequest;
        private MusicRequest lastMusicRequest;
        private MusicCoverPreprocessRequest lastCoverRequest;
        private MusicCoverPreprocessResponse nextCoverPreprocess;

        private FakeProvider(MusicLyricsResponse lyrics, MusicResponse music) {
            this.lyrics = lyrics;
            this.music = music;
        }

        @Override public MusicProviderType getProviderType() { return MusicProviderType.MINIMAX; }

        @Override public MusicResponse generate(MusicRequest request) {
            this.lastMusicRequest = request; return music;
        }

        @Override public MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request) {
            this.lastCoverRequest = request; return nextCoverPreprocess;
        }

        @Override public MusicLyricsResponse generateLyrics(MusicLyricsRequest request) {
            this.lastLyricsRequest = request; return lyrics;
        }

        @Override public String curl(MusicRequest request) { return ""; }

        @Override public String curl(MusicCoverPreprocessRequest request) { return "cover-curl"; }

        @Override public String curl(MusicLyricsRequest request) { return "lyrics-curl"; }
    }
}
