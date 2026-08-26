package com.gdxsoft.ai.platform.adapters;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.platform.*;
import com.gdxsoft.ai.request.ProviderType;

/**
 * OpenAI 兼容格式 API 的通用适配器基类。
 * <p>
 * 适用于所有实现了 OpenAI /v1/models 接口的 provider（OpenAI、DeepSeek、Grok、豆包、OpenRouter、腾讯、openai_compat 等）。
 * 子类只需指定 providerType 和可选的自定义 URL 路径。
 */
public class OpenAiCompatibleAdapter implements PlatformAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);

    private final ProviderType providerType;
    private final String modelsPath;

    public OpenAiCompatibleAdapter(ProviderType providerType) {
        this(providerType, "/v1/models");
    }

    public OpenAiCompatibleAdapter(ProviderType providerType, String modelsPath) {
        this.providerType = providerType;
        this.modelsPath = modelsPath;
    }

    @Override
    public ProviderType getProviderType() {
        return providerType;
    }

    /**
     * 构建 models API 的完整 URL
     */
    protected String buildModelsUrl(String apiUrl) {
        String base = apiUrl.replaceAll("/+$", "");
        // 去掉已知的路径后缀
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        } else if (base.endsWith("/models")) {
            base = base.substring(0, base.length() - "/models".length());
        }
        // modelsPath 如 "/v1/models"，检查 base 是否已包含版本路径
        if (base.endsWith("/v1")) {
            // base 已有 /v1，只需追加 /models
            return base + "/models";
        }
        if (!base.contains("/v1")) {
            return base + "/v1/models";
        }
        return base + modelsPath;
    }

    /**
     * 配置认证头。子类可覆写以使用不同的认证方式。
     */
    protected void addAuthHeaders(HttpRequest.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    @Override
    public List<ModelInfo> listModels(String apiUrl, String apiKey) throws Exception {
        String url = buildModelsUrl(apiUrl);
        LOGGER.debug("查询模型列表: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET();
        addAuthHeaders(builder, apiKey);

        HttpClient client = HttpUtils.createHttpClient();
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("查询模型列表失败, HTTP " + response.statusCode() + ": " + response.body());
        }

        return parseModelsResponse(response.body());
    }

    /**
     * 解析 models API 的 JSON 响应。子类可覆写以处理非标准格式。
     */
    protected List<ModelInfo> parseModelsResponse(String responseBody) {
        List<ModelInfo> models = new ArrayList<>();
        JSONObject json = new JSONObject(responseBody);
        JSONArray data = json.optJSONArray("data");
        if (data == null) {
            return models;
        }
        String providerName = providerType.getName();
        for (int i = 0; i < data.length(); i++) {
            JSONObject obj = data.getJSONObject(i);
            ModelInfo info = new ModelInfo();
            info.setId(obj.optString("id"));
            info.setProvider(providerName);
            info.setCreated(obj.optString("created"));
            info.setOwner(obj.optString("owned_by"));
            info.setName(obj.optString("id"));
            info.setStatus("active");
            models.add(info);
        }
        return models;
    }

    @Override
    public UsageSummary getUsage(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // OpenAI 兼容 API 通常没有标准的 usage 端点
        return null;
    }

    @Override
    public BillingSummary getBilling(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // OpenAI 兼容 API 通常没有标准的 billing 端点
        return null;
    }
}
