package com.gdxsoft.ai.providers.openaiCompat;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestAI;

/**
 * OpenAI 兼容模式请求实现。
 * 可指向任意 OpenAI 兼容端点。
 */
public class RequestAI extends OpenAiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.OPENAI_COMPAT;
	}
}
