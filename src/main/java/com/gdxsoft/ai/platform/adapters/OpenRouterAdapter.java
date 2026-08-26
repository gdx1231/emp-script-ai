package com.gdxsoft.ai.platform.adapters;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.platform.*;
import com.gdxsoft.ai.request.ProviderType;

/**
 * OpenRouter 适配器。
 * <p>
 * models API: GET /api/v1/models<br>
 * credits 查询: GET /api/v1/auth/credits（可用作 billing 参考）
 */
public class OpenRouterAdapter extends OpenAiCompatibleAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRouterAdapter.class);

    public OpenRouterAdapter() {
        super(ProviderType.OPENROUTER, "/api/v1/models");
    }

    @Override
    protected String buildModelsUrl(String apiUrl) {
        String base = apiUrl.replaceAll("/+$", "");
        // 去掉已知后缀
        if (base.endsWith("/chat/completions")) {
            base = base.substring(0, base.length() - "/chat/completions".length());
        } else if (base.endsWith("/models")) {
            base = base.substring(0, base.length() - "/models".length());
        }
        // 确保以 /api/v1 结尾
        if (base.endsWith("/api/v1")) {
            return base + "/models";
        }
        if (base.endsWith("/v1")) {
            return base + "/models";
        }
        return base + "/api/v1/models";
    }

    @Override
    public BillingSummary getBilling(String apiUrl, String apiKey, LocalDate start, LocalDate end) {
        try {
            String base = apiUrl.replaceAll("/+$", "");
            int idx = base.indexOf("/api/");
            if (idx > 0) {
                base = base.substring(0, idx);
            }
            String url = base + "/api/v1/auth/credits";

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET();
            addAuthHeaders(builder, apiKey);

            HttpClient client = HttpUtils.createHttpClient();
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("OpenRouter credits 查询失败: HTTP {}", response.statusCode());
                return null;
            }

            JSONObject json = new JSONObject(response.body());
            BillingSummary summary = new BillingSummary();
            summary.setProvider(ProviderType.OPENROUTER.getName());
            summary.setCurrency("USD");

            BillingRecord record = new BillingRecord();
            record.setModel("openrouter-credits");
            record.setTotalCost(json.optDouble("total_balance", 0));
            record.setCurrency("USD");
            summary.addRecord(record);
            summary.setTotalCost(record.getTotalCost());

            return summary;
        } catch (Exception e) {
            LOGGER.warn("OpenRouter credits 查询异常: {}", e.getMessage());
            return null;
        }
    }
}
