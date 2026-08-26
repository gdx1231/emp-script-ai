package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * 兼容模式适配器，用于 openai_compat 和 anthropic_compat。
 * <p>
 * openai_compat 使用 OpenAI 格式的 models API；
 * anthropic_compat 使用 Anthropic 格式的 models API。
 */
public class CompatAdapter extends OpenAiCompatibleAdapter {

    public CompatAdapter(ProviderType providerType) {
        super(providerType);
    }
}
