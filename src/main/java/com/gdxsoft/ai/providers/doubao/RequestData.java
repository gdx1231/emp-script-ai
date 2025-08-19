package com.gdxsoft.ai.providers.doubao;

import org.json.JSONObject;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 豆包（Doubao/火山引擎）OpenAI 兼容聊天补全请求体
 */
public class RequestData extends RequestDataBase {
    public static final String DEFAULT_MODEL_NAME = "doubao-seed-1-6-250615"; // 示例：豆包 Ark 端点 ID 或模型名

    public RequestData() {
        super(DEFAULT_MODEL_NAME);
        this.providerType = ProviderType.DOUBAO;
        // 默认开启流式
        this.parameters.put("stream", true);
    }

    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        
		if (requestData.has("thinking") && requestData.optBoolean("thinking")) {
			requestData.put("thinking", new JSONObject("{\"type\":\"enabled\"}"));
		} else {
			//关闭思考
			requestData.put("thinking", new JSONObject("{\"type\":\"disabled\"}"));
        }
        
        requestData.put("model", this.model);
        requestData.put("messages", messages);
        return requestData;
    }
}
