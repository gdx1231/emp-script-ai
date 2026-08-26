package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * 腾讯混元适配器
 */
public class TencentAdapter extends OpenAiCompatibleAdapter {

    public TencentAdapter() {
        super(ProviderType.TENCENT);
    }
}
