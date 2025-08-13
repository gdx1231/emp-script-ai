package com.gdxsoft.ai.providers.gemini.request;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.gdxsoft.ai.providers.RequestDataBase;

/**
 * 用于构建发送到 Google Gemini API 的请求体
 * Gemini API 使用不同的格式：contents 包含 parts 结构
 */
public class RequestData extends RequestDataBase {

    private final List<Content> contents;       // Gemini 特有的 contents 结构

    public RequestData() {
        super("gemini-1.5-flash");  // 默认模型
        this.contents = new ArrayList<>();
    }

    /** 添加消息 - Gemini 不使用传统的 role 概念，而是通过 contents 结构 */
    @Override
    public RequestData addMessage(String content, String role) {
        // 对于 Gemini，我们将文本内容包装成 Part -> Content 结构
        Part part = new Part();
        part.setText(content);
        
        List<Part> parts = new ArrayList<>();
        parts.add(part);
        
        Content contentObj = new Content();
        contentObj.setParts(parts);
        
        contents.add(contentObj);
        return this;
    }

    /** 设置 temperature - Gemini 中在 generationConfig 中 */
    @Override
    public RequestData temperature(double temp) {
        // Gemini API 中，这些参数通常在 generationConfig 中
        if (!parameters.has("generationConfig")) {
            parameters.put("generationConfig", new JSONObject());
        }
        parameters.getJSONObject("generationConfig").put("temperature", temp);
        return this;
    }

    /** 设置 top_p - 在 Gemini 中叫 topP */
    @Override
    public RequestData topP(double topP) {
        if (!parameters.has("generationConfig")) {
            parameters.put("generationConfig", new JSONObject());
        }
        parameters.getJSONObject("generationConfig").put("topP", topP);
        return this;
    }

    /** 是否启用流式输出 - Gemini 通过不同的端点支持流式 */
    @Override
    public RequestData stream(boolean stream) {
        // Gemini 的流式输出通过端点区别，这里记录参数供调用方使用
        parameters.put("stream", stream);
        return this;
    }

    /** 设置最大输出 tokens */
    public RequestData maxOutputTokens(int maxTokens) {
        if (!parameters.has("generationConfig")) {
            parameters.put("generationConfig", new JSONObject());
        }
        parameters.getJSONObject("generationConfig").put("maxOutputTokens", maxTokens);
        return this;
    }

    /** 
     * 直接添加 Content 对象 - 用于更精细的控制
     */
    public RequestData addContent(Content content) {
        this.contents.add(content);
        return this;
    }

    /** 
     * 获取模型名称 - 供外部调用者使用
     */
    public String getModel() {
        return this.model;
    }

    /** 
     * 是否为流式请求 - 供外部调用者判断
     */
    public boolean isStream() {
        return parameters.optBoolean("stream", false);
    }

    /**
     * 构建 Gemini API 请求体
     * 格式: { "contents": [...], "generationConfig": {...} }
     */
    @Override
    public JSONObject build() {
        JSONObject requestData = new JSONObject();
        
        // 添加 contents 数组
        List<JSONObject> contentsJson = new ArrayList<>();
        for (Content content : contents) {
            contentsJson.add(content.toJSONObject());
        }
        requestData.put("contents", contentsJson);
        
        // 添加其他参数
        if (parameters.has("generationConfig")) {
            requestData.put("generationConfig", parameters.getJSONObject("generationConfig"));
        }
        
        return requestData;
    }

    /**
     * 兼容原有的 toJSONObject 方法
     */
    public JSONObject toJSONObject() {
        return build();
    }

    /**
     * 设置 contents - 用于直接设置整个 contents 列表
     */
    public void setContents(List<Content> contents) {
        this.contents.clear();
        this.contents.addAll(contents);
    }

    /**
     * 获取 contents - 用于外部访问
     */
    public List<Content> getContents() {
        return contents;
    }
}
