package com.gdxsoft.ai.providers.qwen;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

public class RequestAI extends RequestAIBase {
	// 通义千问的流式API 网址，openai兼容模式
	public static final String DEFAULT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.QWEN;
	}

	 
}
