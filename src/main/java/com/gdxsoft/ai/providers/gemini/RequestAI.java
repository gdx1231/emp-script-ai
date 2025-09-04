package com.gdxsoft.ai.providers.gemini;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// Gemini的流式API网址
	public static final String DEFAULT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:streamGenerateContent";

	public RequestAI() {
		this.providerType = ProviderType.GEMINI;
	}

	/**
	 * Gemini请求地址，分为流式和非流式两种
	 */
	public String createUrl(IRequestData reqData) {
		String apiUrl = super.getApiUrl();
		if (apiUrl.endsWith("/")) {
			apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
		}
		apiUrl += "/models/" + reqData.getModel();
		if (reqData.isStream()) {
			return apiUrl + ":streamGenerateContent?alt=sse";
		} else {
			return apiUrl + ":generateContent";
		}
	}

	/**
	 * 提取Gemini返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line, boolean skipDataPrefix) {
		/*
		 * data: {"candidates":[{"content":{"parts":[{"text":"Hello"}],"role":"model"},
		 * "finishReason":"STOP","index":0,"safetyRatings":[...]}],"promptFeedback":{...
		 * }}
		 */
		// 提取 data: 后面的 JSON
		String jsonData = null;
		if (!skipDataPrefix) {
			// 如果不跳过前缀，则必须以 "data:" 开头
			if (!line.startsWith("data:")) {
				return UJSon.rstFalse("没有data:的数据行，" + line);
			}

			jsonData = line.substring(5).trim();
		} else {
			jsonData = line.trim();
		}
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		// System.out.println(jsonData);
		// System.out.println();
		JSONObject result = new JSONObject();
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
									result.put("content", part.getString("text"));
									UJSon.rstSetTrue(result, null);
								}
							}
						}
					}
				}
			}
			// 使用的Token数量
			// "usageMetadata": {"promptTokenCount": 25,"candidatesTokenCount":
			// 5,"totalTokenCount": 48,"promptTokensDetails": [{"modality":
			// "TEXT","tokenCount": 25}],"thoughtsTokenCount": 18}

			// openai 格式
			// "usage":{"prompt_tokens":29,"completion_tokens":68,"total_tokens":97,"prompt_tokens_details":{"cached_tokens":0}}

			if (json.has("usageMetadata")) {
				JSONObject usage = json.getJSONObject("usageMetadata");
				int candidatesTokenCount = usage.optInt("candidatesTokenCount");
				int promptTokenCount = usage.optInt("promptTokenCount");
				int totalTokenCount = usage.optInt("totalTokenCount");
				int thoughtsTokenCount = usage.optInt("thoughtsTokenCount");

				var existingUsage = super.getTokensUsage();
				if (existingUsage != null) {
					existingUsage.put("completion_tokens",
							existingUsage.optInt("completion_tokens", 0) + candidatesTokenCount + thoughtsTokenCount);
					existingUsage.put("reasoning_tokens",
							existingUsage.optInt("reasoning_tokens", 0) + thoughtsTokenCount);
					existingUsage.put("total_tokens", existingUsage.optInt("total_tokens", 0) + totalTokenCount);
					
					System.out.println("Gemini usage: " + existingUsage.toString(2));
				} else {
					JSONObject usageOpenAi = new JSONObject();
					usageOpenAi.put("completion_tokens", candidatesTokenCount + thoughtsTokenCount);
					usageOpenAi.put("prompt_tokens", promptTokenCount);
					usageOpenAi.put("total_tokens", totalTokenCount);
					/*
					 * OpenAI 的 reasoning_tokens 更侧重于“隐藏”推理（不返回思考内容），适用于 o1 等模型的“黑箱”优化。 Gemini 的
					 * thoughtsTokenCount 更透明，可返回思考摘要（summaries）或签名（signatures）以维护多轮上下文。 OpenAI 标准
					 * API 无此字段；需使用特定模型或 Azure 变体。Gemini 在 2.5 系列中更普遍。
					 */
					usageOpenAi.put("reasoning_tokens", thoughtsTokenCount);
					UJSon.rstSetTrue(usageOpenAi, null);
					System.out.println("Gemini usage: " + usageOpenAi.toString(2));
					super.setTokensUsage(usageOpenAi);
				}
			}
			if (result.has("content")) {
				return result;
			} else {
				return UJSon.rstFalse("无效数据，没有找到文本内容，" + line);
			}
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
