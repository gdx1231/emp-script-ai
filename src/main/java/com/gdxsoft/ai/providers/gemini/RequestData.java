package com.gdxsoft.ai.providers.gemini;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 用于构建发送到 Google Gemini API 的请求体
 */
public class RequestData extends RequestDataBase {

	public RequestData() {
		super("gemini-2.5-flash"); // 默认模型
		this.providerType = ProviderType.GEMINI;
	}

	/**
	 * Gemini API 使用 "model" 而不是 "assistant", "system"改为"user"
	 */
	@Override
	public IRequestData addMessage(String content, String role) {
		String geminiRole;
		if ("system".equals(role)) {
			geminiRole = "user";
		} else if ("assistant".equals(role)) {
			geminiRole = "model";
		} else {
			geminiRole = role;
		}
		JSONObject message = new JSONObject();
		message.put("role", geminiRole);
		message.put("content", content);
		messages.put(message);
		return this;
	}

	/**
	 * 设置 thinking - 默认实现，子类可以重写
	 */
	@Override
	public IRequestData thinking(boolean thinking) {
		return this;
	}

	/**
	 * 是否启用流式输出 - 默认实现，子类可以重写
	 */
	@Override
	public IRequestData stream(boolean stream) {
		return this;
	}

	/**
	 * 构建最终的请求 JSON 对象
	 * 
	 * @return 返回构建好的 JSON 对象
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject();
		JSONArray contents = new JSONArray();

		for (int i = 0; i < this.messages.length(); i++) {
			JSONObject message = this.messages.getJSONObject(i);
			String role = message.getString("role");
			String content = message.getString("content");

			// Gemini API 使用 "model" 而不是 "assistant"
			if ("assistant".equals(role)) {
				role = "model";
			}

			JSONObject contentItem = new JSONObject();
			contentItem.put("role", role);

			JSONArray parts = new JSONArray();
			JSONObject part = new JSONObject();
			part.put("text", content);
			parts.put(part);

			contentItem.put("parts", parts);
			contents.put(contentItem);
		}

		requestData.put("contents", contents);

		// an add parameters, for example:
		// "generationConfig": {
		// "temperature": 1,
		// "topK": 0,
		// "topP": 0.95,
		// "maxOutputTokens": 8192,
		// "stopSequences": []
		// },
		if (this.parameters.length() > 0) {
			requestData.put("generationConfig", this.parameters);
		}

		return requestData;
	}
}
