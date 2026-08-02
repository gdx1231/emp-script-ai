package com.gdxsoft.ai.video.workflow.engine;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.config.WorkflowConfig;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.phase.CompositingPhase;
import com.gdxsoft.ai.video.workflow.phase.GeneratingPhase;
import com.gdxsoft.ai.video.workflow.phase.MaterialsPhase;
import com.gdxsoft.ai.video.workflow.phase.PlanningPhase;
import com.gdxsoft.ai.video.workflow.phase.TtsPhase;
import com.gdxsoft.ai.video.workflow.phase.WorkflowContext;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 工作流引擎 CLI 入口。
 *
 * <p>start 命令工作原理：
 * <ul>
 *   <li><b>轮询</b>：每隔 10s（可配）从 DB 获取一条 pending 实例并占位</li>
 *   <li><b>执行</b>：占位成功后在虚拟线程中执行 planning → materials → tts(可选) → generating → compositing</li>
 *   <li><b>更新</b>：每个 Phase 完成后更新 AI_WF_INSTANCE 状态/进度，task 完成后更新 AI_WF_TASK</li>
 *   <li><b>并发</b>：通过信号量控制同时执行的工作流数量</li>
 * </ul>
 *
 * @since 1.4.0
 */
public class WorkflowCli {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowCli.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { printUsage(); return; }
        switch (args[0]) {
            case "start"  -> cmdStart(args);
            case "submit" -> cmdSubmit(args);
            case "status" -> cmdStatus(args);
            case "list"   -> cmdList(args);
            case "cancel" -> cmdCancel(args);
            default -> { System.err.println("未知命令: " + args[0]); printUsage(); }
        }
    }

    // ===== cmd: start — 主轮询循环 =====

    private static void cmdStart(String[] args) throws Exception {
        String configPath = getArg(args, "--config", "workflow.json");
        String dbConfig = getArg(args, "--db", "default");
        int intervalSec = Integer.parseInt(getArg(args, "--interval", "10"));
        int maxConcurrent = Integer.parseInt(getArg(args, "--concurrent", "3"));

        // 1. 加载配置 + 注册到 DB
        WorkflowConfig config = WorkflowConfig.load(Paths.get(configPath));
        WorkflowDb envDb = new WorkflowDb(new RequestValue(), dbConfig);
        long wfdId = envDb.registerWorkflowDef(config.getName(), config.getDescription(),
                config.getVersion(), config.getRawJson());

        String engineId = buildEngineId();
        ExecutorService executor = com.gdxsoft.ai.HttpUtils.createVirtualThreadExecutorService();
        Semaphore semaphore = new Semaphore(maxConcurrent > 0
                ? maxConcurrent : config.getLimits().getMaxConcurrentWorkflows());

        System.out.println("====================================");
        System.out.println("  视频创作工作流引擎");
        System.out.println("====================================");
        System.out.println("Engine:   " + engineId);
        System.out.println("Config:   " + configPath);
        System.out.println("DB:       " + dbConfig);
        System.out.println("Workflow: " + config.getName() + " (WFD_ID=" + wfdId + ")");
        System.out.println("Phases:   " + config.getPhaseCount());
        System.out.println("Interval: " + intervalSec + "s, MaxConcurrent: " + maxConcurrent);
        System.out.println("Ctrl+C to stop");
        System.out.println("====================================");

        // 2. 恢复中断的实例
        recover(dbConfig, engineId);

        // 3. 主循环：定期轮询
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalSec * 1000L);

                if (!semaphore.tryAcquire()) continue;

                WorkflowDb pollDb = new WorkflowDb(new RequestValue(), dbConfig);
                Long instanceId = pollDb.pollPendingInstance(engineId);

                if (instanceId == null) {
                    semaphore.release();
                    continue;
                }

                final long iid = instanceId;
                executor.submit(() -> {
                    try {
                        runWorkflow(iid, config, dbConfig, engineId);
                    } catch (Exception e) {
                        LOGGER.error("工作流 WFI_ID={} 执行异常: {}", iid, e.getMessage(), e);
                    } finally {
                        semaphore.release();
                    }
                });

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                LOGGER.warn("轮询异常: {}", e.getMessage());
            }
        }

        executor.shutdown();
        System.out.println("工作流引擎已停止");
    }

    /** 执行完整工作流：planning → materials → tts → generating → compositing */
    private static void runWorkflow(long instanceId, WorkflowConfig config,
                                     String dbConfig, String engineId) throws Exception {
        WorkflowDb db = new WorkflowDb(new RequestValue(), dbConfig);
        WorkflowContext ctx = new WorkflowContext(instanceId, config, db,
                loadUserInput(db, instanceId), engineId);
        ctx.setRv(new RequestValue());
        injectApiCredentials(ctx, config);

        LOGGER.info("执行工作流: WFI_ID={}", instanceId);
        long t0 = System.currentTimeMillis();

        try {
            runPhase(instanceId, db, ctx, "planning", new PlanningPhase(),
                    WorkflowDb.STATUS_PLANNING);
            runPhase(instanceId, db, ctx, "materials", new MaterialsPhase(),
                    WorkflowDb.STATUS_MATERIALS);
            runPhase(instanceId, db, ctx, "tts", new TtsPhase(),
                    WorkflowDb.STATUS_GENERATING); // TTS 后接 generating
            runPhase(instanceId, db, ctx, "generating", new GeneratingPhase(),
                    WorkflowDb.STATUS_GENERATING);
            runPhase(instanceId, db, ctx, "compositing", new CompositingPhase(),
                    WorkflowDb.STATUS_COMPOSITING);

            // 完成
            ctx.setFinalVideoUrl(ctx.getFinalVideoPath());
            db.updateInstanceResult(instanceId, ctx.getFinalVideoUrl(),
                    new org.json.JSONObject()
                            .put("finalVideoUrl", ctx.getFinalVideoUrl() != null
                                    ? ctx.getFinalVideoUrl() : "")
                            .put("finalVideoPath", ctx.getFinalVideoPath() != null
                                    ? ctx.getFinalVideoPath() : "")
                            .put("completedShots", ctx.getCompletedShotCount())
                            .toString());

            long elapsed = (System.currentTimeMillis() - t0) / 1000;
            LOGGER.info("工作流完成: WFI_ID={}, 耗时={}s, shots={}",
                    instanceId, elapsed, ctx.getCompletedShotCount());

        } catch (Exception e) {
            LOGGER.error("工作流失败: WFI_ID={}, error={}", instanceId, e.getMessage());
            db.updateInstanceError(instanceId, e.getMessage());
        }
    }

    /** 执行单个阶段 */
    private static void runPhase(long instanceId, WorkflowDb db, WorkflowContext ctx,
                                  String phaseName, Object handler, String nextStatus) throws Exception {
        PhaseDef phaseDef = ctx.getConfig().getPhase(phaseName);
        if (phaseDef == null) return;

        LOGGER.info("开始阶段: {} (WFI_ID={})", phaseName, instanceId);
        db.updateInstanceStatus(instanceId, nextStatus);

        if (handler instanceof com.gdxsoft.ai.video.workflow.phase.IPhaseHandler h) {
            h.execute(ctx);
        }
    }

    /** 加载用户输入 */
    private static String loadUserInput(WorkflowDb db, long instanceId) {
        try {
            var inst = db.queryInstanceById(instanceId);
            return inst != null ? inst.optString("WFI_INPUT", "") : "";
        } catch (Exception e) { return ""; }
    }

    /** 注入 API 凭证（从环境变量读取） */
    private static void injectApiCredentials(WorkflowContext ctx, WorkflowConfig config) {
        for (PhaseDef phase : config.getPhases()) {
            String provider = phase.getProvider();
            if (provider == null) continue;
            String key = System.getenv("WORKFLOW_API_" + provider.toUpperCase() + "_KEY");
            if (key != null && !key.isEmpty()) {
                String url = System.getenv("WORKFLOW_API_" + provider.toUpperCase() + "_URL");
                ctx.putApiCredential(provider, url, key);
            }
        }
    }

    /** 恢复中断实例 */
    private static void recover(String dbConfig, String engineId) {
        try {
            WorkflowDb db = new WorkflowDb(new RequestValue(), dbConfig);
            var instances = db.queryRecoverableInstances(engineId);
            for (var inst : instances) {
                long id = inst.optLong("WFI_ID");
                db.resetInstanceToPending(id);
                LOGGER.info("恢复实例 WFI_ID={}", id);
            }
        } catch (Exception e) {
            LOGGER.warn("恢复异常: {}", e.getMessage());
        }
    }

    private static String buildEngineId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            return host + "-" + pid;
        } catch (Exception e) {
            return "engine-" + System.currentTimeMillis();
        }
    }

    // ===== cmd: submit =====

    private static void cmdSubmit(String[] args) throws Exception {
        String configPath = getArg(args, "--config", "workflow.json");
        String input = getArg(args, "--input", null);
        String dbConfig = getArg(args, "--db", "default");

        if (input == null) {
            System.err.println("缺少 --input 参数");
            return;
        }

        WorkflowConfig config = WorkflowConfig.load(Paths.get(configPath));
        WorkflowDb db = new WorkflowDb(new RequestValue(), dbConfig);

        // 注册工作流定义
        long wfdId = db.registerWorkflowDef(config.getName(), config.getDescription(),
                config.getVersion(), config.getRawJson());

        // 提交实例
        String uid = UUID.randomUUID().toString();
        long instanceId = db.submitInstance(wfdId, uid, input, 5);

        System.out.println("工作流已提交:");
        System.out.println("  UID:  " + uid);
        System.out.println("  WFI_ID: " + instanceId);
        System.out.println("  workflow: " + config.getName());
        System.out.println("  status: pending");
        System.out.println("\n查询状态: java WorkflowCli status --db " + dbConfig + " --uid " + uid);
    }

    // ===== cmd: status =====

    private static void cmdStatus(String[] args) {
        String uid = getArg(args, "--uid", null);
        String dbConfig = getArg(args, "--db", "default");

        if (uid == null) {
            System.err.println("缺少 --uid 参数");
            return;
        }

        WorkflowDb db = new WorkflowDb(new RequestValue(), dbConfig);
        var inst = db.queryInstanceByUid(uid);

        if (inst == null) {
            System.out.println("未找到实例: uid=" + uid);
            return;
        }

        System.out.println("=== 工作流实例 ===");
        System.out.println("UID:       " + uid);
        System.out.println("Status:    " + inst.optString("WFI_STATUS"));
        System.out.println("Progress:  " + inst.optInt("WFI_PROGRESS") + "%");
        System.out.println("Phase:     " + inst.optString("WFI_CUR_PHASE"));
        System.out.println("Priority:  " + inst.optInt("WFI_PRIORITY"));
        System.out.println("Engine:    " + inst.optString("WFI_ENGINE_ID", "-"));
        System.out.println("Final URL: " + inst.optString("WFI_FINAL_URL", "-"));
        System.out.println("Error:     " + inst.optString("WFI_ERROR", "-"));
        System.out.println("Created:   " + inst.optString("WFI_CDATE"));
        System.out.println("Started:   " + inst.optString("WFI_SDATE", "-"));
        System.out.println("Done:      " + inst.optString("WFI_EDATE", "-"));

        // 显示任务列表
        long instanceId = inst.optLong("WFI_ID");
        var tasks = db.queryTasks(instanceId);
        if (!tasks.isEmpty()) {
            System.out.println("\n--- 任务列表 (" + tasks.size() + ") ---");
            for (var task : tasks) {
                System.out.printf("  [%s] %s %s (retry=%d)%n",
                        task.optString("WFT_STATUS"),
                        task.optString("WFT_TYPE"),
                        task.optString("WFT_NAME"),
                        task.optInt("WFT_RETRY_COUNT"));
            }
        }

        // 显示资产
        var assets = db.queryAssets(instanceId);
        if (!assets.isEmpty()) {
            System.out.println("\n--- 资产列表 (" + assets.size() + ") ---");
            for (var asset : assets) {
                System.out.printf("  [%s] %s: %s%n",
                        asset.optString("WFA_TYPE"),
                        asset.optString("WFA_NAME"),
                        asset.optString("WFA_URL", "-").substring(0,
                                Math.min(60, asset.optString("WFA_URL", "-").length())));
            }
        }
    }

    // ===== cmd: list =====

    private static void cmdList(String[] args) {
        String status = getArg(args, "--status", "pending");
        String dbConfig = getArg(args, "--db", "default");

        // 简单列表：直接查数据库
        System.out.println("=== 工作流实例 (" + status + ") ===");
        System.out.println("(请通过数据库直接查询: SELECT * FROM AI_WF_INSTANCE WHERE WFI_STATUS='"
                + status + "' ORDER BY WFI_PRIORITY, WFI_CDATE)");
    }

    // ===== cmd: cancel =====

    private static void cmdCancel(String[] args) {
        String uid = getArg(args, "--uid", null);
        String dbConfig = getArg(args, "--db", "default");

        if (uid == null) {
            System.err.println("缺少 --uid 参数");
            return;
        }

        WorkflowDb db = new WorkflowDb(new RequestValue(), dbConfig);
        var inst = db.queryInstanceByUid(uid);

        if (inst == null) {
            System.out.println("未找到实例: uid=" + uid);
            return;
        }

        db.cancelInstance(inst.optLong("WFI_ID"));
        System.out.println("已取消: uid=" + uid);
    }

    // ===== helpers =====

    private static String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static void printUsage() {
        System.out.println("""
                视频创作工作流引擎 CLI

                工作原理:
                  start 命令启动后，主线程每隔 --interval 秒轮询 DB，
                  取到 pending 实例后提交到虚拟线程执行，
                  每个阶段完成后更新 DB 状态。

                命令:
                  start   启动工作流引擎 (轮询 + 执行)
                  submit  提交工作流实例
                  status  查询实例状态
                  list    列出实例
                  cancel  取消实例

                用法:
                  java WorkflowCli start --config workflow.json [--db default] [--interval 10] [--concurrent 3]
                  java WorkflowCli submit --config workflow.json --input "故事" [--db default]
                  java WorkflowCli status --uid <uuid> [--db default]
                  java WorkflowCli cancel --uid <uuid> [--db default]

                环境变量:
                  WORKFLOW_API_{PROVIDER}_KEY=...  (如 WORKFLOW_API_QWEN_KEY)
                  WORKFLOW_API_{PROVIDER}_URL=...  (可选)
                """);
    }
}
