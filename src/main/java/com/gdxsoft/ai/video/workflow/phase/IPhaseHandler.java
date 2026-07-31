package com.gdxsoft.ai.video.workflow.phase;

/**
 * 阶段处理器接口 — 每个 workflow Phase 对应一个实现。
 *
 * @since 1.4.0
 */
public interface IPhaseHandler {

    /**
     * 执行该阶段。
     *
     * @param ctx 工作流执行上下文（含 instanceId/config/db/已生成数据）
     * @throws Exception 阶段执行失败（引擎会记录错误并标记实例失败）
     */
    void execute(WorkflowContext ctx) throws Exception;

    /**
     * 阶段名称（与 workflow.json 中 phase.name 对应）。
     */
    default String getPhaseName() {
        return getClass().getSimpleName().replace("Phase", "").toLowerCase();
    }
}
