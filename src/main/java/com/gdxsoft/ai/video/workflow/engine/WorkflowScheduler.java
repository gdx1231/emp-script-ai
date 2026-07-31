package com.gdxsoft.ai.video.workflow.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.WorkflowConfig;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;

/**
 * 工作流调度器 — 轮询 DB 取 pending 实例，按并发限制分发给执行器。
 *
 * <p>架构：
 * <pre>
 * while (running) {
 *     if (workflowSemaphore.tryAcquire()) {
 *         instanceId = db.pollPendingInstance(engineId);
 *         if (instanceId != null) executor.submit(() -> executor.run(instanceId));
 *         else { workflowSemaphore.release(); sleep(pollIntervalMs); }
 *     } else { sleep(pollIntervalMs); }
 * }
 * </pre>
 *
 * @since 1.4.0
 */
public class WorkflowScheduler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowScheduler.class);

    private final WorkflowConfig config;
    private final WorkflowDb db;
    private final WorkflowExecutor executor;
    private final ExecutorService threadPool;
    private final Semaphore workflowSemaphore;
    private final String engineId;

    private volatile boolean running = true;
    private long pollIntervalMs;

    public WorkflowScheduler(WorkflowConfig config, WorkflowDb db,
                              WorkflowExecutor executor, ExecutorService threadPool,
                              Semaphore workflowSemaphore, String engineId) {
        this.config = config;
        this.db = db;
        this.executor = executor;
        this.threadPool = threadPool;
        this.workflowSemaphore = workflowSemaphore;
        this.engineId = engineId;
        this.pollIntervalMs = config.getLimits().getPollIntervalMs();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        LOGGER.info("调度器启动: engineId={}, maxConcurrent={}, pollInterval={}ms",
                engineId, config.getLimits().getMaxConcurrentWorkflows(), pollIntervalMs);

        while (running) {
            try {
                // 1. 尝试获取工作流槽位
                if (workflowSemaphore.tryAcquire(0, TimeUnit.SECONDS)) {
                    try {
                        // 2. 查询 pending 实例并原子占位
                        Long instanceId = db.pollPendingInstance(engineId);
                        if (instanceId != null) {
                            // 3. 异步执行（不阻塞调度循环）
                            final long iid = instanceId;
                            threadPool.submit(() -> {
                                try {
                                    executor.run(iid);
                                } finally {
                                    workflowSemaphore.release();
                                }
                            });
                        } else {
                            // 无 pending 实例，释放槽位等待
                            workflowSemaphore.release();
                            Thread.sleep(pollIntervalMs);
                        }
                    } catch (Exception e) {
                        workflowSemaphore.release();
                        LOGGER.warn("调度异常: {}", e.getMessage());
                        Thread.sleep(pollIntervalMs);
                    }
                } else {
                    // 槽位已满，等待
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOGGER.info("调度器已停止");
    }
}
