package com.gdxsoft.ai.video.workflow.config;

/**
 * 工作流并发限制配置，对应 workflow.json 中的 limits 节点。
 *
 * <pre>{@code
 * "limits": {
 *   "maxConcurrentWorkflows": 3,
 *   "maxConcurrentImageGen": 3,
 *   "maxConcurrentVideoGen": 1,
 *   "maxRetries": 2,
 *   "retryDelayMs": 5000,
 *   "taskTimeoutMs": 600000,
 *   "pollIntervalMs": 5000
 * }
 * }</pre>
 *
 * @since 1.4.0
 */
public class LimitsConfig {

    /** 同时执行的最大工作流实例数，默认 3 */
    private int maxConcurrentWorkflows = 3;

    /** 同时并发的图片生成请求数，默认 3 */
    private int maxConcurrentImageGen = 3;

    /** 同时并发的视频生成请求数，默认 1（尾帧续接需串行） */
    private int maxConcurrentVideoGen = 1;

    /** 单 task 最大重试次数，默认 2 */
    private int maxRetries = 2;

    /** 重试间隔毫秒，默认 5000 */
    private long retryDelayMs = 5000;

    /** 单 task 超时毫秒，默认 600000 (10 min) */
    private long taskTimeoutMs = 600000;

    /** 调度轮询间隔毫秒，默认 5000 */
    private long pollIntervalMs = 5000;

    // ===== Getters =====

    public int getMaxConcurrentWorkflows() { return maxConcurrentWorkflows; }
    public void setMaxConcurrentWorkflows(int v) { this.maxConcurrentWorkflows = v; }

    public int getMaxConcurrentImageGen() { return maxConcurrentImageGen; }
    public void setMaxConcurrentImageGen(int v) { this.maxConcurrentImageGen = v; }

    public int getMaxConcurrentVideoGen() { return maxConcurrentVideoGen; }
    public void setMaxConcurrentVideoGen(int v) { this.maxConcurrentVideoGen = v; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int v) { this.maxRetries = v; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long v) { this.retryDelayMs = v; }

    public long getTaskTimeoutMs() { return taskTimeoutMs; }
    public void setTaskTimeoutMs(long v) { this.taskTimeoutMs = v; }

    public long getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(long v) { this.pollIntervalMs = v; }
}
