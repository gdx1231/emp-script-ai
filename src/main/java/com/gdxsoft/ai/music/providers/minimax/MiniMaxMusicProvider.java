package com.gdxsoft.ai.music.providers.minimax;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.music.MusicCoverPreprocessRequest;
import com.gdxsoft.ai.music.MusicCoverPreprocessResponse;
import com.gdxsoft.ai.music.MusicLyricsRequest;
import com.gdxsoft.ai.music.MusicLyricsResponse;
import com.gdxsoft.ai.music.MusicOptions;
import com.gdxsoft.ai.music.MusicProviderBase;
import com.gdxsoft.ai.music.MusicProviderType;
import com.gdxsoft.ai.music.MusicRequest;
import com.gdxsoft.ai.music.MusicResponse;

/**
 * MiniMax Music Generation API Provider。
 */
public class MiniMaxMusicProvider extends MusicProviderBase {
    public static final String DEFAULT_URL = "https://api.minimaxi.com/v1/music_generation";
    public static final String COVER_PREPROCESS_URL = "https://api.minimaxi.com/v1/music_cover_preprocess";
    public static final String LYRICS_URL = "https://api.minimaxi.com/v1/lyrics_generation";

    public MiniMaxMusicProvider() {
        this.apiUrl = DEFAULT_URL;
    }

    @Override
    public MusicProviderType getProviderType() { return MusicProviderType.MINIMAX; }

    @Override
    public MusicResponse generate(MusicRequest request) throws IOException, InterruptedException {
        JSONObject body = buildRequestBody(request);
        return parseResponse(postJson(apiUrl, body));
    }

    @Override
    public MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request)
            throws IOException, InterruptedException {
        return parseCoverPreprocessResponse(postJson(COVER_PREPROCESS_URL, buildCoverPreprocessRequestBody(request)));
    }

    @Override
    public MusicLyricsResponse generateLyrics(MusicLyricsRequest request)
            throws IOException, InterruptedException {
        return parseLyricsResponse(postJson(LYRICS_URL, buildLyricsRequestBody(request)));
    }

    @Override
    public String curl(MusicRequest request) {
        JSONObject body = buildRequestBody(request);
        return curl(apiUrl, body);
    }

    @Override
    public String curl(MusicCoverPreprocessRequest request) {
        return curl(COVER_PREPROCESS_URL, buildCoverPreprocessRequestBody(request));
    }

    @Override
    public String curl(MusicLyricsRequest request) {
        return curl(LYRICS_URL, buildLyricsRequestBody(request));
    }

    /** 构造 API 请求体，公开给单元测试复用。 */
    public JSONObject buildRequestBody(MusicRequest request) {
        MusicOptions opts = request.getOptions();
        String model = opts.getModel() == null || opts.getModel().isEmpty()
                ? MusicOptions.DEFAULT_MODEL : opts.getModel();
        boolean cover = model.startsWith("music-cover");

        if (!cover && !Boolean.TRUE.equals(opts.getInstrumental())
                && !Boolean.TRUE.equals(opts.getLyricsOptimizer())
                && isBlank(opts.getLyrics())) {
            throw new IllegalArgumentException("lyrics is required unless instrumental or lyricsOptimizer is enabled");
        }
        if (opts.isStream()) {
            throw new IllegalArgumentException("stream music generation is not supported");
        }
        int referenceCount = countPresent(opts.getAudioUrl(), opts.getAudioBase64(), opts.getCoverFeatureId());
        if (cover && referenceCount != 1) {
            throw new IllegalArgumentException("music-cover requires exactly one of audioUrl, audioBase64 or coverFeatureId");
        }
        if (!cover && referenceCount != 0) {
            throw new IllegalArgumentException("audio reference fields only support music-cover models");
        }

        validateMusicOptions(model, request, opts);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("prompt", request.getPrompt());
        if (!isBlank(opts.getLyrics())) body.put("lyrics", opts.getLyrics());
        body.put("stream", opts.isStream());
        body.put("output_format", opts.getOutputFormat() == null ? "hex" : opts.getOutputFormat());
        JSONObject audioSetting = new JSONObject();
        if (opts.getSampleRate() != null) audioSetting.put("sample_rate", opts.getSampleRate());
        if (opts.getBitrate() != null) audioSetting.put("bitrate", opts.getBitrate());
        if (opts.getFormat() != null) audioSetting.put("format", opts.getFormat());
        body.put("audio_setting", audioSetting);
        if (opts.getWatermark() != null) body.put("aigc_watermark", opts.getWatermark());
        if (opts.getLyricsOptimizer() != null) body.put("lyrics_optimizer", opts.getLyricsOptimizer());
        if (opts.getInstrumental() != null) body.put("is_instrumental", opts.getInstrumental());
        if (!isBlank(opts.getAudioUrl())) body.put("audio_url", opts.getAudioUrl());
        if (!isBlank(opts.getAudioBase64())) body.put("audio_base64", opts.getAudioBase64());
        if (!isBlank(opts.getCoverFeatureId())) body.put("cover_feature_id", opts.getCoverFeatureId());
        return body;
    }

    /** 构造翻唱前处理请求体。 */
    public JSONObject buildCoverPreprocessRequestBody(MusicCoverPreprocessRequest request) {
        int count = countPresent(request.getAudioUrl(), request.getAudioBase64());
        if (count != 1) {
            throw new IllegalArgumentException("cover preprocess requires exactly one of audioUrl or audioBase64");
        }
        JSONObject body = new JSONObject();
        body.put("model", MusicCoverPreprocessRequest.MODEL);
        if (!isBlank(request.getAudioUrl())) body.put("audio_url", request.getAudioUrl());
        if (!isBlank(request.getAudioBase64())) body.put("audio_base64", request.getAudioBase64());
        return body;
    }

    /** 构造歌词生成请求体。 */
    public JSONObject buildLyricsRequestBody(MusicLyricsRequest request) {
        if (!MusicLyricsRequest.MODE_WRITE_FULL_SONG.equals(request.getMode())
                && !MusicLyricsRequest.MODE_EDIT.equals(request.getMode())) {
            throw new IllegalArgumentException("unsupported lyrics mode: " + request.getMode());
        }
        if (length(request.getPrompt()) > 2000) {
            throw new IllegalArgumentException("lyrics prompt max length is 2000");
        }
        if (length(request.getLyrics()) > 3500) {
            throw new IllegalArgumentException("lyrics max length is 3500");
        }
        if (MusicLyricsRequest.MODE_EDIT.equals(request.getMode()) && isBlank(request.getLyrics())) {
            throw new IllegalArgumentException("lyrics is required in edit mode");
        }
        JSONObject body = new JSONObject();
        body.put("mode", request.getMode());
        if (!isBlank(request.getPrompt())) body.put("prompt", request.getPrompt());
        if (!isBlank(request.getLyrics()) && MusicLyricsRequest.MODE_EDIT.equals(request.getMode())) {
            body.put("lyrics", request.getLyrics());
        }
        if (!isBlank(request.getTitle())) body.put("title", request.getTitle());
        return body;
    }

    /** 解析 API 响应，公开给单元测试复用。 */
    public MusicResponse parseResponse(JSONObject root) {
        JSONObject base = root.optJSONObject("base_resp");
        int status = base != null ? base.optInt("status_code", -1) : root.optInt("status_code", -1);
        if (status != 0) {
            String message = base != null ? base.optString("status_msg", "MiniMax music error") : "MiniMax music error";
            throw new IllegalStateException("MiniMax music error " + status + ": " + message);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IllegalStateException("MiniMax music response has no data");
        Integer musicStatus = data.has("status") ? data.getInt("status") : null;
        if (musicStatus != null && musicStatus != 2) {
            throw new IllegalStateException("MiniMax music is not complete: status " + musicStatus);
        }
        String audio = data.optString("audio", null);
        String audioUrl = audio != null && audio.startsWith("http") ? audio : null;
        String audioHex = audioUrl == null ? audio : null;
        return new MusicResponse(audioHex, audioUrl,
                musicStatus, root.optString("trace_id", null), root.optJSONObject("extra_info"), root);
    }

    /** 解析翻唱前处理响应。 */
    public MusicCoverPreprocessResponse parseCoverPreprocessResponse(JSONObject root) {
        checkBaseResponse(root, "MiniMax music cover preprocess");
        return new MusicCoverPreprocessResponse(root.optString("cover_feature_id", null),
                root.optString("formatted_lyrics", null), root.optString("structure_result", null),
                root.has("audio_duration") ? root.getDouble("audio_duration") : null,
                root.optString("trace_id", null), root);
    }

    /** 解析歌词生成响应。 */
    public MusicLyricsResponse parseLyricsResponse(JSONObject root) {
        checkBaseResponse(root, "MiniMax lyrics generation");
        return new MusicLyricsResponse(root.optString("song_title", null),
                root.optString("style_tags", null), root.optString("lyrics", null), root);
    }

    private JSONObject postJson(String url, JSONObject body) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("MiniMax music requires an API key");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HttpUtils.createHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return new JSONObject(response.body());
    }

    private static void validateMusicOptions(String model, MusicRequest request, MusicOptions opts) {
        if (!Set.of("music-3.0", "music-2.6", "music-cover", "music-3.0-free",
                "music-2.6-free", "music-cover-free").contains(model)) {
            throw new IllegalArgumentException("unsupported music model: " + model);
        }
        if (opts.getOutputFormat() == null
                || !"hex".equals(opts.getOutputFormat()) && !"url".equals(opts.getOutputFormat())) {
            throw new IllegalArgumentException("outputFormat must be hex or url");
        }
        if (opts.getSampleRate() == null
                || !Set.of(16000, 24000, 32000, 44100).contains(opts.getSampleRate())) {
            throw new IllegalArgumentException("unsupported sample rate: " + opts.getSampleRate());
        }
        if (opts.getBitrate() == null
                || !Set.of(32000, 64000, 128000, 256000).contains(opts.getBitrate())) {
            throw new IllegalArgumentException("unsupported bitrate: " + opts.getBitrate());
        }
        if (opts.getFormat() == null || !Set.of("mp3", "wav", "pcm").contains(opts.getFormat())) {
            throw new IllegalArgumentException("unsupported audio format: " + opts.getFormat());
        }

        boolean cover = model.startsWith("music-cover");
        if (cover) {
            if (length(request.getPrompt()) < 10 || length(request.getPrompt()) > 300) {
                throw new IllegalArgumentException("music-cover prompt length must be between 10 and 300");
            }
            if (!isBlank(opts.getLyrics()) && (length(opts.getLyrics()) < 10 || length(opts.getLyrics()) > 1000)) {
                throw new IllegalArgumentException("music-cover lyrics length must be between 10 and 1000");
            }
            if (!isBlank(opts.getCoverFeatureId())
                    && (isBlank(opts.getLyrics()) || length(opts.getLyrics()) < 10)) {
                throw new IllegalArgumentException("lyrics is required when coverFeatureId is used");
            }
        } else {
            if (length(request.getPrompt()) > 2000 || length(opts.getLyrics()) > 3500) {
                throw new IllegalArgumentException("music prompt or lyrics exceeds max length");
            }
            if (Boolean.TRUE.equals(opts.getInstrumental()) && length(request.getPrompt()) < 1) {
                throw new IllegalArgumentException("prompt is required by instrumental music");
            }
        }
    }

    private static void checkBaseResponse(JSONObject root, String source) {
        JSONObject base = root.optJSONObject("base_resp");
        int status = base != null ? base.optInt("status_code", -1) : root.optInt("status_code", -1);
        if (status != 0) {
            String message = base != null ? base.optString("status_msg", "error") : "error";
            throw new IllegalStateException(source + " error " + status + ": " + message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int countPresent(String... values) {
        int count = 0;
        for (String value : values) {
            if (!isBlank(value)) count++;
        }
        return count;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String curl(String url, JSONObject body) {
        return "curl -X POST '" + url + "' \\\n"
                + "  -H 'Authorization: Bearer ****' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '" + body.toString().replace("'", "'\\''") + "'";
    }
}
