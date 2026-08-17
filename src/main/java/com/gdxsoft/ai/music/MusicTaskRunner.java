/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: MusicTaskRunner.java
 * 描述: 音乐生成任务执行器，封装 provider 非阻塞调用 + 日志记录
 */
package com.gdxsoft.ai.music;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 音乐生成任务执行器，封装 provider 非阻塞调用 + 全部日志记录。
 * <p>
 * 调用方只需 submit / poll，日志自动完成（AI_CHAT / AI_CHAT_MSG）。
 * 所有 provider 均支持：由 {@link MusicProviderBase} 本地异步回退
 * （虚拟线程 + 内存注册表），原生异步 provider 可覆盖返回服务端 task_id。
 * <pre>{@code
 * MusicTaskRunner runner = new MusicTaskRunner(provider, MusicChatLogger.create(rv, "dbConfig"));
 * MusicTaskSubmit submit = runner.submit(request);
 * // ... 前端轮询 ...
 * MusicTaskStatus status = runner.poll(taskId, opts);
 * }</pre>
 *
 * @since 1.5.0
 */
public class MusicTaskRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicTaskRunner.class);

    private final IMusicProvider provider;
    private final MusicChatLogger logger;

    public MusicTaskRunner(IMusicProvider provider, MusicChatLogger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * 提交任务（非阻塞），自动记录：用户消息 + curl + 创建返回。
     */
    public MusicTaskSubmit submit(MusicRequest request) throws Exception {
        MusicOptions opts = request.getOptions();

        // 日志：用户消息 + 参数
        if (logger != null) {
            logger.logStart(provider.getProviderType().getName(),
                    opts.getModel(), "music_generate", "music_generate",
                    request.getPrompt(), buildOptsJson(opts));
            logger.logCurl(provider.curl(request));
        }

        // 提交（非阻塞）
        MusicTaskSubmit result = provider.submitTask(request);
        LOGGER.debug("音乐任务已提交 provider={}, taskId={}",
                provider.getProviderType().getName(), result.getTaskId());

        // 日志：创建任务原始返回（本地回退时 raw 为 null，自动跳过）
        if (logger != null) {
            logger.logRawResponse("创建任务返回", result.getRaw());
        }

        return result;
    }

    /**
     * 查询任务状态（非阻塞），自动记录：查询返回 + 成功/失败结果。
     */
    public MusicTaskStatus poll(String taskId, MusicOptions opts) throws Exception {
        MusicTaskStatus st = provider.pollTask(taskId, opts);

        if (logger == null) return st;

        // 日志：查询结果原始返回
        logger.logRawResponse("查询结果返回", st.getRaw());

        if (st.isSucceeded()) {
            logger.logMusicSuccess(st.getResponse());
        } else if (st.isFailed()) {
            logger.logError(new Exception(st.getError()));
        }

        return st;
    }

    /** @return 日志记录器（用于获取 aiId / requestId），可能为 null */
    public MusicChatLogger getLogger() { return logger; }

    /** @return provider 实例 */
    public IMusicProvider getProvider() { return provider; }

    private static JSONObject buildOptsJson(MusicOptions opts) {
        JSONObject j = new JSONObject();
        if (opts.getModel() != null) j.put("model", opts.getModel());
        if (opts.getLyrics() != null) j.put("lyrics", opts.getLyrics());
        j.put("stream", opts.isStream());
        if (opts.getOutputFormat() != null) j.put("output_format", opts.getOutputFormat());
        if (opts.getSampleRate() != null) j.put("sample_rate", opts.getSampleRate());
        if (opts.getBitrate() != null) j.put("bitrate", opts.getBitrate());
        if (opts.getFormat() != null) j.put("format", opts.getFormat());
        if (opts.getWatermark() != null) j.put("watermark", opts.getWatermark());
        if (opts.getLyricsOptimizer() != null) j.put("lyrics_optimizer", opts.getLyricsOptimizer());
        if (opts.getInstrumental() != null) j.put("instrumental", opts.getInstrumental());
        if (opts.getAudioUrl() != null) j.put("audio_url", opts.getAudioUrl());
        if (opts.getAudioBase64() != null) j.put("audio_base64_length", opts.getAudioBase64().length());
        if (opts.getCoverFeatureId() != null) j.put("cover_feature_id", opts.getCoverFeatureId());
        return j.length() > 0 ? j : null;
    }
}
