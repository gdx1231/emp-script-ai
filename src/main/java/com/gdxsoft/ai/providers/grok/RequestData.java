package com.gdxsoft.ai.providers.grok;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * Grok（xAI）请求体 — OpenAI 兼容格式。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("grok-2");
		this.providerType = ProviderType.GROK;
	}
}
