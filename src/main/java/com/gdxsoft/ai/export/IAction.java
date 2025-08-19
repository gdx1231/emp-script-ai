package com.gdxsoft.ai.export;

import org.json.JSONObject;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 定义业务动作接口，例如根据 AI 输出执行业务逻辑，或生成 Prompt 内容。
 * <p>
 * Contract for business actions, such as acting on AI output or generating prompt content.
 */
public interface IAction {
	/**
	 * 执行动作：根据上下文与 AI 返回的全文结果进行业务处理。
	 * <p>
	 * Execute action: perform business logic based on context and AI full text output.
	 *
	 * @param rv       上下文参数 | request context
	 * @param fullText AI 返回的全文 | AI full text output
	 * @return 结果 JSON | result JSON
	 */
	JSONObject doAction(RequestValue rv, String fullText);

	/**
	 * 创建用于后续调用的提示内容（Prompt）。
	 * <p>
	 * Create a prompt text for downstream calls.
	 *
	 * @param rv            上下文参数 | request context
	 * @param dbConfigName  数据源配置名 | database config name
	 * @return 生成的提示文本 | generated prompt text
	 * @throws Exception 生成失败 | on generation errors
	 */
	String createPrompt(RequestValue rv, String dbConfigName) throws Exception;
}

