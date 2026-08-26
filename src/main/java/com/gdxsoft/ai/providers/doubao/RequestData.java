package com.gdxsoft.ai.providers.doubao;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * 豆包（Doubao/火山引擎）请求体。
 * thinking 参数需要显式 disable。
 */
public class RequestData extends OpenAiRequestData {

	/**
	 * Doubao 支持 thinking.level 档位的模型名关键字（忽略大小写）。
	 * <ul>
	 *   <li>doubao-seed-1-6-thinking-*</li>
	 *   <li>doubao-1-5-thinking-*</li>
	 *   <li>deepseek-v3-1 / deepseek-r1（火山方舟代理的推理模型）</li>
	 * </ul>
	 * 其它 doubao-pro / doubao-lite 等非思考模型忽略 thinkingBudget。
	 */
	private static final String[] THINKING_MODEL_KEYWORDS = {
			"thinking", "deepseek-v3-1", "deepseek-r1", "r1-"
	};

	/**
	 * DeepSeek v4 系列（火山方舟代理，如 deepseek-v4-pro / deepseek-v4-flash）：
	 * 方舟对该类模型**不支持** {@code thinking.level}（加上会挂起），须用 DeepSeek 原生
	 * {@code reasoning_effort}（low / high / max）控制思考强度。
	 */
	private static final String[] DEEPSEEK_V4_KEYWORDS = { "deepseek-v4" };

	public RequestData() {
		super("doubao-seed-1-6-250615");
		this.providerType = ProviderType.DOUBAO;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", thinking ? "enabled" : "disabled");
		parameters.put("thinking", thinkingObj);
		if (!thinking) {
			// 关闭思考时同时清除 reasoning_effort，避免残留字段导致模型行为异常
			parameters.remove("reasoning_effort");
		}
		return this;
	}

	/**
	 * 设置 Doubao 思考预算。
	 * <ul>
	 *   <li><b>DeepSeek v4</b>：方舟不支持 thinking.level，映射为 {@code reasoning_effort}
	 *       （low / high / max，见 {@link #reasoningEffort}）；</li>
	 *   <li><b>其它思考模型</b>：映射为 {@code thinking.level}（low / medium / high）。</li>
	 * </ul>
	 * 仅当 model 名匹配对应关键词时写入；其它模型忽略。
	 * 传入 &lt;= 0 视为清除（删除 level / reasoning_effort 字段，保留已设置的 type）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			JSONObject thinkingObj = parameters.optJSONObject("thinking");
			if (thinkingObj != null) {
				thinkingObj.remove("level");
				if (thinkingObj.length() == 0) {
					parameters.remove("thinking");
				}
			}
			parameters.remove("reasoning_effort");
			return this;
		}
		super.thinkingBudget(budgetToken);
		if (isDeepSeekV4Model(this.model)) {
			// DeepSeek v4（Ark 代理）：用 reasoning_effort，不用 thinking.level
			return this.reasoningEffort(effortOf(budgetToken));
		}
		if (!isThinkingModel(this.model)) {
			// 模型不支持：缓存值保留但不写入请求体
			return this;
		}
		String level;
		if (budgetToken <= 4096) {
			level = "low";
		} else if (budgetToken <= 16384) {
			level = "medium";
		} else {
			level = "high";
		}
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", "enabled");
		thinkingObj.put("level", level);
		parameters.put("thinking", thinkingObj);
		return this;
	}

	/** DeepSeek v4 思考强度档位映射（对齐官方 low / high / max） */
	private static String effortOf(int budgetToken) {
		if (budgetToken <= 4096) {
			return "low";
		} else if (budgetToken <= 16384) {
			return "high";
		} else {
			return "max";
		}
	}

	/**
	 * 直接指定 DeepSeek v4 思考强度档位。
	 * 实际生效档位只有 low / high / max（medium / xhigh 归一为 high）。设置成功时
	 * 同时把 {@code thinking.type} 置为 enabled。
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

	/** 按 DeepSeek 官方映射表把 effort 归一化为实际生效档位（low / high / max） */
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
	 * 判断给定 model 名是否支持 Doubao 思考档位（关键词子串匹配，忽略大小写）。
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

	/** 判断给定 model 名是否为 DeepSeek v4 系列（忽略大小写）。 */
	public static boolean isDeepSeekV4Model(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String kw : DEEPSEEK_V4_KEYWORDS) {
			if (lower.contains(kw)) {
				return true;
			}
		}
		return false;
	}
}
