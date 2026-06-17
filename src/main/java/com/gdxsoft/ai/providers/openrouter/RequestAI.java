package com.gdxsoft.ai.providers.openrouter;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestAI;

/**
 * OpenRouter 请求实现 — OpenAI 兼容模式。
 */
public class RequestAI extends OpenAiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.OPENROUTER;
	}
}
