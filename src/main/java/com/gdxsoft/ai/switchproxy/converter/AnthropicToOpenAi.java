package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Anthropic Messages API SSE → OpenAI Chat Completions SSE 响应转换。
 * <p>
 * 有状态：每个请求创建新实例，维护 tool_calls 缓冲。
 */
public class AnthropicToOpenAi {

	private String responseId;
	private int toolCallIndex = 0;
	private StringBuilder currentToolArgs = new StringBuilder();
	private boolean sentRole = false;

	/**
	 * 转换单行 Anthropic SSE 为 OpenAI SSE 格式。
	 *
	 * @return 转换后的 SSE 行（含 data: 前缀），null 表示跳过
	 */
	public String convertSseLine(String rawLine) {
		if (rawLine == null || !rawLine.startsWith("data:")) {
			return null;
		}
		String data = rawLine.substring(5).trim();
		if ("[DONE]".equals(data)) {
			return "data: [DONE]";
		}
		if (data.isEmpty()) {
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
					return handleMessageStop();
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
			this.responseId = message.optString("id", null);
		}

		if (!sentRole) {
			sentRole = true;
			JSONObject chunk = buildBaseChunk();
			JSONObject delta = new JSONObject();
			delta.put("role", "assistant");
			delta.put("content", "");
			chunk.getJSONArray("choices").getJSONObject(0).put("delta", delta);
			return "data: " + chunk.toString();
		}
		return null;
	}

	private String handleContentBlockStart(JSONObject json) {
		JSONObject contentBlock = json.optJSONObject("content_block");
		if (contentBlock == null) {
			return null;
		}

		String cbType = contentBlock.optString("type", "");

		if ("tool_use".equals(cbType)) {
			String id = contentBlock.optString("id", "call_" + toolCallIndex);
			String name = contentBlock.optString("name", "");
			currentToolArgs = new StringBuilder();

			JSONObject chunk = buildBaseChunk();
			JSONObject delta = new JSONObject();
			JSONArray toolCalls = new JSONArray();
			JSONObject tc = new JSONObject();
			tc.put("index", toolCallIndex);
			tc.put("id", id);
			tc.put("type", "function");
			JSONObject fn = new JSONObject();
			fn.put("name", name);
			fn.put("arguments", "");
			tc.put("function", fn);
			toolCalls.put(tc);
			delta.put("tool_calls", toolCalls);
			chunk.getJSONArray("choices").getJSONObject(0).put("delta", delta);
			return "data: " + chunk.toString();
		}

		// thinking block
		if ("thinking".equals(cbType)) {
			// 思考内容通过 reasoning_content 字段传递
			return null;
		}

		return null;
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

			JSONObject chunk = buildBaseChunk();
			JSONObject deltaObj = new JSONObject();
			deltaObj.put("content", text);
			chunk.getJSONArray("choices").getJSONObject(0).put("delta", deltaObj);
			return "data: " + chunk.toString();
		}

		if ("thinking_delta".equals(deltaType)) {
			String text = delta.optString("thinking", "");
			if (text.isEmpty()) {
				return null;
			}

			JSONObject chunk = buildBaseChunk();
			JSONObject deltaObj = new JSONObject();
			deltaObj.put("reasoning_content", text);
			chunk.getJSONArray("choices").getJSONObject(0).put("delta", deltaObj);
			return "data: " + chunk.toString();
		}

		if ("input_json_delta".equals(deltaType)) {
			String partial = delta.optString("partial_json", "");
			if (!partial.isEmpty()) {
				currentToolArgs.append(partial);

				JSONObject chunk = buildBaseChunk();
				JSONObject deltaObj = new JSONObject();
				JSONArray toolCalls = new JSONArray();
				JSONObject tc = new JSONObject();
				tc.put("index", toolCallIndex);
				JSONObject fn = new JSONObject();
				fn.put("arguments", partial);
				tc.put("function", fn);
				toolCalls.put(tc);
				deltaObj.put("tool_calls", toolCalls);
				chunk.getJSONArray("choices").getJSONObject(0).put("delta", deltaObj);
				return "data: " + chunk.toString();
			}
		}

		return null;
	}

	private String handleContentBlockStop(JSONObject json) {
		// content block 结束，如果是 tool_use 则递增 index
		int index = json.optInt("index", -1);
		if (index >= 0) {
			toolCallIndex = index + 1;
		}
		return null;
	}

	private String handleMessageDelta(JSONObject json) {
		JSONObject chunk = buildBaseChunk();
		JSONObject delta = new JSONObject();
		delta.put("content", "");

		// finish_reason
		JSONObject deltaObj = json.optJSONObject("delta");
		if (deltaObj != null) {
			String stopReason = deltaObj.optString("stop_reason", null);
			if (stopReason != null) {
				String finishReason = "end_turn".equals(stopReason) ? "stop"
						: "tool_use".equals(stopReason) ? "tool_calls" : stopReason;
				chunk.getJSONArray("choices").getJSONObject(0).put("finish_reason", finishReason);
			}
		}

		// usage
		JSONObject usage = json.optJSONObject("usage");
		if (usage != null) {
			JSONObject usageObj = new JSONObject();
			usageObj.put("completion_tokens", usage.optInt("output_tokens", 0));
			chunk.put("usage", usageObj);
		}

		chunk.getJSONArray("choices").getJSONObject(0).put("delta", delta);
		return "data: " + chunk.toString();
	}

	private String handleMessageStop() {
		return "data: [DONE]";
	}

	private JSONObject buildBaseChunk() {
		JSONObject chunk = new JSONObject();
		chunk.put("id", responseId != null ? responseId : "chatcmpl-proxy");
		chunk.put("object", "chat.completion.chunk");
		chunk.put("created", System.currentTimeMillis() / 1000);
		chunk.put("model", "");

		JSONArray choices = new JSONArray();
		JSONObject choice = new JSONObject();
		choice.put("index", 0);
		choice.put("finish_reason", JSONObject.NULL);
		choices.put(choice);
		chunk.put("choices", choices);

		return chunk;
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
