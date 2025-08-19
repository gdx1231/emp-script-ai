package com.gdxsoft.ai.providers.gemini;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// Gemini的流式API网址
	public static final String DEFAULT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:streamGenerateContent";

	public RequestAI() {
		this.providerType = ProviderType.GEMINI;
	}

	/**
	 * 提取Gemini返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line) {
		/*
		 * data: {"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"},
		 * "finishReason":"STOP","index":0,"safetyRatings":[...]}],"promptFeedback":{...
		 * }}
		 */
		// 提取 data: 后面的 JSON

		if (!line.startsWith("data:")) {
			return UJSon.rstFalse("没有data:的数据行，" + line);
		}

		String jsonData = line.substring(5).trim();
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		// System.out.println(jsonData);
		// System.out.println();

		try {
			JSONObject json = new JSONObject(jsonData);
			if (json.has("candidates")) {
				JSONArray candidates = json.getJSONArray("candidates");
				if (candidates.length() > 0) {
					JSONObject candidate = candidates.getJSONObject(0);
					if (candidate.has("content")) {
						JSONObject content = candidate.getJSONObject("content");
						if (content.has("parts")) {
							JSONArray parts = content.getJSONArray("parts");
							if (parts.length() > 0) {
								JSONObject part = parts.getJSONObject(0);
								if (part.has("text")) {
									JSONObject result = new JSONObject();
									result.put("content", part.getString("text"));
									UJSon.rstSetTrue(result, null);
									return result;
								}
							}
						}
					}
				}
			}
			return UJSon.rstFalse("无效数据，没有找到文本内容，" + line);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
