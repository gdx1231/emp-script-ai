package com.gdxsoft.ai.providers.doubao;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestAI;

/**
 * 豆包（Doubao/火山引擎）请求实现 — OpenAI 兼容模式。
 */
public class RequestAI extends OpenAiRequestAI {
	public RequestAI() {
		this.providerType = ProviderType.DOUBAO;
	}
}
