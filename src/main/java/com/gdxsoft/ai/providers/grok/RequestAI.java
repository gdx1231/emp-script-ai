package com.gdxsoft.ai.providers.grok;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.providers.ProviderType;
import com.gdxsoft.ai.providers.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// Grok的流式API网址（xAI API）
	public static final String DEFAULT_URL = "https://api.x.ai/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.GROK;
	}

	/**
	 * 提取Grok返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line) {
		/*
		 * data:
		 * {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1234567890,
		 * "model":"grok-beta","choices":[{"index":0,"delta":{"content":"Hello"},
		 * "finish_reason":null}]}
		 */
		// 提取 data: 后面的 JSON

		if (!line.startsWith("data:")) {
			return UJSon.rstFalse("没有data:的数据行，" + line);
		}

		String jsonData = line.substring(5).trim();
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		// 检查是否是结束标记
		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
		}

		// System.out.println(jsonData);
		// System.out.println();

		try {
			JSONObject json = new JSONObject(jsonData);
			JSONArray choices = json.getJSONArray("choices");
			if (choices.length() > 0) {
				JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
				UJSon.rstSetTrue(delta, null);
				return delta;
			}
			return UJSon.rstFalse("无效数据，choices.length() = 0" + line);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
