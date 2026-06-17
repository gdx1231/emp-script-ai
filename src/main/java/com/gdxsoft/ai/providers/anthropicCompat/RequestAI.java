package com.gdxsoft.ai.providers.anthropicCompat;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.AnthropicRequestAI;

/**
 * Anthropic 兼容模式请求实现。
 * 可指向任意 Anthropic 格式端点。
 */
public class RequestAI extends AnthropicRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.ANTHROPIC_COMPAT;
	}
}
