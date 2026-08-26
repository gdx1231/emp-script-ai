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
 * Anthropic 适配器。
 * <p>
 * models API: GET /v1/models（需要 x-api-key + anthropic-version 头）
 */
public class AnthropicAdapter implements PlatformAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnthropicAdapter.class);
    private static final String DEFAULT_API_URL = "https://api.anthropic.com";

    @Override
    public ProviderType getProviderType() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public List<ModelInfo> listModels(String apiUrl, String apiKey) throws Exception {
        String base = (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : DEFAULT_API_URL;
        base = base.replaceAll("/+$", "");
        String url = base + "/v1/models";
        LOGGER.debug("查询 Anthropic 模型列表: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .GET()
                .build();

        HttpClient client = HttpUtils.createHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("查询 Anthropic 模型列表失败, HTTP " + response.statusCode() + ": " + response.body());
        }

        List<ModelInfo> models = new ArrayList<>();
        JSONObject json = new JSONObject(response.body());
        JSONArray data = json.optJSONArray("data");
        if (data == null) {
            return models;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject obj = data.getJSONObject(i);
            ModelInfo info = new ModelInfo();
            info.setId(obj.optString("id"));
            info.setProvider(ProviderType.ANTHROPIC.getName());
            info.setName(obj.optString("display_name", obj.optString("id")));
            info.setStatus(obj.optString("status", "active"));
            info.setCreated(obj.optString("created_at"));
            models.add(info);
        }
        return models;
    }

    @Override
    public UsageSummary getUsage(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // Anthropic 暂无官方 usage API
        return null;
    }

    @Override
    public BillingSummary getBilling(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // Anthropic 暂无官方 billing API
        return null;
    }
}
