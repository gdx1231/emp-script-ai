package com.gdxsoft.ai.providers.anthropic;

import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 Anthropic Messages API 的请求体
 * Anthropic 使用自己的 messages 接口格式
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "claude-sonnet-4-20250514";

	public RequestData() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.ANTHROPIC;
	}

	/**
	 * Anthropic 不支持 system 消息放在 messages 数组中，而是单独的 system 字段
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
	 * Anthropic 不支持 thinking 参数（思考模式通过模型本身支持）
	 */
	@Override
	public IRequestData thinking(boolean thinking) {
		// Anthropic 不直接在顶层支持 thinking 参数，某些模型通过扩展支持
		// 这里保留但不抛出异常，由具体模型决定是否使用
		return this;
	}

	/**
	 * 构建 Anthropic Messages API 请求体
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
