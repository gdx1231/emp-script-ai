package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * 豆包（火山方舟）适配器。
 * <p>
 * 火山方舟兼容 OpenAI 格式，models API 为 /v1/models。
 */
public class DoubaoAdapter extends OpenAiCompatibleAdapter {

    public DoubaoAdapter() {
        super(ProviderType.DOUBAO);
    }
}
