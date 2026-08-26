package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * OpenAI 适配器
 */
public class OpenAiAdapter extends OpenAiCompatibleAdapter {

    public OpenAiAdapter() {
        super(ProviderType.OPENAI);
    }
}
