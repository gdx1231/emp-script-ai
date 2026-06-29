package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Responses API SSE → OpenAI Chat Completions SSE 响应转换。
 * <p>
 * 有状态：每个请求创建新实例。
 */
public class ResponsesToChat {

	private String responseId;
	private int toolCallIndex = 0;
	private boolean sentRole = false;

	/**
	 * 转换单行 Responses API SSE 为 OpenAI Chat Completions SSE 格式。
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
				case "response.created":
					return handleResponseCreated(json);
				case "response.output_text.delta":
					return handleOutputTextDelta(json);
				case "response.output_text.done":
					return null;
				case "response.function_call_arguments.delta":
					return handleFunctionCallArgsDelta(json);
				case "response.function_call_arguments.done":
					return handleFunctionCallArgsDone(json);
				case "response.completed":
					return handleResponseCompleted(json);
				default:
					return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	private String handleResponseCreated(JSONObject json) {
		JSONObject response = json.optJSONObject("response");
		if (response != null) {
			this.responseId = response.optString("id", null);
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

	private String handleOutputTextDelta(JSONObject json) {
		String delta = json.optString("delta", "");
		if (delta.isEmpty()) {
			return null;
		}

		JSONObject chunk = buildBaseChunk();
		JSONObject deltaObj = new JSONObject();
		deltaObj.put("content", delta);
		chunk.getJSONArray("choices").getJSONObject(0).put("delta", deltaObj);
		return "data: " + chunk.toString();
	}

	private String handleFunctionCallArgsDelta(JSONObject json) {
		String delta = json.optString("delta", "");
		if (delta.isEmpty()) {
			return null;
		}

		JSONObject chunk = buildBaseChunk();
		JSONObject deltaObj = new JSONObject();
		JSONArray toolCalls = new JSONArray();
		JSONObject tc = new JSONObject();
		tc.put("index", toolCallIndex);
		JSONObject fn = new JSONObject();
		fn.put("arguments", delta);
		tc.put("function", fn);
		toolCalls.put(tc);
		deltaObj.put("tool_calls", toolCalls);
		chunk.getJSONArray("choices").getJSONObject(0).put("delta", deltaObj);
		return "data: " + chunk.toString();
	}

	private String handleFunctionCallArgsDone(JSONObject json) {
		// function call arguments 完成，递增 index
		toolCallIndex++;
		return null;
	}

	private String handleResponseCompleted(JSONObject json) {
		JSONObject response = json.optJSONObject("response");
		if (response != null) {
			if (this.responseId == null) {
				this.responseId = response.optString("id", null);
			}
		}

		// 发送 finish_reason
		JSONObject chunk = buildBaseChunk();
		JSONObject delta = new JSONObject();
		delta.put("content", "");
		chunk.getJSONArray("choices").getJSONObject(0).put("delta", delta);
		chunk.getJSONArray("choices").getJSONObject(0).put("finish_reason", "stop");

		// usage
		if (response != null) {
			JSONObject usage = response.optJSONObject("usage");
			if (usage != null) {
				JSONObject usageObj = new JSONObject();
				usageObj.put("prompt_tokens", usage.optInt("input_tokens", 0));
				usageObj.put("completion_tokens", usage.optInt("output_tokens", 0));
				usageObj.put("total_tokens", usage.optInt("total_tokens", 0));
				chunk.put("usage", usageObj);
			}

			// 检查 output 中是否有 function_call（finish_reason 应为 tool_calls）
			JSONArray output = response.optJSONArray("output");
			if (output != null) {
				for (int i = 0; i < output.length(); i++) {
					JSONObject item = output.getJSONObject(i);
					if ("function_call".equals(item.optString("type"))) {
						chunk.getJSONArray("choices").getJSONObject(0).put("finish_reason", "tool_calls");
						break;
					}
				}
			}
		}

		return "data: " + chunk.toString() + "\n\ndata: [DONE]";
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
			if ("response.output_text.delta".equals(json.optString("type"))) {
				return json.optString("delta", null);
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}
}
