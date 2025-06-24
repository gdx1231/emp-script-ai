package com.gdxsoft.ai.providers.gemini.request;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 表示整个请求数据结构，包含多个 Content。
 * 适用于向 Gemini API 发送生成内容的请求。
 */
public class RequestData {
	private List<Content> contents;

	public List<Content> getContents() {
		return contents;
	}

	public void setContents(List<Content> contents) {
		this.contents = contents;
	}

	@Override
	public String toString() {
		return "RequestData{" + "contents=" + contents + '}';
	}

	public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();

        JSONArray contentsArray = new JSONArray();
        for (Content content : contents) {
            contentsArray.put(content.toJSONObject());
        }

        obj.put("contents", contentsArray);
        return obj;
    }
	
	/**
	 * 静态工厂方法：直接从 JSON 字符串构建 ApiResponse 对象。 使用 org.json 库进行内部解析。
	 * 
	 * @param jsonString 完整的 JSON 字符串
	 * @return 解析后的 RequestData 对象
	 * @throws org.json.JSONException 如果 JSON 字符串格式不正确
	 */
	public static RequestData fromJsonString(String jsonString) throws org.json.JSONException {

		JSONObject root = new JSONObject(jsonString);
		RequestData requestData = new RequestData();

		// 解析 contents 数组
		List<Content> contents = new ArrayList<>();
		JSONArray contentsArray = root.getJSONArray("contents");

		for (int i = 0; i < contentsArray.length(); i++) {
			JSONObject contentObj = contentsArray.getJSONObject(i);

			Content content = new Content();
			List<Part> parts = new ArrayList<>();

			JSONArray partsArray = contentObj.getJSONArray("parts");
			for (int j = 0; j < partsArray.length(); j++) {
				JSONObject partObj = partsArray.getJSONObject(j);

				Part part = new Part();
				part.setText(partObj.getString("text"));
				parts.add(part);
			}

			content.setParts(parts);
			contents.add(content);
		}

		requestData.setContents(contents);

		return requestData;
	}
}