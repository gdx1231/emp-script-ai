package com.gdxsoft.ai.providers.gemini.request;

import org.json.JSONObject;
/**
 * 表示一个“部分”内容，例如文本、图片等。
 * 在 Gemini API 中，通常用于封装单个消息片段。
 */
public class Part {
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "Part{" +
                "text='" + text + '\'' +
                '}';
    }
    
    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("text", text);
        return obj;
    }
}