package com.gdxsoft.ai.providers.deepseek;

import org.json.JSONObject;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * DeepSeek 请求体。
 * thinking 参数需要 object 格式：{"type": "enabled"}。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("deepseek-chat");
		this.providerType = ProviderType.DEEPSEEK;
	}

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
}
