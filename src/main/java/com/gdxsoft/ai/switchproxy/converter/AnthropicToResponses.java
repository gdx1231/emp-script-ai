package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Anthropic Messages API SSE → Responses API SSE 响应转换。
 * <p>
 * 生成完整的 Responses API 事件序列，符合 Codex 客户端期望。
 */
public class AnthropicToResponses {

	private String responseId;
	private String messageId;
	private int outputIndex = 0;
	private int contentIndex = 0;
	private boolean sentCreated = false;
	private StringBuilder currentText = new StringBuilder();
	private int inputTokens = 0;
	private int outputTokens = 0;

	/**
	 * 转换单行 Anthropic SSE 为 Responses API SSE 格式。
	 *
	 * @return 转换后的 SSE 行（含 event: 和 data: 前缀），null 表示跳过
	 */
	public String convertSseLine(String rawLine) {
		if (rawLine == null) {
			return null;
		}

		// 跳过 event: 行和空行
		if (rawLine.startsWith("event:") || rawLine.trim().isEmpty()) {
			return null;
		}

		if (!rawLine.startsWith("data:")) {
			return null;
		}

		String data = rawLine.substring(5).trim();
		if ("[DONE]".equals(data) || data.isEmpty()) {
			return null;
		}

		try {
			JSONObject json = new JSONObject(data);
			String type = json.optString("type", "");

			switch (type) {
				case "message_start":
					return handleMessageStart(json);
				case "content_block_start":
					return handleContentBlockStart(json);
				case "content_block_delta":
					return handleContentBlockDelta(json);
				case "content_block_stop":
					return handleContentBlockStop(json);
				case "message_delta":
					return handleMessageDelta(json);
				case "message_stop":
					return handleMessageStop(json);
				default:
					return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private String handleMessageStart(JSONObject json) {
		JSONObject message = json.optJSONObject("message");
		if (message != null) {
			this.responseId = message.optString("id", "resp_" + System.currentTimeMillis());
			this.messageId = "msg_" + System.currentTimeMillis();

			// 提取 input tokens
			JSONObject usage = message.optJSONObject("usage");
			if (usage != null) {
				this.inputTokens = usage.optInt("input_tokens", 0);
			}
		}

		StringBuilder result = new StringBuilder();

		// 1. response.created
		if (!sentCreated) {
			sentCreated = true;
			result.append(formatEvent("response.created", createResponseEvent("created")));
		}

		// 2. response.in_progress
		result.append(formatEvent("response.in_progress", createResponseEvent("in_progress")));

		return result.toString();
	}

	private String handleContentBlockStart(JSONObject json) {
		JSONObject contentBlock = json.optJSONObject("content_block");
		if (contentBlock == null) {
			return null;
		}

		String cbType = contentBlock.optString("type", "");
		StringBuilder result = new StringBuilder();

		if ("text".equals(cbType)) {
			// 3. response.output_item.added
			JSONObject outputItemAdded = new JSONObject();
			outputItemAdded.put("type", "response.output_item.added");
			outputItemAdded.put("output_index", outputIndex);
			JSONObject item = new JSONObject();
			item.put("type", "message");
			item.put("id", messageId);
			item.put("uid", messageId);
			item.put("status", "in_progress");
			item.put("role", "assistant");
			item.put("content", new JSONArray());
			outputItemAdded.put("item", item);
			result.append(formatEvent("response.output_item.added", outputItemAdded));

			// 4. response.content_part.added
			JSONObject contentPartAdded = new JSONObject();
			contentPartAdded.put("type", "response.content_part.added");
			contentPartAdded.put("item_id", messageId);
			contentPartAdded.put("output_index", outputIndex);
			contentPartAdded.put("content_index", contentIndex);
			JSONObject part = new JSONObject();
			part.put("type", "output_text");
			part.put("text", "");
			part.put("annotations", new JSONArray());
			contentPartAdded.put("part", part);
			result.append(formatEvent("response.content_part.added", contentPartAdded));

			currentText = new StringBuilder();
		}

		return result.toString();
	}

	private String handleContentBlockDelta(JSONObject json) {
		JSONObject delta = json.optJSONObject("delta");
		if (delta == null) {
			return null;
		}

		String deltaType = delta.optString("type", "");

		if ("text_delta".equals(deltaType)) {
			String text = delta.optString("text", "");
			if (text.isEmpty()) {
				return null;
			}

			currentText.append(text);

			// 5. response.output_text.delta
			JSONObject textDelta = new JSONObject();
			textDelta.put("type", "response.output_text.delta");
			textDelta.put("item_id", messageId);
			textDelta.put("output_index", outputIndex);
			textDelta.put("content_index", contentIndex);
			textDelta.put("delta", text);
			return formatEvent("response.output_text.delta", textDelta);
		}

		return null;
	}

	private String handleContentBlockStop(JSONObject json) {
		int index = json.optInt("index", contentIndex);
		StringBuilder result = new StringBuilder();

		// 6. response.output_text.done
		JSONObject textDone = new JSONObject();
		textDone.put("type", "response.output_text.done");
		textDone.put("item_id", messageId);
		textDone.put("output_index", outputIndex);
		textDone.put("content_index", index);
		textDone.put("text", currentText.toString());
		result.append(formatEvent("response.output_text.done", textDone));

		// 7. response.content_part.done
		JSONObject contentPartDone = new JSONObject();
		contentPartDone.put("type", "response.content_part.done");
		contentPartDone.put("item_id", messageId);
		contentPartDone.put("output_index", outputIndex);
		contentPartDone.put("content_index", index);
		JSONObject part = new JSONObject();
		part.put("type", "output_text");
		part.put("text", currentText.toString());
		part.put("annotations", new JSONArray());
		contentPartDone.put("part", part);
		result.append(formatEvent("response.content_part.done", contentPartDone));

		if (index >= contentIndex) {
			contentIndex = index + 1;
		}

		return result.toString();
	}

	private String handleMessageDelta(JSONObject json) {
		// 提取 output tokens
		JSONObject usage = json.optJSONObject("usage");
		if (usage != null) {
			this.outputTokens = usage.optInt("output_tokens", 0);
		}
		return null;
	}

	private String handleMessageStop(JSONObject json) {
		StringBuilder result = new StringBuilder();

		// 8. response.output_item.done
		JSONObject outputItemDone = new JSONObject();
		outputItemDone.put("type", "response.output_item.done");
		outputItemDone.put("output_index", outputIndex);
		JSONObject item = new JSONObject();
		item.put("type", "message");
		item.put("id", messageId);
		item.put("uid", messageId);
		item.put("status", "completed");
		item.put("role", "assistant");
		JSONArray content = new JSONArray();
		JSONObject textContent = new JSONObject();
		textContent.put("type", "output_text");
		textContent.put("text", currentText.toString());
		textContent.put("annotations", new JSONArray());
		content.put(textContent);
		item.put("content", content);
		outputItemDone.put("item", item);
		result.append(formatEvent("response.output_item.done", outputItemDone));

		// 9. response.completed
		JSONObject completedEvent = createResponseEvent("completed");
		JSONArray output = new JSONArray();
		JSONObject message = new JSONObject();
		message.put("type", "message");
		message.put("id", messageId);
		message.put("uid", messageId);
		message.put("status", "completed");
		message.put("role", "assistant");
		JSONArray msgContent = new JSONArray();
		JSONObject msgTextContent = new JSONObject();
		msgTextContent.put("type", "output_text");
		msgTextContent.put("text", currentText.toString());
		msgTextContent.put("annotations", new JSONArray());
		msgContent.put(msgTextContent);
		message.put("content", msgContent);
		output.put(message);
		completedEvent.getJSONObject("response").put("output", output);

		// 添加 usage
		JSONObject usage = new JSONObject();
		usage.put("input_tokens", inputTokens);
		usage.put("output_tokens", outputTokens);
		usage.put("total_tokens", inputTokens + outputTokens);
		completedEvent.getJSONObject("response").put("usage", usage);

		result.append(formatEvent("response.completed", completedEvent));

		outputIndex++;

		return result.toString();
	}

	private JSONObject createResponseEvent(String status) {
		JSONObject event = new JSONObject();
		event.put("type", "response." + status);

		JSONObject response = new JSONObject();
		response.put("id", responseId);
		response.put("object", "response");
		response.put("status", status);
		response.put("created_at", System.currentTimeMillis() / 1000);
		response.put("model", "deepseek-v4-pro");
		response.put("output", new JSONArray());
		response.put("error", JSONObject.NULL);
		response.put("incomplete_details", JSONObject.NULL);
		response.put("metadata", new JSONObject());

		JSONObject usage = new JSONObject();
		usage.put("input_tokens", inputTokens);
		usage.put("output_tokens", outputTokens);
		usage.put("total_tokens", inputTokens + outputTokens);
		response.put("usage", usage);

		event.put("response", response);
		return event;
	}

	private String formatEvent(String eventType, JSONObject data) {
		return "event: " + eventType + "\ndata: " + data.toString() + "\n\n";
	}

	/**
	 * 提取文本输出（用于日志）。
	 */
	public static String extractTextFromSseLine(String rawLine) {
		if (rawLine == null || !rawLine.startsWith("data:")) {
			return null;
		}
		String data = rawLine.substring(5).trim();
		if ("[DONE]".equals(data) || data.isEmpty()) {
			return null;
		}
		try {
			JSONObject json = new JSONObject(data);
			if ("content_block_delta".equals(json.optString("type"))) {
				JSONObject delta = json.optJSONObject("delta");
				if (delta != null && "text_delta".equals(delta.optString("type"))) {
					return delta.optString("text", null);
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	/**
	 * 提取思考内容（用于日志）。
	 */
	public static String extractThinkingFromSseLine(String rawLine) {
		if (rawLine == null || !rawLine.startsWith("data:")) {
			return null;
		}
		String data = rawLine.substring(5).trim();
		if ("[DONE]".equals(data) || data.isEmpty()) {
			return null;
		}
		try {
			JSONObject json = new JSONObject(data);
			if ("content_block_delta".equals(json.optString("type"))) {
				JSONObject delta = json.optJSONObject("delta");
				if (delta != null && "thinking_delta".equals(delta.optString("type"))) {
					return delta.optString("thinking", null);
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}
}
