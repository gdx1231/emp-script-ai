package com.gdxsoft.ai.providers.grok;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

public class RequestAI extends RequestAIBase {
	// Grok的流式API网址（xAI API）
	public static final String DEFAULT_URL = "https://api.x.ai/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.GROK;
	}

	 
}
