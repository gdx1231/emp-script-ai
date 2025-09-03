package com.gdxsoft.ai.providers.tencent;

import org.json.JSONObject;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 OpenAI Chat Completions API 的请求体
 */
public class RequestData extends RequestDataBase {

    public RequestData() {
        super("gpt-4o-mini"); // 默认模型
        this.providerType = ProviderType.OPENAI;
    }

    /**
     * 构建 OpenAI Chat Completions 请求体
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);
        return requestData;
    }
}
