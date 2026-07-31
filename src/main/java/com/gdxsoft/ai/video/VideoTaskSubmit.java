/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: VideoTaskSubmit.java
 * 描述: 视频生成任务提交结果（非阻塞模式）
 */
package com.gdxsoft.ai.video;

import org.json.JSONObject;

/**
 * 视频生成任务提交结果。
 * <p>
 * 非阻塞模式下 {@code submitTask()} 的返回值，仅包含 taskId，
 * 后续通过 {@code pollTask(taskId)} 轮询获取最终结果。
 *
 * @since 1.3.0
 */
public class VideoTaskSubmit {
    private final String taskId;
    private final JSONObject raw;

    public VideoTaskSubmit(String taskId, JSONObject raw) {
        this.taskId = taskId;
        this.raw = raw;
    }

    /** @return 任务 ID，用于后续轮询 */
    public String getTaskId() { return taskId; }

    /** @return 原始响应 JSON */
    public JSONObject getRaw() { return raw; }
}
