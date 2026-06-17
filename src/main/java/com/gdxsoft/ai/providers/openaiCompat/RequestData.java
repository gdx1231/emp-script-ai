package com.gdxsoft.ai.providers.openaiCompat;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * OpenAI 兼容模式请求体。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("");
		this.providerType = ProviderType.OPENAI_COMPAT;
	}
}
