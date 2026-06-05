package com.gdxsoft.ai.providers.anthropic;

import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 Anthropic OpenAI 兼容模式 API 的请求体
 * Anthropic 提供 OpenAI 兼容接口，格式与 OpenAI 完全一致
 */
public class RequestDataOpenAICompat extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "claude-sonnet-4-20250514";

	public RequestDataOpenAICompat() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.ANTHROPIC;
	}

	/**
	 * OpenAI 兼容模式：system 消息作为普通消息加入 messages 数组
	 */
	@Override
	public IRequestData systemMessage(String content) {
		return this.addMessage(content, "system");
	}

	/**
	 * 构建 OpenAI 兼容格式请求体
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		return requestData;
	}
}
