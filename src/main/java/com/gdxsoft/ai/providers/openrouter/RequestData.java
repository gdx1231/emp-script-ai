package com.gdxsoft.ai.providers.openrouter;

import org.json.JSONObject;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 OpenRouter Chat Completions API 的请求体
 * OpenRouter 使用 OpenAI 兼容接口
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "openai/gpt-4o";

	public RequestData() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.OPENROUTER;
	}

	/**
	 * 构建 OpenRouter 请求体（OpenAI 兼容格式）
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		return requestData;
	}
}
