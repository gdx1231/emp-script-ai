package com.gdxsoft.ai.request.style;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.*;

/**
 * OpenAI Chat Completions 风格请求数据抽象基类。
 * <p>
 * 适用于所有使用 OpenAI 兼容格式的请求体：
 * <ul>
 *   <li>请求体：{@code {"model":"...","messages":[...],...}}</li>
 *   <li>工具：tools[] + tool_choice</li>
 *   <li>多模态：content 数组</li>
 * </ul>
 *
 * @since 1.1.0
 */
public abstract class OpenAiRequestData extends RequestDataBase {

	protected JSONArray tools;
	protected String toolChoice;

	protected OpenAiRequestData(String defaultModel) {
		super(defaultModel);
	}

	@Override
	public IRequestData tools(AiTool... aiTools) {
		if (aiTools != null && aiTools.length > 0) {
			this.tools = AiTool.toOpenAiArray(aiTools);
		}
		return this;
	}

	@Override
	public IRequestData toolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
		return this;
	}

	@Override
	public IRequestData addToolResult(String toolCallId, String content) {
		JSONObject message = new JSONObject();
		message.put("role", "tool");
		message.put("tool_call_id", toolCallId);
		message.put("content", content);
		messages.put(message);
		return this;
	}

	@Override
	public IRequestData addUserMultiPart(AiContent... contents) {
		JSONObject message = new JSONObject();
		message.put("role", "user");
		if (contents.length == 1 && contents[0] instanceof AiTextContent) {
			message.put("content", ((AiTextContent) contents[0]).getText());
		} else {
			JSONArray contentArr = new JSONArray();
			for (AiContent c : contents) {
				contentArr.put(toOpenAiContentPart(c));
			}
			message.put("content", contentArr);
		}
		messages.put(message);
		return this;
	}

	protected JSONObject toOpenAiContentPart(AiContent content) {
		JSONObject part = new JSONObject();
		switch (content.getType()) {
			case TEXT:
				part.put("type", "text");
				part.put("text", ((AiTextContent) content).getText());
				break;
			case IMAGE:
				AiImageContent img = (AiImageContent) content;
				part.put("type", "image_url");
				JSONObject imageUrl = new JSONObject();
				if (img.isUrlMode()) {
					imageUrl.put("url", img.getUrl());
				} else {
					imageUrl.put("url", "data:" + img.getMimeType() + ";base64," + img.getBase64Data());
				}
				part.put("image_url", imageUrl);
				break;
			case AUDIO:
				AiAudioContent audio = (AiAudioContent) content;
				part.put("type", "input_audio");
				JSONObject audioData = new JSONObject();
				if (audio.isUrlMode()) {
					audioData.put("url", audio.getUrl());
				} else {
					audioData.put("data", audio.getBase64Data());
					audioData.put("format", audio.getMimeType() != null ? audio.getMimeType().split("/")[1] : "wav");
				}
				part.put("input_audio", audioData);
				break;
			case VIDEO:
				AiVideoContent video = (AiVideoContent) content;
				part.put("type", "video_url");
				JSONObject videoUrl = new JSONObject();
				if (video.isUrlMode()) {
					videoUrl.put("url", video.getUrl());
				} else {
					videoUrl.put("url", "data:video/mp4;base64," + video.getBase64Data());
				}
				part.put("video_url", videoUrl);
				break;
			case TOOL_RESULT:
				AiToolResult tr = (AiToolResult) content;
				part.put("type", "text");
				part.put("text", "[Tool Result: " + tr.getToolCallId() + "] " + tr.getContent());
				break;
			default:
				part.put("type", "text");
				part.put("text", content.toString());
		}
		return part;
	}

	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		if (tools != null && tools.length() > 0) {
			requestData.put("tools", tools);
			if (toolChoice != null) {
				requestData.put("tool_choice", toolChoice);
			}
		}
		return requestData;
	}
}
