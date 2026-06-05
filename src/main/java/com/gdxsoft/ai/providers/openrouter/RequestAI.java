package com.gdxsoft.ai.providers.openrouter;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

/**
 * OpenRouter AI 请求实现
 * OpenRouter 使用 OpenAI 兼容接口，基础类已提供完整的 HTTP/流式处理能力
 */
public class RequestAI extends RequestAIBase {
	// OpenRouter 的流式 API 网址（OpenAI 兼容模式）
	public static final String DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.OPENROUTER;
	}
}
