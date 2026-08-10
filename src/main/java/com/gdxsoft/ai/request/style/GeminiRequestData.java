package com.gdxsoft.ai.request.style;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.*;

/**
 * Google Gemini GenerateContent API 风格请求数据抽象基类。
 * <p>
 * 角色映射：system→user, assistant→model, tool→function。
 * 参数放入 generationConfig。
 *
 * @since 1.1.0
 */
public abstract class GeminiRequestData extends RequestDataBase {

	/**
	 * Gemini 支持 {@code thinkingConfig} 的模型名称关键字（忽略大小写）。
	 * <ul>
	 *   <li>{@code gemini-2.5-pro} / {@code gemini-2.5-flash} / {@code gemini-2.5-flash-lite}</li>
	 * </ul>
	 * 2.0 / 1.5 / 1.0 系列忽略 {@code thinkingBudget}。
	 */
	private static final String[] THINKING_MODEL_KEYWORDS = {
			"gemini-2.5"
	};

	protected JSONArray tools;

	protected GeminiRequestData(String defaultModel) {
		super(defaultModel);
	}

	@Override
	public IRequestData addMessage(String content, String role) {
		String geminiRole = mapRole(role);
		JSONObject message = new JSONObject();
		message.put("role", geminiRole);
		message.put("content", content);
		messages.put(message);
		return this;
	}

	protected String mapRole(String role) {
		if ("system".equals(role)) return "user";
		if ("assistant".equals(role)) return "model";
		if ("tool".equals(role)) return "function";
		return role;
	}

	@Override
	public IRequestData tools(AiTool... aiTools) {
		if (aiTools != null && aiTools.length > 0) {
			this.tools = AiTool.toGeminiArray(aiTools);
		}
		return this;
	}

	/**
	 * 设置 Gemini 思考预算。
	 * <p>
	 * 当 {@code budgetToken > 0} 且 model 名匹配 {@link #THINKING_MODEL_KEYWORDS} 时，
	 * 在 {@code generationConfig.thinkingConfig} 中写入
	 * {@code {"thinkingBudget": N, "includeThoughts": true}}。
	 * 当 model 不支持时仅记录缓存值、不写入请求体（避免 400 错误）。
	 * 传入 &lt;= 0 视为清除预算（清空缓存并从 generationConfig 中删除 thinkingConfig 子对象）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			// 主动从 parameters 中删除 thinkingConfig
			if (parameters.has("thinkingConfig")) {
				parameters.remove("thinkingConfig");
			}
			return this;
		}
		super.thinkingBudget(budgetToken);
		if (!isThinkingModel(this.model)) {
			// 模型不支持：缓存值保留但不写入请求体
			return this;
		}
		JSONObject thinkingConfig = new JSONObject();
		thinkingConfig.put("thinkingBudget", budgetToken);
		thinkingConfig.put("includeThoughts", true);
		parameters.put("thinkingConfig", thinkingConfig);
		return this;
	}

	/**
	 * 判断给定 model 名是否支持 Gemini 思考预算（关键词子串匹配，忽略大小写）。
	 *
	 * @param modelName 模型名（可为 null）
	 * @return 支持返回 true
	 */
	public static boolean isThinkingModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String kw : THINKING_MODEL_KEYWORDS) {
			if (lower.contains(kw)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public IRequestData addToolResult(String toolCallId, String content) {
		JSONObject contentItem = new JSONObject();
		contentItem.put("role", "function");
		JSONArray parts = new JSONArray();
		JSONObject functionResponse = new JSONObject();
		functionResponse.put("name", toolCallId);
		JSONObject response = new JSONObject();
		response.put("content", content);
		functionResponse.put("response", response);
		JSONObject part = new JSONObject();
		part.put("functionResponse", functionResponse);
		parts.put(part);
		contentItem.put("parts", parts);
		messages.put(contentItem);
		return this;
	}

	@Override
	public IRequestData addUserMultiPart(AiContent... contents) {
		JSONObject contentItem = new JSONObject();
		contentItem.put("role", "user");
		JSONArray parts = new JSONArray();
		for (AiContent c : contents) {
			parts.put(toGeminiPart(c));
		}
		contentItem.put("parts", parts);
		messages.put(contentItem);
		return this;
	}

	protected JSONObject toGeminiPart(AiContent content) {
		JSONObject part = new JSONObject();
		switch (content.getType()) {
			case TEXT:
				part.put("text", ((AiTextContent) content).getText());
				break;
			case IMAGE:
				AiImageContent img = (AiImageContent) content;
				if (img.isUrlMode()) {
					JSONObject fileData = new JSONObject();
					fileData.put("fileUri", img.getUrl());
					if (img.getMimeType() != null) fileData.put("mimeType", img.getMimeType());
					part.put("fileData", fileData);
				} else {
					JSONObject inlineData = new JSONObject();
					inlineData.put("mimeType", img.getMimeType());
					inlineData.put("data", img.getBase64Data());
					part.put("inlineData", inlineData);
				}
				break;
			case AUDIO:
				AiAudioContent audio = (AiAudioContent) content;
				if (audio.isUrlMode()) {
					JSONObject fileData = new JSONObject();
					fileData.put("fileUri", audio.getUrl());
					fileData.put("mimeType", audio.getMimeType() != null ? audio.getMimeType() : "audio/mpeg");
					part.put("fileData", fileData);
				} else {
					JSONObject inlineData = new JSONObject();
					inlineData.put("mimeType", audio.getMimeType());
					inlineData.put("data", audio.getBase64Data());
					part.put("inlineData", inlineData);
				}
				break;
			case VIDEO:
				AiVideoContent video = (AiVideoContent) content;
				if (video.isUrlMode()) {
					JSONObject fileData = new JSONObject();
					fileData.put("fileUri", video.getUrl());
					fileData.put("mimeType", "video/mp4");
					part.put("fileData", fileData);
				} else {
					JSONObject inlineData = new JSONObject();
					inlineData.put("mimeType", "video/mp4");
					inlineData.put("data", video.getBase64Data());
					part.put("inlineData", inlineData);
				}
				break;
			case TOOL_RESULT:
				AiToolResult tr = (AiToolResult) content;
				JSONObject functionResponse = new JSONObject();
				functionResponse.put("name", tr.getToolCallId());
				JSONObject response = new JSONObject();
				response.put("content", tr.getContent());
				functionResponse.put("response", response);
				part.put("functionResponse", functionResponse);
				break;
			default:
				part.put("text", content.toString());
		}
		return part;
	}

	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject();
		JSONArray contents = new JSONArray();
		for (int i = 0; i < this.messages.length(); i++) {
			JSONObject message = this.messages.getJSONObject(i);
			String role = message.getString("role");
			String content = message.getString("content");
			if ("assistant".equals(role)) role = "model";
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
		if (this.parameters.length() > 0) {
			JSONObject clone = new JSONObject(this.parameters.toString());
			clone.remove("stream");
			clone.remove("thinking");
			clone.remove("stream_options");
			clone.remove("response_format");
			requestData.put("generationConfig", clone);
		}
		if (tools != null && tools.length() > 0) {
			requestData.put("tools", tools);
		}
		return requestData;
	}
}
