package com.gdxsoft.ai.request.style;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
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
			if (json.has("usage")) {
				JSONObject usage = json.getJSONObject("usage");
				this.setTokensUsage(usage);
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
}
