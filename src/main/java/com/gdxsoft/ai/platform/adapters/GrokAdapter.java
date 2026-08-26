package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * Grok (xAI) 适配器
 */
public class GrokAdapter extends OpenAiCompatibleAdapter {

    public GrokAdapter() {
        super(ProviderType.GROK);
    }
}
