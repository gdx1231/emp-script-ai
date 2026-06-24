package com.gdxsoft.ai.request.style;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
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

	/**
	 * 列出可用的 AI 模型（Gemini 风格）。
	 * <p>
	 * List available AI models (Gemini style).
	 * 
	 * @return JSON 格式的模型列表| Model list in JSON format
	 * @throws IOException          IO异常
	 * @throws URISyntaxException   URL 语法错误
	 * @throws InterruptedException 线程中断
	 */
	@Override
	public JSONObject listModels() throws IOException, URISyntaxException, InterruptedException {
		String apiUrl = super.getApiUrl();
		if (apiUrl == null || apiUrl.isEmpty()) {
			return UJSon.rstFalse("API URL 未设置");
		}

		// 构建 models 路径
		String modelsUrl;
		if (apiUrl.endsWith("/")) {
			modelsUrl = apiUrl + "models";
		} else {
			modelsUrl = apiUrl + "/models";
		}

		// 创建 HTTP 请求
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(new URI(modelsUrl))
				.header("Content-Type", "application/json");

		// 添加认证头
		if (super.getApiKey() != null && !super.getApiKey().isEmpty()) {
			builder.header("x-goog-api-key", super.getApiKey());
		}

		HttpRequest request = builder.GET().build();

		// 发送请求
		var client = HttpUtils.createHttpClient();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		int statusCode = response.statusCode();
		if (statusCode == 200) {
			try {
				JSONObject json = new JSONObject(response.body());
				JSONArray models = json.optJSONArray("models");
				if (models != null) {
					// 转换为标准格式
					JSONArray modelList = new JSONArray();
					for (int i = 0; i < models.length(); i++) {
						JSONObject model = models.getJSONObject(i);
						JSONObject item = new JSONObject();
						item.put("id", model.optString("name"));
						item.put("object", "model");
						item.put("displayName", model.optString("displayName"));
						item.put("description", model.optString("description"));
						item.put("version", model.optString("version"));
						modelList.put(item);
					}
					JSONObject result = new JSONObject();
					result.put("data", modelList);
					result.put("object", "list");
					UJSon.rstSetTrue(result, null);
					return result;
				} else {
					return UJSon.rstFalse("无效的响应格式，未找到 models 数组");
				}
			} catch (Exception e) {
				return UJSon.rstFalse("解析响应失败: " + e.getMessage());
			}
		} else {
			return UJSon.rstFalse("请求失败，状态码: " + statusCode + ", 响应: " + response.body());
		}
	}
}
