package com.gdxsoft.ai.providers.deepseek;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * DeepSeek 请求体。
 * <p>
 * 思考模式（OpenAI 兼容格式）：
 * <ul>
 *   <li>开关：{@code {"thinking": {"type": "enabled|disabled"}}}，默认打开</li>
 *   <li>强度：{@code reasoning_effort}，取值 low / high / max（默认 high）</li>
 * </ul>
 */
public class RequestData extends OpenAiRequestData {

	/**
	 * DeepSeek 支持 {@code reasoning_effort} 思考强度的模型名关键字（忽略大小写）。
	 * <ul>
	 *   <li>deepseek-v4-pro / deepseek-v4-flash 等 v4 系列</li>
	 *   <li>deepseek-reasoner（思考专用模型）</li>
	 * </ul>
	 * 其它模型（deepseek-chat 等）忽略 thinkingBudget。
	 */
	private static final String[] REASONING_MODEL_KEYWORDS = {
			"deepseek-v4", "reasoner"
	};

	public RequestData() {
		super("deepseek-chat");
		this.providerType = ProviderType.DEEPSEEK;
	}

	/**
	 * 思考模式开关。DeepSeek 默认打开思考模式，因此关闭时必须显式下发
	 * {@code {"thinking": {"type": "disabled"}}}，同时清除 {@code reasoning_effort}。
	 */
	@Override
	public IRequestData thinking(boolean thinking) {
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", thinking ? "enabled" : "disabled");
		parameters.put("thinking", thinkingObj);
		if (!thinking) {
			parameters.remove("reasoning_effort");
		}
		return this;
	}

	/**
	 * 设置 DeepSeek 思考预算。
	 * <p>
	 * DeepSeek 不接受数值化预算参数，仅支持 {@code reasoning_effort} 档位。
	 * 本方法按预算大小启发式映射：
	 * <ul>
	 *   <li>0 &lt; budget &le; 4096 &rarr; {@code low}</li>
	 *   <li>4096 &lt; budget &le; 16384 &rarr; {@code high}</li>
	 *   <li>budget &gt; 16384 &rarr; {@code max}</li>
	 * </ul>
	 * 仅当 model 名匹配 {@link #REASONING_MODEL_KEYWORDS} 时写入
	 * {@code reasoning_effort} 并同时开启 {@code thinking.type=enabled}；其它模型忽略。
	 * 传入 &lt;= 0 视为清除（删除 reasoning_effort 字段，保留已设置的 thinking 开关）。
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
			// 模型不支持：缓存值保留但不写入请求体
			return this;
		}
		String effort;
		if (budgetToken <= 4096) {
			effort = "low";
		} else if (budgetToken <= 16384) {
			effort = "high";
		} else {
			effort = "max";
		}
		return this.reasoningEffort(effort);
	}

	/**
	 * 直接指定思考强度档位。
	 * <p>
	 * DeepSeek 实际生效档位只有 low / high / max，其它入参按官方映射表归一化：
	 * medium / high / xhigh &rarr; high。传入 null 或空视为清除该字段。
	 * 设置成功时同时把 {@code thinking.type} 置为 enabled。
	 *
	 * @param effort 强度档位（low / medium / high / xhigh / max，忽略大小写）
	 * @return this
	 */
	public IRequestData reasoningEffort(String effort) {
		if (effort == null || effort.trim().length() == 0) {
			parameters.remove("reasoning_effort");
			return this;
		}
		String level = normalizeEffort(effort.trim());
		parameters.put("reasoning_effort", level);
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", "enabled");
		parameters.put("thinking", thinkingObj);
		return this;
	}

	/**
	 * 按 DeepSeek 官方映射表把用户 effort 归一化为实际生效档位。
	 *
	 * @param effort 用户传入档位
	 * @return low / high / max
	 */
	public static String normalizeEffort(String effort) {
		String lower = effort.toLowerCase();
		switch (lower) {
		case "low":
			return "low";
		case "max":
			return "max";
		default:
			// medium / high / xhigh 以及未知值统一映射为 high（官方默认档位）
			return "high";
		}
	}

	/**
	 * 判断给定 model 名是否支持 DeepSeek 思考强度（关键词子串匹配，忽略大小写）。
	 *
	 * @param modelName 模型名（可为 null）
	 * @return 支持返回 true
	 */
	public static boolean isReasoningModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String kw : REASONING_MODEL_KEYWORDS) {
			if (lower.contains(kw)) {
				return true;
			}
		}
		return false;
	}
}
