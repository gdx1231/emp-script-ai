package com.gdxsoft.ai.voicedesign;

import java.io.IOException;

/**
 * 声音设计高层入口。
 * <p>
 * 典型用法：
 * <pre>{@code
 * VoiceDesignResponse r = VoiceDesignClient.of("qwen_voice_design")
 *     .setApiKey("your-api-key")
 *     .create("沉稳的中年男性播音员，音色低沉浑厚，富有磁性，语速平稳，吐字清晰，适合用于新闻播报。");
 * System.out.println("Voice ID: " + r.getVoiceId());
 * r.savePreview(Path.of("preview.wav"));
 * }</pre>
 *
 * @since 1.1.0
 */
public final class VoiceDesignClient {
    private final IVoiceDesignProvider provider;

    public VoiceDesignClient(IVoiceDesignProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider is null");
        this.provider = provider;
    }

    /** 按 provider 名称创建。 */
    public static VoiceDesignClient of(String providerName) {
        return new VoiceDesignClient(VoiceDesignProviderFactory.create(providerName));
    }

    /** 使用已配置的 provider 创建。 */
    public static VoiceDesignClient of(IVoiceDesignProvider provider) {
        return new VoiceDesignClient(provider);
    }

    /** 设置 API Key（链式调用）。 */
    public VoiceDesignClient setApiKey(String key) {
        provider.setApiKey(key);
        return this;
    }

    /** 设置 API URL（链式调用）。 */
    public VoiceDesignClient setApiUrl(String url) {
        provider.setApiUrl(url);
        return this;
    }

    /** 设置 provider 特定配置（链式调用）。 */
    public VoiceDesignClient setConfig(String key, String value) {
        provider.setConfig(key, value);
        return this;
    }

    /**
     * 通过声音描述创建音色。
     */
    public VoiceDesignResponse create(String voicePrompt) throws IOException, InterruptedException {
        return create(new VoiceDesignRequest(voicePrompt));
    }

    /**
     * 通过声音描述创建音色（带可选参数）。
     */
    public VoiceDesignResponse create(String voicePrompt, VoiceDesignOptions options)
            throws IOException, InterruptedException {
        return create(new VoiceDesignRequest(voicePrompt, options));
    }

    /**
     * 通过声音描述创建音色（完整请求）。
     */
    public VoiceDesignResponse create(VoiceDesignRequest request) throws IOException, InterruptedException {
        return provider.create(request);
    }

    /**
     * 查询音色状态/详情。
     */
    public VoiceDesignResponse query(String voiceId) throws IOException, InterruptedException {
        return provider.query(voiceId);
    }

    /** 获取底层 provider（用于高级配置，如 list/delete）。 */
    public IVoiceDesignProvider getProvider() { return provider; }
}
