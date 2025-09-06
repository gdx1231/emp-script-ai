package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI 提供商请求数据基类
 * 包含各个 RequestData 实现类的公共方法和字段
 */
public abstract class RequestDataBase implements IRequestData {
    protected ProviderType providerType;

    protected String model;
    protected final JSONArray messages;
    protected final JSONObject parameters;

    public RequestDataBase(String defaultModel) {
        this.model = defaultModel;
        this.messages = new JSONArray();
        this.parameters = new JSONObject();
        this.providerType = null; // 由子类设置
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public JSONObject getParameters() {
        return parameters;
    }

    public boolean isStream() {
        return this.parameters.optBoolean("stream");
    }

    public String getModel() {
        return this.model;
    }

    public JSONArray getMessages() {
        return messages;
    }

    /**
     * 获取 AI 提供商类型
     */
    public String getProviderName() {
        if (providerType == null) {
            return "unknown";
        }
        return providerType.toString();
    }

    /**
     * 设置模型名称
     */
    @Override
    public IRequestData model(String model) {
        this.model = model;
        return this;
    }

    /**
     * 添加消息 - 默认实现，子类可以重写
     */
    @Override
    public IRequestData addMessage(String content, String role) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        messages.put(message);
        return this;
    }

    /**
     * 添加用户消息
     */
    @Override
    public IRequestData userMessage(String content) {
        return this.addMessage(content, "user");
    }

    /**
     * 添加助手消息
     */
    @Override
    public IRequestData assistantMessage(String content) {
        return this.addMessage(content, "assistant");
    }

    /**
     * 添加系统消息
     */
    @Override
    public IRequestData systemMessage(String content) {
        return this.addMessage(content, "system");
    }

    /**
     * 设置 temperature - 默认实现，子类可以重写
     */
    @Override
    public IRequestData temperature(double temp) {
        parameters.put("temperature", temp);
        return this;
    }

    public String getResponseFormat() {
        return this.parameters.optString("response_format", "text");
    }

    /**
     * 设置响应格式。返回内容的格式。可选值：{"type": "text"}或{"type": "json_object"}。<br>
     * 例如，设置为 json_object 时，返回结果将是一个 JSON 对象，而不是纯文本。<br>
     * 适用于需要结构化数据的场景，如请求返回一个包含特定字段的 JSON 对象。<br>
     * 示例:
     * {<br>
     * "model": "gpt-4o",<br>
     * "messages": [<br>
     * {<br>
     * "role": "user",<br>
     * "content": "请返回一个包含用户名和年龄的 JSON 对象"<br>
     * }<br>
     * ],<br>
     * "<b>response_format</b>": {<br>
     * "type": "<b>json_object</b>"<br>
     * }<br>
     * }
     */
    @Override
    public IRequestData responseFormat(String format) {
        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", format);
        this.parameters.put("response_format", responseFormat);
        return this;
    }

    /**
     * 设置 thinking
     */
    @Override
    public IRequestData thinking(boolean thinking) {
        parameters.put("thinking", thinking);
        return this;
    }

    /**
     * 设置 top_p - 默认实现，子类可以重写
     */
    @Override
    public IRequestData topP(double topP) {
        parameters.put("top_p", topP);
        return this;
    }

    /**
     * 是否启用流式输出 - 默认实现，子类可以重写
     */
    @Override
    public IRequestData stream(boolean stream) {
        parameters.put("stream", stream);
        if (stream) {
            parameters.put("stream_options", new JSONObject("{\"include_usage\": true}"));
        } else {
            parameters.remove("stream_options");
        }
        return this;
    }

    /**
     * 最大tokens，可选 - 通用方法
     */
    public IRequestData maxTokens(int maxTokens) {
        parameters.put("max_tokens", maxTokens);
        return this;
    }

    /**
     * 构建并返回字符串形式
     */
    @Override
    public String buildJson() {
        return build().toString();
    }

    /**
     * 构建最终的请求 JSON 对象
     * 子类需要实现具体的构建逻辑
     */
    @Override
    public abstract JSONObject build();
}
