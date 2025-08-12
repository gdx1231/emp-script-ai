package com.gdxsoft.ai.providers.grok.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 用于构建发送到 xAI Grok（OpenAI 兼容）Chat Completions API 的请求体
 */
public class RequestData {

    private String model = "grok-2";   // 默认模型，可按需覆盖
    private final JSONArray messages;    // 消息列表
    private final JSONObject parameters; // 其他参数

    public RequestData() {
        this.messages = new JSONArray();
        this.parameters = new JSONObject();
    }

    /** 设置模型名称 */
    public RequestData model(String model) {
        this.model = model;
        return this;
    }

    /** 添加消息 */
    public RequestData addMessage(String content, String role) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        messages.put(message);
        return this;
    }

    /** 添加用户消息 */
    public RequestData userMessage(String content) { return this.addMessage(content, "user"); }

    /** 添加助手消息 */
    public RequestData assistantMessage(String content) { return this.addMessage(content, "assistant"); }

    /** 添加系统消息 */
    public RequestData systemMessage(String content) { return this.addMessage(content, "system"); }

    /** 设置 temperature */
    public RequestData temperature(double temp) {
        parameters.put("temperature", temp);
        return this;
    }

    /** 设置 top_p */
    public RequestData topP(double topP) {
        parameters.put("top_p", topP);
        return this;
    }

    /** 是否启用流式输出 */
    public RequestData stream(boolean stream) {
        parameters.put("stream", stream);
        return this;
    }

    /** 最大 tokens，可选 */
    public RequestData maxTokens(int maxTokens) {
        parameters.put("max_tokens", maxTokens);
        return this;
    }

    /**
     * 构建 Grok（OpenAI 兼容）Chat Completions 请求体
     */
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);
        return requestData;
    }

    /** 返回 JSON 字符串 */
    public String buildJson() { return build().toString(); }
}
