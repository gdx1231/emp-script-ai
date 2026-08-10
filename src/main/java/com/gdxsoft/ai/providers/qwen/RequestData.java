package com.gdxsoft.ai.providers.qwen;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * 通义千问（Qwen）请求体。
 * thinking 参数需要 object 格式。
 */
public class RequestData extends OpenAiRequestData {

	/**
	 * Qwen 支持数值化思考预算的模型名关键字（忽略大小写）。
	 * <ul>
	 *   <li>qwen3-* 含 {@code -thinking} 后缀</li>
	 *   <li>qwq-*（QwQ 推理）</li>
	 *   <li>qvq-*（QVQ 视觉推理）</li>
	 * </ul>
	 * 其它 qwen-plus / qwen-turbo / qwen-max 等非思考模型忽略 thinkingBudget。
	 */
	private static final String[] THINKING_MODEL_KEYWORDS = {
			"qwq", "qvq", "-thinking"
	};

	public RequestData() {
		super("qwen-plus");
		this.providerType = ProviderType.QWEN;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		JSONObject thinkingObj = new JSONObject();
		thinkingObj.put("type", thinking ? "enabled" : "disabled");
		parameters.put("thinking", thinkingObj);
		return this;
	}

	/**
	 * 设置 Qwen 思考预算。
	 * <p>
	 * DashScope OpenAI 兼容接口原生字段为 {@code thinking.max_thinking_tokens}；
	 * 部分网关会封装为 {@code thinking.budget}，本实现按直连 DashScope 走原生字段名。
	 * 当 {@code budgetToken > 0} 且 model 名匹配 {@link #THINKING_MODEL_KEYWORDS} 时，
	 * 写入 {@code {"thinking": {"type": "enabled", "max_thinking_tokens": N}}}。
	 * model 不支持时仅记录缓存值、不写入请求体（避免 400 错误）。
	 * 传入 &lt;= 0 视为清除预算（不主动发送 disabled，保持现有 thinking 字段状态）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			return this;
		}
		super.thinkingBudget(budgetToken);
		if (!isThinkingModel(this.model)) {
			// 模型不支持：缓存值保留但不写入请求体
			return this;
		}
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", "enabled");
		thinkingObj.put("max_thinking_tokens", budgetToken);
		parameters.put("thinking", thinkingObj);
		return this;
	}

	/**
	 * 判断给定 model 名是否支持 Qwen 思考预算（关键词子串匹配，忽略大小写）。
	 *
	 * @param modelName 模型名（可为 null）
	 * @return 支持返回 true
	 */
	public static boolean isThinkingModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String kw : THINKING_MODEL_KEYWORDS) {
			if (lower.contains(kw)) {
				return true;
			}
		}
		return false;
	}
}
