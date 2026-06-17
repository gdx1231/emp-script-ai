package com.gdxsoft.ai.providers.openrouter;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * OpenRouter 请求体。
 * reasoning 参数格式不同于标准 OpenAI。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("openai/gpt-4o");
		this.providerType = ProviderType.OPENROUTER;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		if (thinking) {
			JSONObject reasoningObj = new JSONObject();
			reasoningObj.put("enabled", true);
			parameters.put("reasoning", reasoningObj);
		} else {
			parameters.remove("reasoning");
		}
		return this;
	}
}
