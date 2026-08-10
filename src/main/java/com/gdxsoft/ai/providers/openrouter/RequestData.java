package com.gdxsoft.ai.providers.openrouter;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * OpenRouter 请求体。
 * reasoning 参数格式不同于标准 OpenAI。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("openai/gpt-4o");
		this.providerType = ProviderType.OPENROUTER;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		if (thinking) {
			JSONObject reasoningObj = new JSONObject();
			reasoningObj.put("enabled", true);
			parameters.put("reasoning", reasoningObj);
		} else {
			parameters.remove("reasoning");
		}
		return this;
	}

	/**
	 * 设置 OpenRouter 推理预算。
	 * <p>
	 * 写入 {@code {"reasoning": {"max_tokens": N}}}。OpenRouter 作为多 provider 透传层，
	 * 始终下发此字段（target provider 不支持时由 OpenRouter 后端忽略并降级处理）。
	 * 传入 &lt;= 0 视为清除预算（从 parameters 中删除 reasoning.max_tokens 字段，
	 * 但保留已设置的 enabled 标志）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			JSONObject reasoningObj = parameters.optJSONObject("reasoning");
			if (reasoningObj != null) {
				reasoningObj.remove("max_tokens");
				if (reasoningObj.length() == 0) {
					parameters.remove("reasoning");
				}
			}
			return this;
		}
		super.thinkingBudget(budgetToken);
		JSONObject reasoningObj = parameters.optJSONObject("reasoning");
		if (reasoningObj == null) {
			reasoningObj = new JSONObject();
		}
		reasoningObj.put("max_tokens", budgetToken);
		parameters.put("reasoning", reasoningObj);
		return this;
	}
}
