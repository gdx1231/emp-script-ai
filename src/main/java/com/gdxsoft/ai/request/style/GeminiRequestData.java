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
