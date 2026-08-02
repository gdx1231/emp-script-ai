package com.gdxsoft.ai.video.workflow.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.LimitsConfig;
import com.gdxsoft.ai.video.workflow.config.WorkflowConfig;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.easyweb.script.RequestValue;
import com.sun.net.httpserver.HttpServer;

/**
 * 工作流引擎主进程。
 *
 * <p>职责：
 * <ul>
 *   <li>加载 workflow.json 配置并注册到 DB</li>
 *   <li>启动调度器线程（轮询 + 并发控制）</li>
 *   <li>启动 HTTP 状态服务（可选）</li>
 *   <li>注册 shutdown hook 实现优雅关闭</li>
 * </ul>
 *
 * <p>启动方式：
 * <pre>{@code
 * // 方式1: 通过 CLI
 * WorkflowCli.main(new String[]{"start", "--config", "workflow.json"});
 *
 * // 方式2: 编程
 * WorkflowEngine engine = new WorkflowEngine(Paths.get("workflow.json"), "default");
 * engine.start();
 * Thread.currentThread().join(); // 阻塞
 * }</pre>
 *
 * @since 1.4.0
 */
public class WorkflowEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowConfig config;
    private final WorkflowDb db;
    private final String engineId;
    private final ExecutorService threadPool;

    private WorkflowScheduler scheduler;
    private Thread schedulerThread;
    private HttpServer httpServer;
    private int statusPort = 8181;

    /**
     * 构造引擎。
     *
     * @param configPath   workflow.json 文件路径
     * @param dbConfigName 数据库配置名称（ewa_conf.xml 中的配置节）
     */
    public WorkflowEngine(Path configPath, String dbConfigName) throws IOException {
        this(WorkflowConfig.load(configPath), dbConfigName);
    }

    /**
     * 构造引擎（使用已加载的配置）。
     */
    public WorkflowEngine(WorkflowConfig config, String dbConfigName) {
        this.config = config;
        this.engineId = buildEngineId();
        this.db = new WorkflowDb(new RequestValue(), dbConfigName);
        this.threadPool = com.gdxsoft.ai.HttpUtils.createVirtualThreadExecutorService();

        LOGGER.info("工作流引擎创建: engineId={}, name={}, dbConfig={}",
                engineId, config.getName(), dbConfigName);
    }

    /**
     * 启动引擎。
     * <ol>
     *   <li>注册工作流定义到 DB（MD5 去重）</li>
     *   <li>恢复中断的实例</li>
     *   <li>创建信号量并启动调度器</li>
     *   <li>注册 shutdown hook</li>
     * </ol>
     */
    public void start() {
        // 1. 注册工作流定义
        long wfdId = db.registerWorkflowDef(config.getName(), config.getDescription(),
                config.getVersion(), config.getRawJson());
        LOGGER.info("工作流定义已注册: WFD_ID={}, name={}", wfdId, config.getName());

        // 2. 恢复中断的实例
        recoverInstances();

        // 3. 创建信号量
        LimitsConfig limits = config.getLimits();
        Semaphore workflowSemaphore = new Semaphore(limits.getMaxConcurrentWorkflows());

        // 4. 创建执行器和调度器
        WorkflowExecutor executor = new WorkflowExecutor(config, db, engineId);
        scheduler = new WorkflowScheduler(config, db, executor, threadPool,
                workflowSemaphore, engineId);

        // 5. 启动调度线程（JDK 21+ 用虚拟线程，否则用普通线程）
        schedulerThread = startVirtualOrPlatformThread("wf-scheduler", scheduler);

        // 6. 启动 HTTP 状态服务
        startStatusServer(8181);

        // 7. 注册 shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("收到 shutdown 信号，正在关闭...");
            stop();
        }));

        LOGGER.info("工作流引擎已启动: engineId={}, maxConcurrentWorkflows={}, phases={}",
                engineId,
                limits.getMaxConcurrentWorkflows(),
                config.getPhaseCount());
    }

    /** 优雅关闭 */
    public void stop() {
        LOGGER.info("关闭工作流引擎...");
        if (scheduler != null) scheduler.stop();
        if (schedulerThread != null) schedulerThread.interrupt();
        if (httpServer != null) httpServer.stop(1);
        threadPool.shutdown();
        LOGGER.info("工作流引擎已关闭");
    }

    /** 阻塞主线程直到引擎停止 */
    public void join() throws InterruptedException {
        if (schedulerThread != null) {
            schedulerThread.join();
        }
    }

    /** 构建引擎标识 */
    private String buildEngineId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return host + "-" + pid;
        } catch (Exception e) {
            return "engine-" + System.currentTimeMillis();
        }
    }

    /** 恢复中断的实例 */
    private void recoverInstances() {
        try {
            var instances = db.queryRecoverableInstances(engineId);
            if (instances.isEmpty()) return;

            LOGGER.warn("发现 {} 个中断实例，重置为 pending 等待重新调度",
                    instances.size());
            for (var inst : instances) {
                long id = inst.optLong("WFI_ID");
                String status = inst.optString("WFI_STATUS");
                String phase = inst.optString("WFI_CUR_PHASE");
                db.resetInstanceToPending(id);
                LOGGER.info("重置实例 WFI_ID={} (status={}, phase={})", id, status, phase);
            }

            // 同时恢复远端任务
            for (var inst : instances) {
                long id = inst.optLong("WFI_ID");
                var tasks = db.queryRecoverableRemoteTasks(id);
                for (var task : tasks) {
                    long taskId = task.optLong("WFT_ID");
                    String remoteId = task.optString("WFT_REMOTE_ID");
                    LOGGER.warn("发现未完成的远端任务: WFT_ID={}, remoteId={}, 将重新轮询",
                            taskId, remoteId);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("恢复中断实例异常: {}", e.getMessage());
        }
    }

    // ===== HTTP 状态服务 =====

    private void startStatusServer(int port) {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", exchange -> {
                JSONObject status = new JSONObject();
                status.put("engineId", engineId);
                status.put("workflow", config.getName());
                status.put("status", "running");
                status.put("maxConcurrent", config.getLimits().getMaxConcurrentWorkflows());
                status.put("phases", config.getPhaseCount());
                String json = status.toString(2);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            httpServer.setExecutor(com.gdxsoft.ai.HttpUtils.createVirtualThreadExecutorService());
            httpServer.start();
            LOGGER.info("HTTP 状态端点: http://0.0.0.0:{}/", port);
        } catch (Exception e) {
            LOGGER.warn("HTTP 状态服务启动失败: {} (端口={})", e.getMessage(), port);
        }
    }

    /** JDK 21+ 启动虚拟线程，否则回退到普通线程 */
    private static Thread startVirtualOrPlatformThread(String name, Runnable task) {
        try {
            // Thread.ofVirtual() 是 JDK 21+ API，通过反射调用以兼容 JDK 17 编译
            var ofVirtual = Thread.class.getMethod("ofVirtual");
            Object builder = ofVirtual.invoke(null);
            var nameMethod = builder.getClass().getMethod("name", String.class);
            builder = nameMethod.invoke(builder, name);
            var startMethod = builder.getClass().getMethod("start", Runnable.class);
            return (Thread) startMethod.invoke(builder, task);
        } catch (Exception e) {
            LOGGER.debug("JDK < 21, fallback to platform thread");
            Thread t = new Thread(task, name);
            t.setDaemon(true);
            t.start();
            return t;
        }
    }

    // ===== getters =====

    public String getEngineId() { return engineId; }
    public WorkflowConfig getConfig() { return config; }
    public WorkflowDb getDb() { return db; }
    public void setStatusPort(int port) { this.statusPort = port; }
}
