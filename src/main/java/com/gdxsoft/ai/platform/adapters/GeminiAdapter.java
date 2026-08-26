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
 * Google Gemini 适配器。
 * <p>
 * models API: GET /v1beta/models?key=API_KEY
 */
public class GeminiAdapter implements PlatformAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiAdapter.class);
    private static final String DEFAULT_API_URL = "https://generativelanguage.googleapis.com";

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GEMINI;
    }

    @Override
    public List<ModelInfo> listModels(String apiUrl, String apiKey) throws Exception {
        String base = (apiUrl != null && !apiUrl.isEmpty()) ? apiUrl : DEFAULT_API_URL;
        base = base.replaceAll("/+$", "");
        String url = base + "/v1beta/models?key=" + apiKey;
        LOGGER.debug("查询 Gemini 模型列表: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpClient client = HttpUtils.createHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("查询 Gemini 模型列表失败, HTTP " + response.statusCode() + ": " + response.body());
        }

        List<ModelInfo> models = new ArrayList<>();
        JSONObject json = new JSONObject(response.body());
        JSONArray data = json.optJSONArray("models");
        if (data == null) {
            return models;
        }

        for (int i = 0; i < data.length(); i++) {
            JSONObject obj = data.getJSONObject(i);
            ModelInfo info = new ModelInfo();
            // Gemini 返回的 name 格式为 "models/gemini-2.5-pro"
            String name = obj.optString("name", "");
            String id = name.startsWith("models/") ? name.substring("models/".length()) : name;
            info.setId(id);
            info.setProvider(ProviderType.GEMINI.getName());
            info.setName(obj.optString("displayName", id));
            info.setModality(parseSupportedGenerationMethods(obj));
            info.setStatus(obj.optString("state", "active"));

            // 解析 inputTokenLimit
            int inputLimit = obj.optInt("inputTokenLimit", 0);
            if (inputLimit > 0) {
                info.setContextWindow(inputLimit);
            }
            int outputLimit = obj.optInt("outputTokenLimit", 0);
            if (outputLimit > 0) {
                info.setMaxOutputTokens(outputLimit);
            }

            models.add(info);
        }
        return models;
    }

    /**
     * 从 supportedGenerationMethods 推断模态
     */
    private String parseSupportedGenerationMethods(JSONObject obj) {
        JSONArray methods = obj.optJSONArray("supportedGenerationMethods");
        if (methods == null) {
            return "text";
        }
        boolean hasVision = false;
        for (int i = 0; i < methods.length(); i++) {
            String m = methods.getString(i).toLowerCase();
            if (m.contains("image") || m.contains("vision")) {
                hasVision = true;
            }
        }
        return hasVision ? "text+vision" : "text";
    }

    @Override
    public UsageSummary getUsage(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // Gemini 暂无公开的 usage API
        return null;
    }

    @Override
    public BillingSummary getBilling(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        // Gemini 暂无公开的 billing API
        return null;
    }
}
