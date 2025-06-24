package com.gdxsoft.ai.providers.gemini.response;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;


//Content 类
public class Content {
	private List<Part> parts;
	private String role;

	// 无参构造函数
	public Content() {
	}

	// Getters and Setters
	public List<Part> getParts() {
		return parts;
	}

	public void setParts(List<Part> parts) {
		this.parts = parts;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	@Override
	public String toString() {
		return "Content{" + "parts=" + parts + ", role='" + role + '\'' + '}';
	}

	/**
     * 静态工厂方法：从 JSONObject 构建 Content 对象。
     * @param jsonObject org.json.JSONObject 对象
     * @return 解析后的 Content 对象
     * @throws org.json.JSONException 如果 JSONObject 缺少必要字段或类型不匹配
     */
    public static Content fromJsonObject(JSONObject jsonObject) throws org.json.JSONException {
        Content content = new Content();
        if (jsonObject.has("role")) {
            content.setRole(jsonObject.getString("role"));
        }

        // 解析 parts 数组
        if (jsonObject.has("parts")) {
            JSONArray rawPartsArray = jsonObject.getJSONArray("parts");
            List<Part> partsList = new ArrayList<>();
            for (int i = 0; i < rawPartsArray.length(); i++) {
                JSONObject rawPart = rawPartsArray.getJSONObject(i);
                partsList.add(Part.fromJsonObject(rawPart));
            }
            content.setParts(partsList);
        }
        return content;
    }
}
