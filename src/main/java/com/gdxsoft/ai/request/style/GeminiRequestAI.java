package com.gdxsoft.ai.request.style;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Google Gemini GenerateContent API 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 Gemini 格式的 Provider：
 * <ul>
 *   <li>认证：{@code x-goog-api-key}</li>
 *   <li>URL：{@code {baseUrl}/models/{model}:streamGenerateContent?alt=sse}（流式）</li>
 *   <li>SSE：{@code data: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}}</li>
 *   <li>Usage：从 {@code usageMetadata} 提取，映射为 OpenAI 格式</li>
 * </ul>
 *
 * @since 1.1.0
 */
public abstract class GeminiRequestAI extends RequestAIBase {

	@Override
	public String createUrl(IRequestData reqData) {
		String apiUrl = super.getApiUrl();
		if (apiUrl != null && apiUrl.endsWith("/")) {
			apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
		}
		apiUrl += "/models/" + reqData.getModel();
		if (reqData.isStream()) {
			return apiUrl + ":streamGenerateContent?alt=sse";
		} else {
			return apiUrl + ":generateContent";
		}
	}

	@Override
	public JSONObject extraceJson(String line, boolean skipDataPrefix) {
		String jsonData = skipDataPrefix ? line.trim() : extractDataPrefix(line);
		if (jsonData == null) {
			return UJSon.rstFalse("没有data:的数据行，" + line);
		}
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		JSONObject result = new JSONObject();
		try {
			JSONObject json = new JSONObject(jsonData);

			// 提取文本内容
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
								}
							}
						}
					}
				}
			}

			// 提取 usage 并映射为 OpenAI 格式
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
					existingUsage.put("total_tokens",
							existingUsage.optInt("total_tokens", 0) + totalTokenCount);
				} else {
					JSONObject usageOpenAi = new JSONObject();
					usageOpenAi.put("completion_tokens", candidatesTokenCount + thoughtsTokenCount);
					usageOpenAi.put("prompt_tokens", promptTokenCount);
					usageOpenAi.put("total_tokens", totalTokenCount);
					usageOpenAi.put("reasoning_tokens", thoughtsTokenCount);
					UJSon.rstSetTrue(usageOpenAi, null);
					super.setTokensUsage(usageOpenAi);
				}
			}

			if (result.has("content")) {
				UJSon.rstSetTrue(result, null);
				return result;
			}
			return UJSon.rstFalse("无效数据，没有找到文本内容，" + line);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}

	protected String extractDataPrefix(String line) {
		if (line == null || !line.startsWith("data:")) {
			return null;
		}
		return line.substring(5).trim();
	}
}
