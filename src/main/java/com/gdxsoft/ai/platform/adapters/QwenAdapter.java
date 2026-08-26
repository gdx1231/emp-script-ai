package com.gdxsoft.ai.platform.adapters;

import com.gdxsoft.ai.request.ProviderType;

/**
 * 通义千问 (DashScope) 适配器。
 * <p>
 * DashScope 兼容 OpenAI 格式，models API 为 /compatible-mode/v1/models 或 /v1/models。
 */
public class QwenAdapter extends OpenAiCompatibleAdapter {

    public QwenAdapter() {
        super(ProviderType.QWEN, "/compatible-mode/v1/models");
    }

    @Override
    protected String buildModelsUrl(String apiUrl) {
        String base = apiUrl.replaceAll("/+$", "");
        // DashScope 的 apiUrl 通常是 https://dashscope.aliyuncs.com/compatible-mode/v1
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
            return base.replace("/v1", "/compatible-mode/v1") + "/models";
        }
        // 如果已包含 /compatible-mode/v1
        if (base.contains("/compatible-mode/v1")) {
            return base + "/models";
        }
        // 默认拼接到 compatible-mode
        if (!base.contains("/compatible-mode")) {
            base = base + "/compatible-mode/v1";
        }
        return base + "/models";
    }
}
