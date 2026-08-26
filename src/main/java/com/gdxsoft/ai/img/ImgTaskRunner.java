/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: ImgTaskRunner.java
 * 描述: 图片生成任务执行器，封装 provider 非阻塞调用 + 日志记录
 */
package com.gdxsoft.ai.img;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图片生成任务执行器，封装 provider 非阻塞调用 + 全部日志记录。
 * <p>
 * 调用方只需 submit / poll，日志自动完成（AI_CHAT / AI_CHAT_MSG）。
 * 所有 provider 均支持：原生异步 provider（如 Qwen Wanx）走服务端 task_id，
 * 其余 provider 由 {@link ImgProviderBase} 本地异步回退（虚拟线程 + 内存注册表）。
 * <pre>{@code
 * ImgTaskRunner runner = new ImgTaskRunner(provider, ImgChatLogger.create(rv, "dbConfig"));
 * ImgTaskSubmit submit = runner.submit(request);
 * // ... 前端轮询 ...
 * ImgTaskStatus status = runner.poll(taskId, opts);
 * }</pre>
 *
 * @since 1.4.0
 */
public class ImgTaskRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImgTaskRunner.class);

    private final IImgProvider provider;
    private final ImgChatLogger logger;

    public ImgTaskRunner(IImgProvider provider, ImgChatLogger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * 提交任务（非阻塞），自动记录：用户消息 + curl + 创建返回。
     */
    public ImgTaskSubmit submit(ImgRequest request) throws Exception {
        ImgOptions opts = request.getOptions();

        // 日志：用户消息 + 参数
        if (logger != null) {
            logger.logStart(provider.getProviderType().getName(),
                    opts.getModel(), opts.getPrompt(), buildOptsJson(opts));
            logger.logCurl(provider.curl(request));
        }

        // 提交（非阻塞）
        ImgTaskSubmit result = provider.submitTask(request);
        LOGGER.debug("图片任务已提交 provider={}, taskId={}",
                provider.getProviderType().getName(), result.getTaskId());

        // 日志：创建任务原始返回（本地回退时 raw 为 null，自动跳过）
        // 原生异步任务写入查询状态 curl；本地回退（local-）无服务端查询端点，记录说明
        if (logger != null) {
            logger.logRawResponse("创建任务返回", result.getRaw());
            String tid = result.getTaskId();
            if (tid != null && !tid.isEmpty()) {
                if (tid.startsWith("local-")) {
                    logger.logCurl("# 本地回退任务 " + tid + "：无服务端查询端点，结果仅在本 worker JVM 内存，无法用 curl 查询状态");
                } else {
                    String queryCurl = provider.queryCurl(tid);
                    if (queryCurl != null && !queryCurl.isEmpty()) {
                        logger.logCurl("# 查询任务状态（task_id=" + tid + "）\n" + queryCurl);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 查询任务状态（非阻塞），自动记录：查询返回 + 成功/失败结果。
     */
    public ImgTaskStatus poll(String taskId, ImgOptions opts) throws Exception {
        ImgTaskStatus st = provider.pollTask(taskId, opts);

        if (logger == null) return st;

        // 日志：查询结果原始返回
        logger.logRawResponse("查询结果返回", st.getRaw());

        if (st.isSucceeded()) {
            logger.logSuccess(st.getResponse());
        } else if (st.isFailed()) {
            logger.logError(new Exception(st.getError()));
        }

        return st;
    }

    /** @return 日志记录器（用于获取 aiId / requestId），可能为 null */
    public ImgChatLogger getLogger() { return logger; }

    /** @return provider 实例 */
    public IImgProvider getProvider() { return provider; }

    private static JSONObject buildOptsJson(ImgOptions opts) {
        JSONObject j = new JSONObject();
        if (opts.getSize() != null) j.put("size", opts.getSize());
        if (opts.getQuality() != null) j.put("quality", opts.getQuality());
        if (opts.getStyle() != null) j.put("style", opts.getStyle());
        if (opts.getN() != null) j.put("n", opts.getN());
        if (opts.getResponseFormat() != null) j.put("response_format", opts.getResponseFormat());
        if (opts.getNegativePrompt() != null) j.put("negative_prompt", opts.getNegativePrompt());
        if (opts.getSteps() != null) j.put("steps", opts.getSteps());
        if (opts.getSeed() != null) j.put("seed", opts.getSeed());
        if (opts.getRefImageUrl() != null) j.put("ref_image", opts.getRefImageUrl());
        if (opts.getRefStrength() != null) j.put("ref_strength", opts.getRefStrength());
        if (opts.getRefMode() != null) j.put("ref_mode", opts.getRefMode());
        return j.length() > 0 ? j : null;
    }
}
