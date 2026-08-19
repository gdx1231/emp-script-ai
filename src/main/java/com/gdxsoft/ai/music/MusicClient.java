package com.gdxsoft.ai.music;

import java.io.IOException;
import java.nio.file.Path;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 音乐生成高层门面。 */
public final class MusicClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicClient.class);
    private final IMusicProvider provider;
    private MusicChatLogger chatLogger;

    public MusicClient(IMusicProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    public static MusicClient of(String providerName) {
        return new MusicClient(MusicProviderFactory.create(providerName));
    }

    public static MusicClient of(IMusicProvider provider) {
        return new MusicClient(provider);
    }

    public MusicClient apiKey(String key) {
        provider.setApiKey(key); return this;
    }

    public MusicClient apiUrl(String url) {
        provider.setApiUrl(url); return this;
    }

    public MusicClient config(String key, String value) {
        provider.setConfig(key, value); return this;
    }

    /** 启用 AI_CHAT / AI_CHAT_MSG 持久化日志。 */
    public MusicClient chatLogger(MusicChatLogger chatLogger) {
        this.chatLogger = chatLogger; return this;
    }

    public MusicClient chatLogger(String dbConfigName) {
        return chatLogger(MusicChatLogger.create(dbConfigName));
    }

    public MusicResponse generate(String prompt, MusicOptions options)
            throws IOException, InterruptedException {
        return generate(new MusicRequest(prompt, options));
    }

    public MusicResponse generate(MusicRequest request) throws IOException, InterruptedException {
        String providerName = provider.getProviderType().getName();
        String curl = provider.curl(request);
        LOGGER.info("Music curl [{}]: {}", providerName, curl);
        if (chatLogger != null) {
            chatLogger.logStart(providerName, request.getOptions().getModel(), "music", "music_generate",
                    request.getPrompt(), buildMusicOptionsJson(request.getOptions()));
            chatLogger.logCurl(curl);
        }
        try {
            MusicResponse response = provider.generate(request);
            if (chatLogger != null) chatLogger.logMusicSuccess(response);
            return response;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (chatLogger != null) chatLogger.logError(e);
            throw e;
        }
    }

    public MusicCoverPreprocessResponse preprocessCover(MusicCoverPreprocessRequest request)
            throws IOException, InterruptedException {
        String providerName = provider.getProviderType().getName();
        String curl = provider.curl(request);
        LOGGER.info("Music cover preprocess curl [{}]: {}", providerName, curl);
        if (chatLogger != null) {
            chatLogger.logStart(providerName, "music-cover", "music", "music_cover_preprocess",
                    null, buildCoverPreprocessJson(request));
            chatLogger.logCurl(curl);
        }
        try {
            MusicCoverPreprocessResponse response = provider.preprocessCover(request);
            if (chatLogger != null) chatLogger.logPreprocessSuccess(response);
            return response;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (chatLogger != null) chatLogger.logError(e);
            throw e;
        }
    }

    public MusicLyricsResponse generateLyrics(MusicLyricsRequest request)
            throws IOException, InterruptedException {
        String providerName = provider.getProviderType().getName();
        String curl = provider.curl(request);
        LOGGER.info("Music lyrics curl [{}]: {}", providerName, curl);
        if (chatLogger != null) {
            chatLogger.logStart(providerName, null, "music_lyrics", "music_lyrics",
                    request.getPrompt(), buildLyricsJson(request));
            chatLogger.logCurl(curl);
        }
        try {
            MusicLyricsResponse response = provider.generateLyrics(request);
            if (chatLogger != null) chatLogger.logLyricsSuccess(response);
            return response;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (chatLogger != null) chatLogger.logError(e);
            throw e;
        }
    }

    /**
     * 完整创作流程：先根据创意生成标题、风格与歌词，再生成音乐。
     * <p>音乐 prompt 使用歌词接口返回的 style_tags；返回歌词与音乐响应便于调用方展示。
     */
    public MusicComposition compose(String creativePrompt, MusicOptions options)
            throws IOException, InterruptedException {
        MusicLyricsResponse lyrics = generateLyrics(MusicLyricsRequest.writeFullSong(creativePrompt));
        options.lyrics(lyrics.getLyrics());
        String musicPrompt = lyrics.getStyleTags() == null || lyrics.getStyleTags().isBlank()
                ? creativePrompt : lyrics.getStyleTags();
        MusicResponse music = generate(musicPrompt, options);
        return new MusicComposition(lyrics, music);
    }

    /**
     * 两步翻唱流程：先预处理参考音频获得 cover_feature_id 与 ASR 格式化歌词，
     * 再直接用二者发起翻唱生成；调用方可通过 revisedLyrics 覆盖识别出的歌词。
     */
    public MusicCoverComposition cover(MusicCoverPreprocessRequest request,
            String prompt, MusicOptions options, String revisedLyrics)
            throws IOException, InterruptedException {
        MusicCoverPreprocessResponse preprocess = preprocessCover(request);
        options.coverFeatureId(preprocess.getCoverFeatureId());
        options.lyrics(revisedLyrics == null || revisedLyrics.isBlank()
                ? preprocess.getFormattedLyrics() : revisedLyrics);
        MusicResponse music = generate(prompt, options);
        return new MusicCoverComposition(preprocess, music);
    }

    public MusicCoverComposition cover(MusicCoverPreprocessRequest request,
            String prompt, MusicOptions options) throws IOException, InterruptedException {
        return cover(request, prompt, options, null);
    }

    /** 生成并直接保存 hex 音频。 */
    public Path generateToFile(String prompt, MusicOptions options, Path output)
            throws IOException, InterruptedException {
        return generate(prompt, options).save(output);
    }

    public IMusicProvider getProvider() { return provider; }

    private JSONObject buildMusicOptionsJson(MusicOptions opts) {
        JSONObject json = new JSONObject();
        if (opts.getModel() != null) json.put("model", opts.getModel());
        if (opts.getLyrics() != null) json.put("lyrics", opts.getLyrics());
        json.put("output_format", opts.getOutputFormat());
        json.put("audio_setting", new JSONObject()
                .put("sample_rate", opts.getSampleRate())
                .put("bitrate", opts.getBitrate())
                .put("format", opts.getFormat()));
        if (opts.getWatermark() != null) json.put("aigc_watermark", opts.getWatermark());
        if (opts.getLyricsOptimizer() != null) json.put("lyrics_optimizer", opts.getLyricsOptimizer());
        if (opts.getInstrumental() != null) json.put("is_instrumental", opts.getInstrumental());
        if (opts.getAudioUrl() != null) json.put("audio_url", opts.getAudioUrl());
        if (opts.getAudioBase64() != null) json.put("audio_base64_length", opts.getAudioBase64().length());
        if (opts.getCoverFeatureId() != null) json.put("cover_feature_id", opts.getCoverFeatureId());
        return json;
    }

    private JSONObject buildCoverPreprocessJson(MusicCoverPreprocessRequest request) {
        JSONObject json = new JSONObject();
        if (request.getAudioUrl() != null) json.put("audio_url", request.getAudioUrl());
        if (request.getAudioBase64() != null) json.put("audio_base64_length", request.getAudioBase64().length());
        return json;
    }

    private JSONObject buildLyricsJson(MusicLyricsRequest request) {
        JSONObject json = new JSONObject();
        if (request.getMode() != null) json.put("mode", request.getMode());
        if (request.getLyrics() != null) json.put("lyrics", request.getLyrics());
        if (request.getTitle() != null) json.put("title", request.getTitle());
        return json;
    }
}
