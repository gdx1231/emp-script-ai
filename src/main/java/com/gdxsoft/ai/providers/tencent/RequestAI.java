package com.gdxsoft.ai.providers.tencent;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

public class RequestAI extends RequestAIBase {
	// OpenAI的流式API网址
	public static final String DEFAULT_URL = "https://api.hunyuan.cloud.tencent.com/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.TENCENT;
	}

	 
}
