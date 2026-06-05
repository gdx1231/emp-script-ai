package com.gdxsoft.ai.request;

/**
 * RequestData 工厂类 根据提供商名称创建相应的 RequestData 实例
 */
public class RequestAIFactory {
	/**
	 * 根据提供商名称创建RequestData实例
	 * 
	 * @param providerName 提供商名称 (gemini, grok, openai, qwen, doubao)
	 * @return RequestData实例
	 * @throws IllegalArgumentException 如果提供商名称不被支持
	 */
	public static IRequestAI createRequestAI(String providerName) {
		ProviderType type = ProviderType.fromName(providerName);

		if (type == null) {
			throw new IllegalArgumentException(
					"不支持的AI提供商: " + providerName + ". 支持的提供商: gemini, grok, openai, qwen, doubao, tencent, deepseek, openrouter, anthropic, openai_compat, anthropic_compat");
		}
		return createRequestAI(type);
	}

	/**
	 * 根据提供商类型创建RequestData实例
	 * 
	 * @param type 提供商类型
	 * @return RequestData实例
	 * @throws IllegalArgumentException 如果提供商类型不被支持
	 */
	public static IRequestAI createRequestAI(ProviderType type) {
		switch (type) {
		case GEMINI:
			return new com.gdxsoft.ai.providers.gemini.RequestAI();
		case GROK:
			return new com.gdxsoft.ai.providers.grok.RequestAI();
		case OPENAI:
			return new com.gdxsoft.ai.providers.openai.RequestAI();
		case QWEN:
			return new com.gdxsoft.ai.providers.qwen.RequestAI();
		case DOUBAO:
			return new com.gdxsoft.ai.providers.doubao.RequestAI();
		case TENCENT:
			return new com.gdxsoft.ai.providers.tencent.RequestAI();
		case DEEPSEEK:
			return new com.gdxsoft.ai.providers.deepseek.RequestAI();
		case OPENROUTER:
			return new com.gdxsoft.ai.providers.openrouter.RequestAI();
		case ANTHROPIC:
			return new com.gdxsoft.ai.providers.anthropic.RequestAI();
		case OPENAI_COMPAT:
			return new com.gdxsoft.ai.providers.openai_compat.RequestAI();
		case ANTHROPIC_COMPAT:
			return new com.gdxsoft.ai.providers.anthropic_compat.RequestAI();
		default:
			throw new IllegalArgumentException("不支持的AI提供商类型: " + type);
		}
	}

	/**
	 * 检查是否支持指定的提供商名称
	 * 
	 * @param providerName 提供商名称
	 * @return 是否支持
	 */
	public static boolean isSupported(String providerName) {
		return ProviderType.fromName(providerName) != null;
	}

	/**
	 * 获取所有支持的提供商名称
	 * 
	 * @return 支持的提供商名称数组
	 */
	public static String[] getSupportedProviders() {
		ProviderType[] types = ProviderType.values();
		String[] providers = new String[types.length];
		for (int i = 0; i < types.length; i++) {
			providers[i] = types[i].getName();
		}
		return providers;
	}

}
