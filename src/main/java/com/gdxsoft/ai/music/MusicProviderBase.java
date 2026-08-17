package com.gdxsoft.ai.music;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.gdxsoft.ai.HttpUtils;

/**
 * 音乐 Provider 公共配置基类。
 */
public abstract class MusicProviderBase implements IMusicProvider {
    protected volatile String apiUrl;
    protected volatile String apiKey;
    protected final Map<String, String> extras = new ConcurrentHashMap<>();

    @Override
    public String getApiUrl() { return apiUrl; }
    @Override
    public void setApiUrl(String url) { this.apiUrl = url; }

    @Override
    public String getApiKey() { return apiKey; }
    @Override
    public void setApiKey(String key) { this.apiKey = key; }

    @Override
    public void setConfig(String key, String value) {
        if (key == null) return;
        if (value == null) extras.remove(key);
        else extras.put(key, value);
    }

    @Override
    public String getConfig(String key) {
        return key == null ? null : extras.get(key);
    }

    /** 本地异步回退的任务注册表：taskId -> 任务状态（含提交时间戳） */
    private final Map<String, LocalTask> localTasks = new ConcurrentHashMap<>();
    /** 本地任务注册表条目存活时间（终态任务超过该时间在下次 submit 时清理） */
    private static final long LOCAL_TASK_TTL_MS = 3600_000L;

    // ======================== 本地异步回退（submit / poll） ========================

    /**
     * 本地异步回退：提交任务（非阻塞）。
     * <p>
     * 仅同步 REST 的 provider 通过本方法获得统一异步能力：虚拟线程中执行
     * {@link #generate(MusicRequest)}，结果写入内存注册表，立即返回 taskId。
     * <p>
     * <b>注意</b>：taskId 为 {@code local-} 前缀，仅在本 JVM 进程内有效，
     * 进程重启后任务状态丢失。原生异步 provider 可覆盖此方法返回服务端 task_id。
     */
    @Override
    public MusicTaskSubmit submitTask(MusicRequest request) throws Exception {
        purgeExpiredLocalTasks();
        String taskId = "local-" + UUID.randomUUID();
        localTasks.put(taskId, new LocalTask(new MusicTaskStatus("processing", null, null, null)));

        Executor executor = HttpUtils.createVirtualThreadExecutorService();
        executor.execute(() -> {
            try {
                MusicResponse resp = generate(request);
                localTasks.put(taskId, new LocalTask(
                        new MusicTaskStatus("succeeded", resp, null, resp.getRaw())));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                localTasks.put(taskId, new LocalTask(new MusicTaskStatus("failed", null, msg, null)));
            }
        });
        return new MusicTaskSubmit(taskId, null);
    }

    /**
     * 本地异步回退：轮询任务状态（非阻塞）。
     * <p>
     * 从内存注册表读取当前状态；未知 taskId 抛出 {@link IllegalArgumentException}。
     */
    @Override
    public MusicTaskStatus pollTask(String taskId, MusicOptions opts) throws Exception {
        LocalTask task = localTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown music taskId: " + taskId);
        }
        return task.status;
    }

    private void purgeExpiredLocalTasks() {
        long now = System.currentTimeMillis();
        localTasks.entrySet().removeIf(e -> {
            MusicTaskStatus st = e.getValue().status;
            return (st.isSucceeded() || st.isFailed())
                    && now - e.getValue().createdAt > LOCAL_TASK_TTL_MS;
        });
    }

    private static class LocalTask {
        final MusicTaskStatus status;
        final long createdAt;

        LocalTask(MusicTaskStatus status) {
            this.status = status;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
