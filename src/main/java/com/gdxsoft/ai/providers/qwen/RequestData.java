package com.gdxsoft.ai.providers.qwen;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * 通义千问（Qwen）请求体。
 * Qwen OpenAI 兼容协议使用 enable_thinking 控制思考开关，thinking_budget 控制预算。
 */
public class RequestData extends OpenAiRequestData {

	/**
	 * Qwen 支持思考预算的模型名关键字（忽略大小写）。
	 * <ul>
	 *   <li>qwen3 系列</li>
	 *   <li>qwq-*（QwQ 推理）</li>
	 *   <li>qvq-*（QVQ 视觉推理）</li>
	 * </ul>
	 * 其它 qwen-plus / qwen-turbo / qwen-max 等非思考模型忽略 thinkingBudget。
	 */
	private static final String[] THINKING_MODEL_KEYWORDS = {
			"qwq", "qvq", "qwen3"
	};

	public RequestData() {
		super("qwen-plus");
		this.providerType = ProviderType.QWEN;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		parameters.put("enable_thinking", thinking);
		return this;
	}

	/**
	 * 开启/关闭联网搜索（DashScope {@code enable_search}）。
	 * <p>
	 * 关闭时同时清除 {@code search_options}，避免残留的搜索策略在后续请求中生效。
	 */
	@Override
	public IRequestData enableSearch(boolean enable) {
		if (enable) {
			parameters.put("enable_search", true);
		} else {
			parameters.remove("enable_search");
			parameters.remove("search_options");
		}
		return this;
	}

	/**
	 * 设置联网搜索选项（DashScope {@code search_options}）。
	 * <p>
	 * 常用字段：
	 * <ul>
	 *   <li>{@code forced_search} — 模型判断无需搜索时仍强制联网</li>
	 *   <li>{@code search_strategy} — turbo / max / agent / agent_max</li>
	 *   <li>{@code enable_search_extension} — 垂域搜索</li>
	 * </ul>
	 * 仅在 {@code enable_search=true} 时由服务端生效。
	 *
	 * @param options 搜索选项；null 或空对象表示清除
	 */
	@Override
	public IRequestData searchOptions(JSONObject options) {
		if (options == null || options.isEmpty()) {
			parameters.remove("search_options");
		} else {
			// 复制一份，避免调用方后续修改影响请求体
			parameters.put("search_options", new JSONObject(options.toString()));
		}
		return this;
	}

	/**
	 * 是否强制联网搜索（{@code search_options.forced_search}）。
	 *
	 * @param forced true 强制搜索
	 */
	public IRequestData forcedSearch(boolean forced) {
		return putSearchOption("forced_search", forced);
	}

	/**
	 * 设置搜索量级策略（{@code search_options.search_strategy}）。
	 *
	 * @param strategy turbo（默认）/ max / agent / agent_max；空值表示清除
	 */
	public IRequestData searchStrategy(String strategy) {
		if (strategy == null || strategy.trim().isEmpty()) {
			return removeSearchOption("search_strategy");
		}
		return putSearchOption("search_strategy", strategy.trim());
	}

	/**
	 * 是否开启垂域搜索（{@code search_options.enable_search_extension}）。
	 *
	 * @param enable true 开启
	 */
	public IRequestData enableSearchExtension(boolean enable) {
		return putSearchOption("enable_search_extension", enable);
	}

	private IRequestData putSearchOption(String key, Object value) {
		JSONObject options = parameters.optJSONObject("search_options");
		if (options == null) {
			options = new JSONObject();
			parameters.put("search_options", options);
		}
		options.put(key, value);
		return this;
	}

	private IRequestData removeSearchOption(String key) {
		JSONObject options = parameters.optJSONObject("search_options");
		if (options != null) {
			options.remove(key);
			if (options.isEmpty()) {
				parameters.remove("search_options");
			}
		}
		return this;
	}

	/**
	 * 设置 Qwen 思考预算。
	 * <p>
	 * Qwen OpenAI 兼容接口的原生字段为顶层 {@code thinking_budget}。
	 * 当 {@code budgetToken > 0} 且 model 名匹配 {@link #THINKING_MODEL_KEYWORDS} 时，
	 * 写入顶层 {@code thinking_budget}。
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
		parameters.put("thinking_budget", budgetToken);
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
