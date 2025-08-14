package com.gdxsoft.ai.providers.grok;

import org.json.JSONObject;

import com.gdxsoft.ai.providers.ProviderType;
import com.gdxsoft.ai.providers.RequestDataBase;

/**
 * 用于构建发送到 xAI Grok（OpenAI 兼容）Chat Completions API 的请求体
 */
public class RequestData extends RequestDataBase {

    public RequestData() {
        super("grok-2"); // 默认模型
        this.providerType = ProviderType.GROK;
    }

    /**
     * 构建 Grok（OpenAI 兼容）Chat Completions 请求体
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);
        return requestData;
    }
}
