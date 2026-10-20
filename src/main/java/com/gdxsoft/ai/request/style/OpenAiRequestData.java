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

	/**
	 * 启用/关闭联网搜索，写入 OpenAI 兼容的顶层字段 {@code enable_search}。
	 * <p>
	 * 各服务商支持情况由服务端决定：Qwen（DashScope）支持该字段，
	 * 并提供 {@code search_options} 扩展（见 Qwen RequestData）；
	 * 不支持的 provider 由服务端忽略或拒绝。
	 */
	@Override
	public IRequestData enableSearch(boolean enable) {
		if (enable) {
			parameters.put("enable_search", true);
		} else {
			parameters.remove("enable_search");
		}
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
				// Qwen 兼容协议要求 data 字段；Base64 使用 Data URL。
				if (audio.isUrlMode()) {
					audioData.put("data", audio.getUrl());
				} else {
					String mime = audio.getMimeType() != null ? audio.getMimeType() : "audio/wav";
					audioData.put("data", "data:" + mime + ";base64," + audio.getBase64Data());
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
			case FILE:
				AiFileContent file = (AiFileContent) content;
				part.put("type", "file");
				JSONObject fileData = new JSONObject();
				if (file.isUrlMode()) {
					// OpenAI 兼容生态多数实现使用 file_data 传入 data URL 或外部文件 URL。
					fileData.put("file_data", file.getUrl());
				} else {
					String mimeType = file.getMimeType() != null ? file.getMimeType() : "application/octet-stream";
					fileData.put("file_data", "data:" + mimeType + ";base64," + file.getBase64Data());
				}
				String filename = file.getFilename();
				if (filename == null && file.isUrlMode()) {
					filename = fileNameFromUrl(file.getUrl());
				}
				if (filename != null) {
					fileData.put("filename", filename);
				}
				part.put("file", fileData);
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

	/**
	 * 从 URL 中提取文件名；无法提取时返回 null。
	 */
	protected String fileNameFromUrl(String url) {
		if (url == null || url.startsWith("data:")) {
			return null;
		}
		int q = url.indexOf('?');
		String path = q >= 0 ? url.substring(0, q) : url;
		int hash = path.indexOf('#');
		if (hash >= 0) {
			path = path.substring(0, hash);
		}
		int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		return path.isEmpty() ? null : path;
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
