package com.gdxsoft.ai.providers.openai;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestAI;

/**
 * OpenAI Chat Completions API 请求实现。
 */
public class RequestAI extends OpenAiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.OPENAI;
	}
}
