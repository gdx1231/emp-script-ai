/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: ImgTaskStatus.java
 * 描述: 图片生成任务轮询状态（非阻塞模式）
 */
package com.gdxsoft.ai.img;

import org.json.JSONObject;

/**
 * 图片生成任务轮询状态。
 * <p>
 * 非阻塞模式下 {@code pollTask(taskId, opts)} 的返回值。
 * <ul>
 *   <li>{@code processing} — 任务进行中，{@link #getResponse()} 为 null</li>
 *   <li>{@code succeeded} — 任务完成，{@link #getResponse()} 包含图片结果</li>
 *   <li>{@code failed} — 任务失败，{@link #getError()} 包含错误信息</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class ImgTaskStatus {
    private final String status;          // "processing", "succeeded", "failed"
    private final ImgResponse response;   // succeeded 时非 null
    private final String error;           // failed 时非 null
    private final JSONObject raw;

    public ImgTaskStatus(String status, ImgResponse response, String error, JSONObject raw) {
        this.status = status;
        this.response = response;
        this.error = error;
        this.raw = raw;
    }

    /** @return 任务状态：processing / succeeded / failed */
    public String getStatus() { return status; }

    /** @return 图片生成结果（仅 succeeded 时非 null） */
    public ImgResponse getResponse() { return response; }

    /** @return 错误信息（仅 failed 时非 null） */
    public String getError() { return error; }

    /** @return 原始响应 JSON */
    public JSONObject getRaw() { return raw; }

    /** @return 是否仍在处理中 */
    public boolean isProcessing() { return "processing".equals(status); }

    /** @return 是否已成功完成 */
    public boolean isSucceeded() { return "succeeded".equals(status) || "completed".equals(status) || "success".equals(status); }

    /** @return 是否失败 */
    public boolean isFailed() { return "failed".equals(status) || "cancelled".equals(status); }
}
