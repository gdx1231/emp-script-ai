package com.gdxsoft.ai.providers;

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

    /**
     * 设置 thinking - 仅 Qwen 模型使用，默认实现返回自身
     */
    @Override
    public IRequestData thinking(boolean thinking) {
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
