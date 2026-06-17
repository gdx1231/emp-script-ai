package com.gdxsoft.ai.providers.qwen;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * 通义千问（Qwen）请求体。
 * thinking 参数需要 object 格式。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("qwen-plus");
		this.providerType = ProviderType.QWEN;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		JSONObject thinkingObj = new JSONObject();
		thinkingObj.put("type", thinking ? "enabled" : "disabled");
		parameters.put("thinking", thinkingObj);
		return this;
	}
}
