package com.gdxsoft.ai.providers.gemini;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.GeminiRequestData;

/**
 * Google Gemini GenerateContent API 请求体。
 */
public class RequestData extends GeminiRequestData {
	public RequestData() {
		super("gemini-2.5-flash");
		this.providerType = ProviderType.GEMINI;
	}
}
