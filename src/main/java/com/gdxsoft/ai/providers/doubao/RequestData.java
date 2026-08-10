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

	public RequestData() {
		super("doubao-seed-1-6-250615");
		this.providerType = ProviderType.DOUBAO;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		if (thinking) {
			parameters.put("thinking", new JSONObject("{\"type\":\"enabled\"}"));
		} else {
			parameters.put("thinking", new JSONObject("{\"type\":\"disabled\"}"));
		}
		return this;
	}

	/**
	 * 设置 Doubao 思考预算。
	 * <p>
	 * 火山方舟的 thinking 不支持数值化预算，仅支持 {@code thinking.level}（low / medium / high）。
	 * 本方法按预算大小启发式映射：
	 * <ul>
	 *   <li>0 &lt; budget &le; 4096 &rarr; {@code low}</li>
	 *   <li>4096 &lt; budget &le; 16384 &rarr; {@code medium}</li>
	 *   <li>budget &gt; 16384 &rarr; {@code high}</li>
	 * </ul>
	 * 仅当 model 名匹配 {@link #THINKING_MODEL_KEYWORDS} 时写入
	 * {@code {"thinking": {"type": "enabled", "level": "low|medium|high"}}}；
	 * 其它模型忽略。
	 * 传入 &lt;= 0 视为清除（删除 thinking.level 字段，保留已设置的 type）。
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
			return this;
		}
		super.thinkingBudget(budgetToken);
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
}
