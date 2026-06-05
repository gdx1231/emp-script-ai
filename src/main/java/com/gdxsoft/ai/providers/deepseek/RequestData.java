package com.gdxsoft.ai.providers.deepseek;

import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 DeepSeek Chat Completions API 的请求体
 * DeepSeek 使用 OpenAI 兼容接口
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "deepseek-chat";

	public RequestData() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.DEEPSEEK;
	}

	/**
	 * DeepSeek 的 thinking 参数需要对象格式，非布尔值
	 * 启用: {"thinking": {"type": "enabled"}}
	 * 禁用: 不包含此字段
	 */
	@Override
	public IRequestData thinking(boolean thinking) {
		if (thinking) {
			JSONObject thinkingObj = new JSONObject();
			thinkingObj.put("type", "enabled");
			parameters.put("thinking", thinkingObj);
		} else {
			parameters.remove("thinking");
		}
		return this;
	}

	/**
	 * 构建 DeepSeek 请求体（OpenAI 兼容格式）
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		return requestData;
	}
}
