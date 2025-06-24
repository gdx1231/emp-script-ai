package com.gdxsoft.ai.providers.gemini.response;

import org.json.JSONObject;


//Part 类
public class Part {
	private String text;

	// 无参构造函数
	public Part() {
	}

	// Getters and Setters
	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return "Part{" + "text='" + text + '\'' + '}';
	}

	/**
	 * 静态工厂方法：从 JSONObject 构建 Part 对象。
	 * 
	 * @param jsonObject org.json.JSONObject 对象
	 * @return 解析后的 Part 对象
	 * @throws org.json.JSONException 如果 JSONObject 缺少必要字段或类型不匹配
	 */
	public static Part fromJsonObject(JSONObject jsonObject) throws org.json.JSONException {
		Part part = new Part();
		if (jsonObject.has("text")) {
			part.setText(jsonObject.getString("text"));
		}
		return part;
	}
}
