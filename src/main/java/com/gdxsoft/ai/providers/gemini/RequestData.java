package com.gdxsoft.ai.providers.gemini;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.providers.ProviderType;
import com.gdxsoft.ai.providers.RequestDataBase;

/**
 * 用于构建发送到 Google Gemini API 的请求体
 */
public class RequestData extends RequestDataBase {

    public RequestData() {
        super("gemini-1.5-flash-latest"); // 默认模型
        this.providerType = ProviderType.GEMINI;
    }

    /**
     * 构建最终的请求 JSON 对象
     * 
     * @return 返回构建好的 JSON 对象
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject();
        JSONArray contents = new JSONArray();

        for (int i = 0; i < this.messages.length(); i++) {
            JSONObject message = this.messages.getJSONObject(i);
            String role = message.getString("role");
            String content = message.getString("content");

            // Gemini API 使用 "model" 而不是 "assistant"
            if ("assistant".equals(role)) {
                role = "model";
            }

            JSONObject contentItem = new JSONObject();
            contentItem.put("role", role);

            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", content);
            parts.put(part);

            contentItem.put("parts", parts);
            contents.put(contentItem);
        }

        requestData.put("contents", contents);

        // an add parameters, for example:
        // "generationConfig": {
        // "temperature": 1,
        // "topK": 0,
        // "topP": 0.95,
        // "maxOutputTokens": 8192,
        // "stopSequences": []
        // },
        if (this.parameters.length() > 0) {
            requestData.put("generationConfig", this.parameters);
        }

        return requestData;
    }
}
