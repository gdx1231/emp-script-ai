package com.gdxsoft.ai.providers.gemini;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.GeminiRequestAI;

/**
 * Google Gemini GenerateContent API 请求实现。
 */
public class RequestAI extends GeminiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.GEMINI;
	}
}
