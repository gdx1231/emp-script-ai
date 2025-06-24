package com.gdxsoft.ai.providers.gemini.request;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 表示一个“内容块”，包含多个 Part。
 * 在 Gemini API 中，通常表示一条用户或模型的消息。
 */
public class Content {
    private List<Part> parts;

    public List<Part> getParts() {
        return parts;
    }

    public void setParts(List<Part> parts) {
        this.parts = parts;
    }

    @Override
    public String toString() {
        return "Content{" +
                "parts=" + parts +
                '}';
    }
    
    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        JSONArray partsArray = new JSONArray();
        for (Part part : parts) {
            partsArray.put(part.toJSONObject());
        }
        obj.put("parts", partsArray);
        return obj;
    }
}