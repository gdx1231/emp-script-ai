package com.gdxsoft.ai.providers.anthropicCompat;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.AnthropicRequestData;

/**
 * Anthropic 兼容模式请求体。
 */
public class RequestData extends AnthropicRequestData {
	public RequestData() {
		super("");
		this.providerType = ProviderType.ANTHROPIC_COMPAT;
	}
}
