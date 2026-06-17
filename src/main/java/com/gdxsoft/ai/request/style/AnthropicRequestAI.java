package com.gdxsoft.ai.request.style;

import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Anthropic Messages API 风格 AI 请求抽象基类。
 * <p>
 * 适用于所有使用 Anthropic Messages API 格式的 Provider：
 * <ul>
 *   <li>认证：{@code x-api-key} + {@code anthropic-version: 2023-06-01}</li>
 *   <li>SSE 事件：{@code content_block_delta} / {@code message_delta} / {@code message_start}</li>
 *   <li>内容提取：从 {@code delta.text} 获取文本</li>
 * </ul>
 * <p>
 * 子类只需设置 {@code providerType} 即可。
 *
 * @since 1.1.0
 */
public abstract class AnthropicRequestAI extends RequestAIBase {

	public static final String API_VERSION = "2023-06-01";

	@Override
	public String createUrl(IRequestData reqData) {
		return super.getApiUrl();
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
		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
		}

		try {
			JSONObject json = new JSONObject(jsonData);
			String type = json.optString("type", "");

			// content_block_delta: 实际文本内容
			if ("content_block_delta".equals(type) && json.has("delta")) {
				JSONObject delta = json.getJSONObject("delta");
				if ("text_delta".equals(delta.optString("type")) && delta.has("text")) {
					UJSon.rstSetTrue(delta, null);
					return delta;
				}
			}

			// message_delta: usage 信息
			if ("message_delta".equals(type) && json.has("usage")) {
				JSONObject usage = json.getJSONObject("usage");
				UJSon.rstSetTrue(usage, null);
				this.setTokensUsage(usage);
				return usage;
			}

			// message_start: 初始 usage
			if ("message_start".equals(type) && json.has("message")) {
				JSONObject message = json.getJSONObject("message");
				if (message.has("usage")) {
					JSONObject usage = message.getJSONObject("usage");
					UJSon.rstSetTrue(usage, null);
					this.setTokensUsage(usage);
				}
				return UJSon.rstTrue("message_start");
			}

			return UJSon.rstFalse("未处理的事件类型: " + type);
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
