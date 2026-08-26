package com.gdxsoft.ai.platform;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.platform.adapters.*;
import com.gdxsoft.ai.request.ProviderType;

/**
 * AI 平台管理统一入口。
 * <p>
 * 提供跨 provider 的模型查询、使用量统计、费用查询等能力。
 * 模型信息完全从各 provider 的 API 动态获取。
 * 内部根据 ProviderType 路由到对应的 PlatformAdapter 实现。
 */
public class PlatformClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformClient.class);

    private static final Map<ProviderType, PlatformAdapter> ADAPTERS = new ConcurrentHashMap<>();

    static {
        registerAdapter(new OpenAiAdapter());
        registerAdapter(new QwenAdapter());
        registerAdapter(new DoubaoAdapter());
        registerAdapter(new AnthropicAdapter());
        registerAdapter(new GeminiAdapter());
        registerAdapter(new DeepSeekAdapter());
        registerAdapter(new GrokAdapter());
        registerAdapter(new OpenRouterAdapter());
        registerAdapter(new TencentAdapter());
        registerAdapter(new CompatAdapter(ProviderType.OPENAI_COMPAT));
        registerAdapter(new CompatAdapter(ProviderType.ANTHROPIC_COMPAT));
    }

    private static void registerAdapter(PlatformAdapter adapter) {
        ADAPTERS.put(adapter.getProviderType(), adapter);
    }

    /**
     * 获取指定 provider 的适配器
     */
    public static PlatformAdapter getAdapter(ProviderType provider) {
        return ADAPTERS.get(provider);
    }

    /**
     * 查询指定 provider 的可用模型列表（从 provider API 动态获取）
     */
    public List<ModelInfo> listModels(ProviderType provider, String apiUrl, String apiKey) throws Exception {
        if (provider == null) {
            throw new IllegalArgumentException("provider 不能为 null");
        }
        PlatformAdapter adapter = getAdapter(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的 provider: " + provider);
        }
        return adapter.listModels(apiUrl, apiKey);
    }

    /**
     * 查询所有已配置 provider 的模型汇总
     */
    public List<ModelInfo> listAllModels(List<ProviderConfig> configs) {
        List<ModelInfo> all = new ArrayList<>();
        for (ProviderConfig cfg : configs) {
            try {
                all.addAll(listModels(cfg.getProviderType(), cfg.getApiUrl(), cfg.getApiKey()));
            } catch (Exception e) {
                LOGGER.warn("查询 {} 模型列表失败: {}", cfg.getProviderType(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 查询指定 provider 的使用量
     */
    public UsageSummary getUsage(ProviderType provider, String apiUrl, String apiKey,
                                  LocalDate start, LocalDate end) throws Exception {
        PlatformAdapter adapter = getAdapter(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的 provider: " + provider);
        }
        return adapter.getUsage(apiUrl, apiKey, start, end);
    }

    /**
     * 查询指定 provider 的费用
     */
    public BillingSummary getBilling(ProviderType provider, String apiUrl, String apiKey,
                                      LocalDate start, LocalDate end) throws Exception {
        PlatformAdapter adapter = getAdapter(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的 provider: " + provider);
        }
        return adapter.getBilling(apiUrl, apiKey, start, end);
    }
}
