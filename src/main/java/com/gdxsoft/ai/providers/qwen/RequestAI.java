package com.gdxsoft.ai.providers.qwen;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.providers.ProviderType;
import com.gdxsoft.ai.providers.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// 通义千问的流式API 网址，openai兼容模式
	public static final String DEFAULT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.QWEN;
	}

	/**
	 * 提取通义千问返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line) {
		/*
		 * data: {"choices":[{"delta":{"content":"旨在为用户提供全面"}
		 * ,"finish_reason":null,"index":0,"logprobs":null}]
		 * ,"object":"chat.completion.chunk","usage":null,"created":1754902913
		 * ,"system_fingerprint":null,"model":"qwen-turbo"
		 * ,"id":"chatcmpl-500b1fb4-2b18-9cc7-82d5-c9d0e9fd38a9"}
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
