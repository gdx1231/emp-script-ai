package com.gdxsoft.ai.request;

/**
 * RequestData 工厂类
 * 根据提供商名称创建相应的 RequestData 实例
 */
public class RequestDataFactory {
    /**
     * 根据提供商名称创建RequestData实例
     * 
     * @param providerName 提供商名称 (gemini, grok, openai, qwen)
     * @return RequestData实例
     * @throws IllegalArgumentException 如果提供商名称不被支持
     */
    public static IRequestData createRequestData(String providerName) {
        ProviderType type = ProviderType.fromName(providerName);

        if (type == null) {
            throw new IllegalArgumentException("不支持的AI提供商: " + providerName +
                    ". 支持的提供商: gemini, grok, openai, qwen, doubao, tencent, deepseek, openrouter, anthropic, openai_compat, anthropic_compat");
        }

        return createRequestData(type);
    }

    /**
     * 根据提供商类型创建RequestData实例
     * 
     * @param type 提供商类型
     * @return RequestData实例
     * @throws IllegalArgumentException 如果提供商类型不被支持
     */
    public static IRequestData createRequestData(ProviderType type) {
        switch (type) {
            case GEMINI:
                return new com.gdxsoft.ai.providers.gemini.RequestData();
            case GROK:
                return new com.gdxsoft.ai.providers.grok.RequestData();
            case OPENAI:
                return new com.gdxsoft.ai.providers.openai.RequestData();
            case QWEN:
                return new com.gdxsoft.ai.providers.qwen.RequestData();
            case DOUBAO:
                return new com.gdxsoft.ai.providers.doubao.RequestData();
			case TENCENT:
				return new com.gdxsoft.ai.providers.tencent.RequestData();
			case DEEPSEEK:
				return new com.gdxsoft.ai.providers.deepseek.RequestData();
			case OPENROUTER:
				return new com.gdxsoft.ai.providers.openrouter.RequestData();
			case ANTHROPIC:
				return new com.gdxsoft.ai.providers.anthropic.RequestData();
			case OPENAI_COMPAT:
				return new com.gdxsoft.ai.providers.openaiCompat.RequestData();
			case ANTHROPIC_COMPAT:
				return new com.gdxsoft.ai.providers.anthropicCompat.RequestData();
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

    /**
     * 根据模型名称推断提供商类型
     * 基于常见的模型命名规律进行推断
     * 
     * @param modelName 模型名称
     * @return 推断的提供商类型，如果无法推断则返回null
     */
    public static ProviderType inferProviderFromModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return null;
        }

        String lowerModelName = modelName.toLowerCase();

        // Gemini 模型特征
        if (lowerModelName.contains("gemini")) {
            return ProviderType.GEMINI;
        }

        // OpenAI 模型特征
        if (lowerModelName.startsWith("gpt-") ||
                lowerModelName.contains("davinci") ||
                lowerModelName.contains("curie") ||
                lowerModelName.contains("babbage") ||
                lowerModelName.contains("ada")) {
            return ProviderType.OPENAI;
        }

        // Grok 模型特征
        if (lowerModelName.contains("grok")) {
            return ProviderType.GROK;
        }

        // Qwen 模型特征
        if (lowerModelName.contains("qwen") ||
                lowerModelName.contains("通义")) {
            return ProviderType.QWEN;
        }

        // Doubao（豆包）模型特征
        if (lowerModelName.contains("doubao") ||
                lowerModelName.contains("豆包") ||
                lowerModelName.contains("skylark") || // 部分早期命名
                lowerModelName.contains("bytegpt") ||
                lowerModelName.contains("larksuite")) {
            return ProviderType.DOUBAO;
        }

        // DeepSeek 模型特征
        if (lowerModelName.contains("deepseek")) {
            return ProviderType.DEEPSEEK;
        }

        // OpenRouter 模型特征（通常以提供商名/模型名格式出现）
        if (lowerModelName.contains("openrouter")) {
            return ProviderType.OPENROUTER;
        }

        // Anthropic 模型特征
        if (lowerModelName.contains("claude") || lowerModelName.contains("anthropic")) {
            return ProviderType.ANTHROPIC;
        }

        // 兼容模式前缀匹配（兜底）
        if (lowerModelName.startsWith("openai") || lowerModelName.startsWith("oaic")) {
            return ProviderType.OPENAI_COMPAT;
        }
        if (lowerModelName.startsWith("anthropic") || lowerModelName.startsWith("antc")) {
            return ProviderType.ANTHROPIC_COMPAT;
        }

        return null;
    }

    /**
     * 根据模型名称创建对应的RequestData实例
     * 
     * @param modelName 模型名称
     * @return RequestData实例
     * @throws IllegalArgumentException 如果无法从模型名称推断提供商
     */
    public static IRequestData createRequestDataByModel(String modelName) {
        ProviderType type = inferProviderFromModel(modelName);
        if (type == null) {
            throw new IllegalArgumentException("无法从模型名称推断AI提供商: " + modelName);
        }

        IRequestData requestData = createRequestData(type);
        requestData.model(modelName); // 设置指定的模型名称
        return requestData;
    }
}
