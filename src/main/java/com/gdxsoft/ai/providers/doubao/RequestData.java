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
}
