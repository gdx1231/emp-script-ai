package com.gdxsoft.ai.img;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;

/**
 * Common scaffolding for image generation providers — config map, defaults,
 * apiUrl/apiKey state.
 * <p>
 * Providers are <b>thread-safe</b> — apiKey/apiUrl are volatile,
 * extras uses {@link ConcurrentHashMap}. A single provider instance can be
 * shared across threads.
 * <p>
 * Concrete providers must implement {@link #generate(ImgRequest)} and
 * {@link #curl(ImgRequest)}. Transport is via {@code HttpUtils.createHttpClient()}.
 *
 * @since 1.2.0
 */
public abstract class ImgProviderBase implements IImgProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ImgProviderBase.class);

    protected volatile String apiUrl;
    protected volatile String apiKey;
    protected final Map<String, String> extras = new ConcurrentHashMap<>();

    /** 本地异步回退的任务注册表：taskId -> 任务状态（含提交时间戳） */
    private final Map<String, LocalTask> localTasks = new ConcurrentHashMap<>();
    /** 本地任务注册表条目存活时间（终态任务超过该时间在下次 submit 时清理） */
    private static final long LOCAL_TASK_TTL_MS = 3600_000L;

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

    // ======================== 本地异步回退（submit / poll） ========================

    /**
     * 本地异步回退：提交任务（非阻塞）。
     * <p>
     * 仅同步 REST 的 provider 通过本方法获得统一异步能力：虚拟线程中执行
     * {@link #generate(ImgRequest)}，结果写入内存注册表，立即返回 taskId。
     * <p>
     * <b>注意</b>：taskId 为 {@code local-} 前缀，仅在本 JVM 进程内有效，
     * 进程重启后任务状态丢失。原生异步 provider（如 Qwen Wanx）覆盖此方法，
     * 返回服务端 task_id。
     */
    @Override
    public ImgTaskSubmit submitTask(ImgRequest request) throws Exception {
        purgeExpiredLocalTasks();
        String taskId = "local-" + UUID.randomUUID();
        localTasks.put(taskId, new LocalTask(new ImgTaskStatus("processing", null, null, null)));

        Executor executor = HttpUtils.createVirtualThreadExecutorService();
        executor.execute(() -> {
            try {
                ImgResponse resp = generate(request);
                localTasks.put(taskId, new LocalTask(new ImgTaskStatus("succeeded", resp, null, resp.getRaw())));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                localTasks.put(taskId, new LocalTask(new ImgTaskStatus("failed", null, msg, null)));
                LOGGER.warn("本地异步图片任务失败 taskId={}: {}", taskId, msg);
            }
        });
        return new ImgTaskSubmit(taskId, null);
    }

    /**
     * 本地异步回退：查询任务状态（非阻塞）。
     *
     * @throws IllegalArgumentException taskId 不存在时
     */
    @Override
    public ImgTaskStatus pollTask(String taskId, ImgOptions opts) throws Exception {
        LocalTask task = localTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在（本地任务仅在当前 JVM 进程内有效）: " + taskId);
        }
        return task.status;
    }

    /** 清理超过 TTL 的终态（succeeded/failed）本地任务，防止内存膨胀 */
    private void purgeExpiredLocalTasks() {
        long now = System.currentTimeMillis();
        localTasks.entrySet().removeIf(e -> {
            ImgTaskStatus st = e.getValue().status;
            return (st.isSucceeded() || st.isFailed()) && now - e.getValue().createdAt > LOCAL_TASK_TTL_MS;
        });
    }

    /** 本地任务注册表条目 */
    private static class LocalTask {
        final ImgTaskStatus status;
        final long createdAt = System.currentTimeMillis();

        LocalTask(ImgTaskStatus status) {
            this.status = status;
        }
    }


    /**
     * Build a debug curl line header. Sensitive headers are masked.
     */
    protected StringBuilder curlHeader(StringBuilder sb, String name, String value, boolean isSensitive) {
        sb.append("-H '").append(name).append(": ");
        if (value == null) sb.append("'");
        else if (isSensitive) sb.append("****'");
        else sb.append(value.replace("'", "'\\''")).append("'");
        return sb;
    }

    // ======================== Reference image resolution ========================

    /**
     * Resolve reference images from {@link ImgOptions}.
     * <p>
     * Prefers {@code refImageUrls} (plural), falls back to {@code refImageUrl} (singular).
     * Filters out {@code null} and empty entries. Returns {@code null} if no valid references.
     *
     * @param opts image options
     * @return list of valid image URLs, or {@code null} if none
     */
    protected static List<String> resolveRefImages(ImgOptions opts) {
        return resolveRefImages(opts, 0);
    }

    /**
     * Resolve reference images with optional truncation.
     * <p>
     * Same as {@link #resolveRefImages(ImgOptions)} but limits the result to at most
     * {@code maxCount} entries. Use {@code maxCount <= 0} for no limit.
     *
     * @param opts     image options
     * @param maxCount max number of images to return; &lt;= 0 means no limit
     * @return list of valid image URLs (size &lt;= maxCount if maxCount &gt; 0),
     *         or {@code null} if none
     */
    protected static List<String> resolveRefImages(ImgOptions opts, int maxCount) {
        if (opts == null) {
            return null;
        }
        List<String> urls = opts.getRefImageUrls();
        List<String> result = null;
        if (urls != null) {
            for (String u : urls) {
                if (u != null && !u.isEmpty()) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(u);
                    if (maxCount > 0 && result.size() >= maxCount) {
                        break;
                    }
                }
            }
        }
        if (result != null) {
            return result;
        }
        String single = opts.getRefImageUrl();
        if (single != null && !single.isEmpty()) {
            return Collections.singletonList(single);
        }
        return null;
    }
}
