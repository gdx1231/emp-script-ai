package com.gdxsoft.ai.providers.doubao;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;

/**
 * 豆包（Doubao/火山引擎）OpenAI 兼容聊天补全流式接口 默认使用 ByteDance OpenAI-Compatible endpoint
 */
public class RequestAI extends RequestAIBase {
	// Doubao 的 OpenAI 兼容流式 API（火山引擎兼容模式）
	// 文档参考： https://www.volcengine.com/docs/82379/1298450 （若路径不同，可按实际调整）
	public static final String DEFAULT_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.DOUBAO;
	}

	 
}
