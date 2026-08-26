package com.gdxsoft.ai.platform;

import com.gdxsoft.ai.request.ProviderType;

/**
 * Provider 连接配置
 */
public class ProviderConfig {
    private ProviderType providerType;
    private String apiUrl;
    private String apiKey;
    private String name;

    public ProviderConfig() {}

    public ProviderConfig(ProviderType providerType, String apiUrl, String apiKey) {
        this.providerType = providerType;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    public ProviderConfig(ProviderType providerType, String apiUrl, String apiKey, String name) {
        this(providerType, apiUrl, apiKey);
        this.name = name;
    }

    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) { this.providerType = providerType; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
