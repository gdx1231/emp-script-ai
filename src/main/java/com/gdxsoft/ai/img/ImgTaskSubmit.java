/*
 * Copyright (c) 2025 GDX Software
 *
 * 文件名: ImgTaskSubmit.java
 * 描述: 图片生成任务提交结果（非阻塞模式）
 */
package com.gdxsoft.ai.img;

import org.json.JSONObject;

/**
 * 图片生成任务提交结果。
 * <p>
 * 非阻塞模式下 {@code submitTask()} 的返回值，仅包含 taskId，
 * 后续通过 {@code pollTask(taskId, opts)} 轮询获取最终结果。
 * <p>
 * 本地异步回退（{@link ImgProviderBase} 默认实现）下 taskId 为 {@code local-} 前缀，
 * 仅在本 JVM 进程内有效；原生异步 provider（如 Wanx）返回服务端 task_id。
 *
 * @since 1.4.0
 */
public class ImgTaskSubmit {
    private final String taskId;
    private final JSONObject raw;

    public ImgTaskSubmit(String taskId, JSONObject raw) {
        this.taskId = taskId;
        this.raw = raw;
    }

    /** @return 任务 ID，用于后续轮询 */
    public String getTaskId() { return taskId; }

    /** @return 原始响应 JSON（本地回退时为 null） */
    public JSONObject getRaw() { return raw; }
}
