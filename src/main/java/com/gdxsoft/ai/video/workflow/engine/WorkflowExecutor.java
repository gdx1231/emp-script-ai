package com.gdxsoft.ai.video.workflow.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.config.WorkflowConfig;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.phase.CompositingPhase;
import com.gdxsoft.ai.video.workflow.phase.GeneratingPhase;
import com.gdxsoft.ai.video.workflow.phase.IPhaseHandler;
import com.gdxsoft.ai.video.workflow.phase.MaterialsPhase;
import com.gdxsoft.ai.video.workflow.phase.PlanningPhase;
import com.gdxsoft.ai.video.workflow.phase.TtsPhase;
import com.gdxsoft.ai.video.workflow.phase.WorkflowContext;
import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 工作流执行器 — 串行执行单个实例的四个阶段。
 *
 * <p>执行顺序：
 * <ol>
 *   <li>PlanningPhase    — LLM 分镜拆解</li>
 *   <li>MaterialsPhase   — 批量图片素材生成</li>
 *   <li>TtsPhase         — TTS 配音（可选）</li>
 *   <li>GeneratingPhase  — 分镜视频生成 + 尾帧续接</li>
 *   <li>CompositingPhase — ffmpeg 合成最终视频</li>
 * </ol>
 *
 * <p>每个阶段完成后更新 DB 状态和进度，失败时记录错误并标记实例 failed。
 *
 * @since 1.4.0
 */
public class WorkflowExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExecutor.class);

    private final WorkflowConfig config;
    private final WorkflowDb db;
    private final String engineId;

    public WorkflowExecutor(WorkflowConfig config, WorkflowDb db, String engineId) {
        this.config = config;
        this.db = db;
        this.engineId = engineId;
    }

    /**
     * 执行单个工作流实例。
     *
     * @param instanceId 实例 ID（已被调度器标记为 running）
     */
    public void run(long instanceId) {
        LOGGER.info("开始执行工作流: WFI_ID={}", instanceId);

        WorkflowContext ctx = null;
        try {
            // 加载实例数据
            String userInput = loadUserInput(instanceId);

            // 创建上下文
            ctx = new WorkflowContext(instanceId, config, db, userInput, engineId);
            ctx.setRv(new RequestValue());

            // 注入 API 凭证
            injectApiCredentials(ctx);

            // 按阶段顺序执行
            runPhase(instanceId, "planning", new PlanningPhase(), ctx);
            runPhase(instanceId, "materials", new MaterialsPhase(), ctx);
            runPhase(instanceId, "tts", new TtsPhase(), ctx);          // 可选
            runPhase(instanceId, "generating", new GeneratingPhase(), ctx);
            runPhase(instanceId, "compositing", new CompositingPhase(), ctx);

            // 完成
            ctx.setFinalVideoUrl(ctx.getFinalVideoPath()); // URL = local path for now
            db.updateInstanceResult(instanceId, ctx.getFinalVideoUrl(),
                    buildResultJson(ctx));
            LOGGER.info("工作流实例完成: WFI_ID={}, shots={}",
                    instanceId, ctx.getStoryboard() != null
                            ? ctx.getStoryboard().getShotCount() : 0);

        } catch (Exception e) {
            LOGGER.error("工作流实例失败: WFI_ID={}, error={}",
                    instanceId, e.getMessage(), e);
            db.updateInstanceError(instanceId, e.getMessage());
        }
    }

    /** 执行单个阶段 */
    private void runPhase(long instanceId, String phaseName,
                           IPhaseHandler handler, WorkflowContext ctx) throws Exception {
        PhaseDef phaseDef = config.getPhase(phaseName);
        if (phaseDef == null) {
            LOGGER.info("阶段 '{}' 未配置，跳过", phaseName);
            return;
        }

        LOGGER.info("执行阶段: {} (WFI_ID={})", phaseName, instanceId);
        try {
            db.updateInstanceStatus(instanceId, getStatusForPhase(phaseName));
            handler.execute(ctx);
        } catch (Exception e) {
            LOGGER.error("阶段 {} 失败: {}", phaseName, e.getMessage());
            throw e;
        }
    }

    /** 加载用户输入文本 */
    private String loadUserInput(long instanceId) {
        try {
            var inst = db.queryInstanceById(instanceId);
            if (inst != null) {
                return inst.optString("WFI_INPUT", "");
            }
        } catch (Exception e) {
            LOGGER.warn("加载实例输入失败: {}", e.getMessage());
        }
        return "";
    }

    /** 注入 API 凭证 */
    private void injectApiCredentials(WorkflowContext ctx) {
        for (PhaseDef phase : ctx.getConfig().getPhases()) {
            String provider = phase.getProvider();
            if (provider == null) continue;

            // 从环境变量读取凭证
            // 约定：WORKFLOW_API_{PROVIDER}_KEY / WORKFLOW_API_{PROVIDER}_URL
            String envKeyName = "WORKFLOW_API_" + provider.toUpperCase() + "_KEY";
            String envUrlName = "WORKFLOW_API_" + provider.toUpperCase() + "_URL";

            String key = System.getenv(envKeyName);
            String url = System.getenv(envUrlName);

            if (key != null && !key.isEmpty()) {
                ctx.putApiCredential(provider, url, key);
                LOGGER.info("注入 API 凭证: provider={}, from={}", provider, envKeyName);
            } else {
                LOGGER.warn("API 凭证未配置: provider={}, 请设置环境变量 {}", provider, envKeyName);
            }
        }
    }

    /** 构建结果 JSON */
    private String buildResultJson(WorkflowContext ctx) {
        var j = new org.json.JSONObject();
        j.put("finalVideoUrl", ctx.getFinalVideoUrl() != null ? ctx.getFinalVideoUrl() : "");
        j.put("finalVideoPath", ctx.getFinalVideoPath() != null ? ctx.getFinalVideoPath() : "");
        j.put("completedShots", ctx.getCompletedShotCount());
        return j.toString();
    }

    /** 阶段名 → DB 状态映射 */
    private String getStatusForPhase(String phaseName) {
        return switch (phaseName) {
            case "planning"    -> WorkflowDb.STATUS_PLANNING;
            case "materials"   -> WorkflowDb.STATUS_MATERIALS;
            case "generating"  -> WorkflowDb.STATUS_GENERATING;
            case "compositing" -> WorkflowDb.STATUS_COMPOSITING;
            default            -> phaseName;
        };
    }
}
