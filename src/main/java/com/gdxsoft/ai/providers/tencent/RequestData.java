package com.gdxsoft.ai.providers.tencent;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.style.OpenAiRequestData;

/**
 * 腾讯混元请求体 — OpenAI 兼容格式。
 */
public class RequestData extends OpenAiRequestData {
	public RequestData() {
		super("hunyuan-turbos-latest");
		this.providerType = ProviderType.TENCENT;
	}
}
