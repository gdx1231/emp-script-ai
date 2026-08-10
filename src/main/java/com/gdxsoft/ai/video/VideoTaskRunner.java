/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: VideoTaskRunner.java
 * 描述: 视频生成任务执行器，封装 provider 调用 + 日志记录
 */
package com.gdxsoft.ai.video;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 视频生成任务执行器，封装 provider 非阻塞调用 + 全部日志记录。
 * <p>
 * 调用方只需 submit / poll，日志自动完成（AI_CHAT / AI_CHAT_MSG）。
 * <pre>{@code
 * VideoTaskRunner runner = new VideoTaskRunner(provider, VideoChatLogger.create(rv, "dbConfig"));
 * VideoTaskSubmit submit = runner.submit(request);
 * // ... 前端轮询 ...
 * VideoTaskStatus status = runner.poll(taskId);
 * }</pre>
 *
 * @since 1.3.0
 */
public class VideoTaskRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoTaskRunner.class);

    private final IVideoProvider provider;
    private final VideoChatLogger logger;
    private VideoRequest lastRequest;

    public VideoTaskRunner(IVideoProvider provider, VideoChatLogger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * 提交任务（非阻塞），自动记录：用户消息 + curl + 创建返回。
     */
    public VideoTaskSubmit submit(VideoRequest request) throws Exception {
        this.lastRequest = request;

        // 日志：用户消息 + 参数
        if (logger != null) {
            VideoOptions opts = request.getOptions();
            JSONObject optsJson = buildOptsJson(opts);
            logger.logStart(provider.getProviderType().getName(),
                    opts.getModel(), opts.getPrompt(), optsJson);
            logger.logCurl(curlOf(request));
        }

        // 提交（非阻塞）
        VideoTaskSubmit result = provider.submitTask(request);

        // 日志：创建任务原始返回
        if (logger != null) {
            logger.logRawResponse("创建任务返回", result.getRaw());
        }

        return result;
    }

    /**
     * 查询任务状态（非阻塞），自动记录：查询返回 + 成功/失败结果。
     */
    public VideoTaskStatus poll(String taskId, VideoOptions opts) throws Exception {
        VideoTaskStatus st = provider.pollTask(taskId, opts);

        if (logger == null) return st;

        // 日志：查询结果原始返回
        logger.logRawResponse("查询结果返回", st.getRaw());

        if (st.isSucceeded()) {
            String respCurl = "curl '" + provider.getApiUrl() + "/" + taskId
                    + "' -H 'Authorization: Bearer " + provider.getApiKey() + "'";
            logger.logSuccess(st.getResponse(), respCurl);
        } else if (st.isFailed()) {
            logger.logError(new Exception(st.getError()));
        }

        return st;
    }

    /** @return 日志记录器（用于获取 aiId / requestId） */
    public VideoChatLogger getLogger() { return logger; }

    /** @return provider 实例 */
    public IVideoProvider getProvider() { return provider; }

    private String curlOf(VideoRequest request) {
        return provider.curl(request);
    }

    private JSONObject buildOptsJson(VideoOptions opts) {
        JSONObject j = new JSONObject();
        if (opts.getDuration() != null) j.put("duration", opts.getDuration());
        if (opts.getAspectRatio() != null) j.put("ratio", opts.getAspectRatio());
        if (opts.getResolution() != null) j.put("resolution", opts.getResolution());
        if (opts.getGenerateAudio() != null) j.put("generate_audio", opts.getGenerateAudio());
        if (opts.getEnableWebSearch() != null) j.put("web_search", opts.getEnableWebSearch());
        if (opts.getReturnLastFrame() != null) j.put("return_last_frame", opts.getReturnLastFrame());
        if (opts.getServiceTier() != null) j.put("service_tier", opts.getServiceTier());
        if (opts.getRefImageUrls() != null) j.put("ref_images", opts.getRefImageUrls());
        if (opts.getRefVideoUrls() != null) j.put("ref_videos", opts.getRefVideoUrls());
        if (opts.getRefAudioUrls() != null) j.put("ref_audios", opts.getRefAudioUrls());
        return j;
    }
}
