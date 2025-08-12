package com.gdxsoft.ai.providers.qwen.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 用于构建发送到 Qwen（通义千问）API 的请求体
 */
public class RequestData {

    private String model = "qwen-max";           // 默认模型
    private final JSONArray messages;          // 消息列表
    private final JSONObject parameters;        // 参数（可选）

    public RequestData() {
        this.messages = new org.json.JSONArray();
        this.parameters = new JSONObject();
    }

    /**
     * 设置模型名称
     */
    public RequestData model(String model) {
        this.model = model;
        return this;
    }

    /**
	 * 设置模型是否为深度思考
     * @param thinking
     * @return
     */
	public RequestData thinking(boolean thinking) {
		parameters.put("thinking", thinking);
		return this;
	}
    
    public RequestData addMessage(String content, String role) {
    	 JSONObject message = new JSONObject();
         message.put("role", role);
         message.put("content", content);
         messages.put(message);
         return this;
    }
    /**
     * 添加用户消息
     */
    public RequestData userMessage(String content) {
        return this.addMessage(content, "user");
    }

    /**
     * 添加助手消息（可用于多轮对话）
     */
    public RequestData assistantMessage(String content) {
        return this.addMessage(content, "assistant");
    }
    /**
     * 添加助手消息（可用于多轮对话）
     */
    public RequestData systemMessage(String content) {
    	 return this.addMessage(content, "system");
    }
    /**
     * 设置 temperature
     */
    public RequestData temperature(double temp) {
        parameters.put("temperature", temp);
        return this;
    }

    /**
     * 设置 top_p
     */
    public RequestData topP(double topP) {
        parameters.put("top_p", topP);
        return this;
    }

    /**
     * 是否启用流式输出
     */
    public RequestData stream(boolean stream) {
        parameters.put("stream", stream);
        return this;
    }

    /**
     * 构建最终的请求 JSON 对象 OpenAI API 的请求体格式
     * @return 返回构建好的 JSON 对象
     */
    public JSONObject build() {
        JSONObject requestData = new JSONObject(parameters.toString());
        requestData.put("model", this.model);
        requestData.put("messages", messages);

        return requestData;
    }

    /**
     * 构建并返回字符串形式
     * @return 返回 JSON 字符串
     */
    public String buildJson() {
        return build().toString();
    }
}