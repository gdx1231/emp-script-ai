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
 * OpenAI Chat Completions 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 OpenAI 兼容格式的 Provider：
 * <ul>
 *   <li>认证：{@code Authorization: Bearer {apiKey}}</li>
 *   <li>URL：直接使用 {@code apiUrl}（由数据库 AI_PROVIDER_URL 配置）</li>
 *   <li>SSE：{@code data: {"choices":[{"delta":{"content":"..."}}]}}</li>
 *   <li>结束标记：{@code data: [DONE]}</li>
 * </ul>
 * <p>
 * 子类只需设置 {@code providerType} 即可。
 * HTTP 请求、流式处理、JSON 解析全部继承。
 *
 * @since 1.1.0
 */
public abstract class OpenAiRequestAI extends RequestAIBase {

	/**
	 * 创建请求 URL，直接使用 apiUrl。
	 */
	@Override
	public String createUrl(IRequestData reqData) {
		return super.getApiUrl();
	}

	/**
	 * 提取 OpenAI 兼容格式的 JSON 数据。
	 * <p>
	 * 同时支持流式（delta）和非流式（message）响应。
	 * 如果响应中包含 usage 字段，会保存到 tokensUsage 中。
	 */
	@Override
	public JSONObject extraceJson(String line, boolean skipDataPrefix) {
		String jsonData = skipDataPrefix ? line.trim() : extractDataPrefix(line);
		if (jsonData == null) {
			return UJSon.rstFalse("没有data:的数据行，" + line);
		}
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}
		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
		}

		try {
			JSONObject json = new JSONObject(jsonData);

			// 保存 usage 信息（非流式响应和流式结束帧都可能有）
			// 使用 optJSONObject 避免 usage:null 时抛异常（Qwen/DeepSeek 等 SSE chunk 中 usage 常为 null）
			JSONObject usageObj = json.optJSONObject("usage");
			if (usageObj != null) {
				this.setTokensUsage(usageObj);
			}

			JSONArray choices = json.optJSONArray("choices");
			if (choices != null && choices.length() > 0) {
				JSONObject choice = choices.getJSONObject(0);
				// 流式：delta
				if (choice.has("delta")) {
					JSONObject delta = choice.getJSONObject("delta");
					UJSon.rstSetTrue(delta, null);
					return delta;
				}
				// 非流式：message
				if (choice.has("message")) {
					JSONObject message = choice.getJSONObject("message");
					UJSon.rstSetTrue(message, null);
					return message;
				}
			}
			return UJSon.rstFalse("无效数据，choices 为空或无 delta/message，" + line);
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
	 * 列出可用的 AI 模型（OpenAI 兼容风格）。
	 * <p>
	 * List available AI models (OpenAI compatible style).
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

		// 从 chat/completions 路径推导出 models 路径
		String modelsUrl;
		if (apiUrl.endsWith("/chat/completions")) {
			modelsUrl = apiUrl.substring(0, apiUrl.length() - "/chat/completions".length()) + "/models";
		} else if (apiUrl.endsWith("/")) {
			modelsUrl = apiUrl + "models";
		} else {
			modelsUrl = apiUrl + "/models";
		}

		// 创建 HTTP 请求
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(new URI(modelsUrl))
				.header("Content-Type", "application/json");

		// 添加认证头
		if (super.getApiKey() != null && !super.getApiKey().isEmpty()) {
			if (ProviderType.GEMINI == super.getProviderType()) {
				builder.header("x-goog-api-key", super.getApiKey());
			} else if (ProviderType.ANTHROPIC == super.getProviderType()
					|| ProviderType.ANTHROPIC_COMPAT == super.getProviderType()) {
				builder.header("x-api-key", super.getApiKey());
				builder.header("anthropic-version", "2023-06-01");
			} else {
				builder.header("Authorization", "Bearer " + super.getApiKey());
			}
		}

		HttpRequest request = builder.GET().build();

		// 发送请求
		var client = HttpUtils.createHttpClient();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		int statusCode = response.statusCode();
		if (statusCode == 200) {
			try {
				JSONObject json = new JSONObject(response.body());
				JSONArray models = json.optJSONArray("data");
				if (models != null) {
					// 转换为标准格式
					JSONArray modelList = new JSONArray();
					for (int i = 0; i < models.length(); i++) {
						JSONObject model = models.getJSONObject(i);
						JSONObject item = new JSONObject();
						item.put("id", model.optString("id"));
						item.put("object", model.optString("object"));
						item.put("created", model.optLong("created"));
						item.put("owned_by", model.optString("owned_by"));
						modelList.put(item);
					}
					JSONObject result = new JSONObject();
					result.put("data", modelList);
					result.put("object", "list");
					UJSon.rstSetTrue(result, null);
					return result;
				} else {
					return UJSon.rstFalse("无效的响应格式，未找到 data 数组");
				}
			} catch (Exception e) {
				return UJSon.rstFalse("解析响应失败: " + e.getMessage());
			}
		} else {
			return UJSon.rstFalse("请求失败，状态码: " + statusCode + ", 响应: " + response.body());
		}
	}
}
