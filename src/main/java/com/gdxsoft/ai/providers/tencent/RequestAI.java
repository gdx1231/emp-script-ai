package com.gdxsoft.ai.providers.tencent;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestAI;

/**
 * 腾讯混元请求实现 — OpenAI 兼容模式。
 */
public class RequestAI extends OpenAiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.TENCENT;
	}
}
