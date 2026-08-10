package com.gdxsoft.ai.providers.openai;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * OpenAI Chat Completions 请求体。
 */
public class RequestData extends OpenAiRequestData {

	/**
	 * OpenAI 支持 {@code reasoning_effort} 的模型名前缀（忽略大小写）。
	 * o1 / o3 / o4 / gpt-5 系列。普通 gpt-4o / gpt-4 / gpt-3.5 不支持，忽略 thinkingBudget。
	 */
	private static final String[] REASONING_MODEL_PREFIXES = {
			"o1", "o3", "o4", "gpt-5"
	};

	public RequestData() {
		super("gpt-4o");
		this.providerType = ProviderType.OPENAI;
	}

	/**
	 * 设置 OpenAI 思考预算。
	 * <p>
	 * OpenAI 的 o-series / gpt-5 不接受数值化预算参数，仅支持 {@code reasoning_effort}
	 * （low / medium / high）。本方法按预算大小启发式映射：
	 * <ul>
	 *   <li>0 &lt; budget &le; 4096 &rarr; {@code low}</li>
	 *   <li>4096 &lt; budget &le; 16384 &rarr; {@code medium}</li>
	 *   <li>budget &gt; 16384 &rarr; {@code high}</li>
	 * </ul>
	 * 仅当 model 名以 {@link #REASONING_MODEL_PREFIXES} 之一开头时写入
	 * {@code reasoning_effort} 字段；其它模型忽略。
	 * 传入 &lt;= 0 视为清除（删除 reasoning_effort 字段）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			parameters.remove("reasoning_effort");
			return this;
		}
		super.thinkingBudget(budgetToken);
		if (!isReasoningModel(this.model)) {
			// 模型不支持 reasoning_effort：缓存值保留但不写入请求体
			return this;
		}
		String effort;
		if (budgetToken <= 4096) {
			effort = "low";
		} else if (budgetToken <= 16384) {
			effort = "medium";
		} else {
			effort = "high";
		}
		parameters.put("reasoning_effort", effort);
		return this;
	}

	/**
	 * 判断给定 model 名是否支持 OpenAI {@code reasoning_effort}（前缀匹配，忽略大小写）。
	 *
	 * @param modelName 模型名（可为 null）
	 * @return 支持返回 true
	 */
	public static boolean isReasoningModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String prefix : REASONING_MODEL_PREFIXES) {
			if (lower.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
}
