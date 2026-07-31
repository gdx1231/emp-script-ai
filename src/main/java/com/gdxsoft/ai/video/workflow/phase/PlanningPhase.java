package com.gdxsoft.ai.video.workflow.phase;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.video.workflow.config.PhaseDef;
import com.gdxsoft.ai.video.workflow.db.WorkflowDb;
import com.gdxsoft.ai.video.workflow.model.Storyboard;
import com.gdxsoft.ai.video.workflow.model.StoryboardParser;

/**
 * Phase1：分镜拆解 — 调用 LLM 将用户故事文本拆分为结构化分镜脚本。
 *
 * <p>流程：
 * <ol>
 *   <li>读取 {@code PhaseDef.promptTemplate} 并替换 @{input} 占位符</li>
 *   <li>创建 {@link LLmClient} 调用 LLM（非流式，responseFormat=json_object）</li>
 *   <li>{@link StoryboardParser} 解析 LLM 输出为 {@link Storyboard}</li>
 *   <li>保存 storyboard 到 {@code AI_WF_INSTANCE.WFI_STORYBOARD}</li>
 *   <li>更新实例状态和进度</li>
 * </ol>
 *
 * @since 1.4.0
 */
public class PlanningPhase implements IPhaseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningPhase.class);

    @Override
    public void execute(WorkflowContext ctx) throws Exception {
        WorkflowDb db = ctx.getDb();
        long instanceId = ctx.getInstanceId();
        PhaseDef phaseDef = ctx.getConfig().getPhase("planning");

        if (phaseDef == null) {
            throw new IllegalStateException("工作流配置中缺少 'planning' 阶段定义");
        }

        LOGGER.info("[PlanningPhase] 开始分镜拆解, WFI_ID={}", instanceId);

        // 1. 创建 planning task
        long taskId = db.createTask(instanceId, "planning", "分镜拆解", 0, "planning",
                new JSONObject().put("provider", phaseDef.getProvider())
                        .put("model", phaseDef.getModel()).toString());

        db.markTaskRunning(taskId);
        db.updateInstanceProgress(instanceId, "planning", 5);

        try {
            // 2. 获取 API 凭证
            WorkflowContext.ApiCredential cred = ctx.getApiCredential(phaseDef.getProvider());
            if (cred == null || cred.getKey() == null || cred.getKey().isEmpty()) {
                throw new IllegalStateException("LLM 供应商 " + phaseDef.getProvider() + " 的 API Key 未配置");
            }

            // 3. 构建 prompt
            String prompt = buildPrompt(phaseDef.getPromptTemplate(), ctx.getUserInput());
            LOGGER.info("[PlanningPhase] prompt 长度: {} chars", prompt.length());

            // 4. 调用 LLM
            LLmClient llm = new LLmClient(phaseDef.getProvider(), cred.getUrl(), cred.getKey(),
                    phaseDef.getModel());
            String responseFormat = phaseDef.getResponseFormat() != null
                    ? phaseDef.getResponseFormat() : "json_object";
            String llmOutput = llm.chat(null, prompt, responseFormat);

            LOGGER.info("[PlanningPhase] LLM 响应长度: {} chars", llmOutput.length());
            db.updateInstanceProgress(instanceId, "planning", 10);

            // 5. 解析 LLM 输出
            StoryboardParser parser = new StoryboardParser();
            Storyboard storyboard;
            try {
                storyboard = parser.parse(llmOutput);
            } catch (StoryboardParser.StoryboardParseException e) {
                LOGGER.warn("[PlanningPhase] 分镜解析失败，尝试 retry: {}", e.getMessage());
                // 重试一次，加一个强调 JSON 格式的 follow-up prompt
                String retryPrompt = prompt + "\n\n注意：请严格按照 JSON 格式输出，确保所有字段完整。上次解析错误：" + e.getMessage();
                llmOutput = llm.chat(null, retryPrompt, responseFormat);
                try {
                    storyboard = parser.parse(llmOutput);
                } catch (StoryboardParser.StoryboardParseException e2) {
                    db.updateTaskError(taskId, "分镜解析失败(重试后): " + e2.getMessage());
                    throw e2;
                }
            }

            // 6. 校验
            parser.validate(storyboard);

            // 7. 设置上下文
            ctx.setStoryboard(storyboard);

            // 8. 持久化
            String storyboardJson = storyboard.toJson().toString();
            db.saveStoryboard(instanceId, storyboardJson);

            // 9. 更新任务和实例
            JSONObject taskOutput = new JSONObject();
            taskOutput.put("storyboard", storyboard.toJson());
            taskOutput.put("characterCount", storyboard.getCharacterCount());
            taskOutput.put("environmentCount", storyboard.getEnvironmentCount());
            taskOutput.put("shotCount", storyboard.getShotCount());
            taskOutput.put("totalMaterialCount", storyboard.getTotalMaterialCount());
            db.updateTaskStatus(taskId, WorkflowDb.TASK_SUCCEEDED, taskOutput.toString());

            db.updateInstanceProgress(instanceId, "planning", 20);
            db.updateInstanceStatus(instanceId, WorkflowDb.STATUS_MATERIALS);

            LOGGER.info("[PlanningPhase] 分镜拆解完成: {} characters, {} environments, {} shots",
                    storyboard.getCharacterCount(), storyboard.getEnvironmentCount(),
                    storyboard.getShotCount());

        } catch (Exception e) {
            LOGGER.error("[PlanningPhase] 失败: {}", e.getMessage(), e);
            db.updateTaskError(taskId, e.getMessage());
            throw e;
        }
    }

    /**
     * 替换 prompt 模板中的占位符。
     * <p>支持：{@code @{input}} → 用户输入文本
     */
    String buildPrompt(String template, String userInput) {
        if (template == null || template.isEmpty()) {
            return userInput;
        }
        return template.replace("@{input}", userInput);
    }
}
