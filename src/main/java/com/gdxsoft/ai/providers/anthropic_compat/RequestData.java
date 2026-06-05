package com.gdxsoft.ai.providers.anthropic_compat;

import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 通用的 Anthropic 兼容模式请求体
 * 可指向任意 Anthropic 格式端点（通过 initUrlAndKey 自定义 URL）
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "";

	public RequestData() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.ANTHROPIC_COMPAT;
	}

	/**
	 * Anthropic 格式：system 消息使用独立字段
	 */
	@Override
	public IRequestData systemMessage(String content) {
		parameters.put("system", content);
		return this;
	}

	/**
	 * 设置最大 tokens（Anthropic 必须指定此参数）
	 */
	@Override
	public IRequestData maxTokens(int maxTokens) {
		parameters.put("max_tokens", maxTokens);
		return this;
	}

	/**
	 * Anthropic 不支持 thinking 参数
	 */
	@Override
	public IRequestData thinking(boolean thinking) {
		return this;
	}

	/**
	 * 构建 Anthropic 格式请求体
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);

		// Anthropic 必须指定 max_tokens
		if (!requestData.has("max_tokens")) {
			requestData.put("max_tokens", 4096);
		}

		return requestData;
	}
}
