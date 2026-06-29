package com.gdxsoft.ai.switchproxy.entry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 流式构建日志条目（append-only builder）。
 * <p>
 * 两阶段生命周期：
 * <ol>
 *   <li>阶段一（SSE 进行中）：appendOutput / appendThinking / appendRawSseLine</li>
 *   <li>阶段二（SSE 结束后）：finalize() 提取结构化字段</li>
 * </ol>
 */
public class RequestLogEntry {
	private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	// 一次性设置的字段
	private String id;
	private String timestamp;
	private String mode;
	private String target;
	private String model;
	private String input;
	private String convertedInput;
	private Map<String, String> headers = new LinkedHashMap<>();
	private long startTimeMs;

	// 阶段一：流式累积
	private StringBuilder outputBuffer = new StringBuilder();
	private StringBuilder thinkingBuffer = new StringBuilder();
	private List<String> rawSseLines = new ArrayList<>();

	// 阶段二：结构化提取
	private String finishReason;
	private String responseId;
	private List<ToolCallEntry> toolCalls = new ArrayList<>();
	private int promptTokens;
	private int completionTokens;
	private int totalTokens;

	public RequestLogEntry() {
		this.timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
		this.startTimeMs = System.currentTimeMillis();
	}

	// === 阶段一：流式累积 ===

	public void appendOutput(String chunk) {
		if (chunk != null && !chunk.isEmpty()) {
			outputBuffer.append(chunk);
		}
	}

	public void appendThinking(String chunk) {
		if (chunk != null && !chunk.isEmpty()) {
			thinkingBuffer.append(chunk);
		}
	}

	public void appendRawSseLine(String line) {
		if (line != null) {
			rawSseLines.add(line);
		}
	}

	// === 阶段二：结构化提取 ===

	/**
	 * 解析累积的 SSE 行，填充 finishReason / responseId / toolCalls / usage。
	 * 根据 mode 选择不同的解析策略。
	 */
	public void finalize(String upstreamFormat) {
		long endTimeMs = System.currentTimeMillis();
		this.durationMs = endTimeMs - startTimeMs;

		if ("anthropic".equals(upstreamFormat)) {
			finalizeAnthropic();
		} else if ("responses".equals(upstreamFormat)) {
			finalizeResponses();
		} else {
			finalizeOpenAi();
		}
	}

	private void finalizeOpenAi() {
		for (String line : rawSseLines) {
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if ("[DONE]".equals(data)) {
				break;
			}
			try {
				JSONObject json = new JSONObject(data);

				// 提取 response id（顶层 id 字段）
				String id = json.optString("id", null);
				if (id != null && !id.isEmpty()) {
					this.responseId = id;
				}

				JSONArray choices = json.optJSONArray("choices");
				if (choices != null && choices.length() > 0) {
					JSONObject choice = choices.getJSONObject(0);
					String reason = choice.optString("finish_reason", null);
					if (reason != null) {
						this.finishReason = reason;
					}
					JSONObject delta = choice.optJSONObject("delta");
					if (delta != null && delta.has("tool_calls")) {
						JSONArray tcArr = delta.getJSONArray("tool_calls");
						for (int i = 0; i < tcArr.length(); i++) {
							JSONObject tc = tcArr.getJSONObject(i);
							String tcId = tc.optString("id", null);
							JSONObject fn = tc.optJSONObject("function");
							if (fn != null) {
								String name = fn.optString("name", null);
								String args = fn.optString("arguments", null);
								if (name != null) {
									toolCalls.add(new ToolCallEntry(tcId, name, args != null ? args : ""));
								} else if (!toolCalls.isEmpty() && args != null) {
									toolCalls.get(toolCalls.size() - 1).appendArguments(args);
								}
							}
						}
					}
				}
				JSONObject usage = json.optJSONObject("usage");
				if (usage != null) {
					this.promptTokens = usage.optInt("prompt_tokens", 0);
					this.completionTokens = usage.optInt("completion_tokens", 0);
					this.totalTokens = usage.optInt("total_tokens", 0);
				}
			} catch (Exception e) {
				// 忽略解析失败的行
			}
		}
	}

	private void finalizeAnthropic() {
		for (String line : rawSseLines) {
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if ("[DONE]".equals(data)) {
				break;
			}
			try {
				JSONObject json = new JSONObject(data);
				String type = json.optString("type", "");

				if ("message_start".equals(type) && json.has("message")) {
					JSONObject msg = json.getJSONObject("message");
					this.responseId = msg.optString("id", null);
					JSONObject usage = msg.optJSONObject("usage");
					if (usage != null) {
						this.promptTokens = usage.optInt("input_tokens", 0);
					}
				}

				if ("message_delta".equals(type)) {
					JSONObject delta = json.optJSONObject("delta");
					if (delta != null) {
						this.finishReason = delta.optString("stop_reason", null);
					}
					JSONObject usage = json.optJSONObject("usage");
					if (usage != null) {
						this.completionTokens = usage.optInt("output_tokens", 0);
						this.totalTokens = this.promptTokens + this.completionTokens;
					}
				}

				if ("content_block_start".equals(type)) {
					JSONObject cb = json.optJSONObject("content_block");
					if (cb != null && "tool_use".equals(cb.optString("type"))) {
						String tcId = cb.optString("id", null);
						String name = cb.optString("name", null);
						toolCalls.add(new ToolCallEntry(tcId, name, ""));
					}
				}

				if ("content_block_delta".equals(type)) {
					JSONObject delta = json.optJSONObject("delta");
					if (delta != null && "input_json_delta".equals(delta.optString("type"))) {
						String partial = delta.optString("partial_json", null);
						if (partial != null && !toolCalls.isEmpty()) {
							toolCalls.get(toolCalls.size() - 1).appendArguments(partial);
						}
					}
				}
			} catch (Exception e) {
				// 忽略解析失败的行
			}
		}
	}

	private void finalizeResponses() {
		for (String line : rawSseLines) {
			if (!line.startsWith("data:")) {
				continue;
			}
			String data = line.substring(5).trim();
			if ("[DONE]".equals(data)) {
				break;
			}
			try {
				JSONObject json = new JSONObject(data);
				String type = json.optString("type", "");

				if ("response.created".equals(type) && json.has("response")) {
					JSONObject resp = json.getJSONObject("response");
					this.responseId = resp.optString("id", null);
				}

				if ("response.completed".equals(type) && json.has("response")) {
					JSONObject resp = json.getJSONObject("response");
					this.finishReason = "stop";

					JSONObject usage = resp.optJSONObject("usage");
					if (usage != null) {
						this.promptTokens = usage.optInt("input_tokens", 0);
						this.completionTokens = usage.optInt("output_tokens", 0);
						this.totalTokens = usage.optInt("total_tokens", this.promptTokens + this.completionTokens);
					}

					JSONArray output = resp.optJSONArray("output");
					if (output != null) {
						for (int i = 0; i < output.length(); i++) {
							JSONObject item = output.getJSONObject(i);
							if ("function_call".equals(item.optString("type"))) {
								toolCalls.add(new ToolCallEntry(
										item.optString("call_id", null),
										item.optString("name", null),
										item.optString("arguments", "")));
							}
						}
					}
				}
			} catch (Exception e) {
				// 忽略解析失败的行
			}
		}
	}

	// === 一次性设置的字段 ===

	public void setId(String id) {
		this.id = id;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public void setConvertedInput(String convertedInput) {
		this.convertedInput = convertedInput;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public void addHeader(String name, String value) {
		this.headers.put(name, value);
	}

	// === Getters ===

	public String getId() {
		return id;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public String getDatePath() {
		return LocalDateTime.now().format(DATE_FMT);
	}

	public String getMode() {
		return mode;
	}

	public String getTarget() {
		return target;
	}

	public String getModel() {
		return model;
	}

	public String getInput() {
		return input;
	}

	public String getConvertedInput() {
		return convertedInput;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getOutput() {
		return outputBuffer.toString();
	}

	public String getThinking() {
		return thinkingBuffer.toString();
	}

	public String getFinishReason() {
		return finishReason;
	}

	public String getResponseId() {
		return responseId;
	}

	public List<ToolCallEntry> getToolCalls() {
		return toolCalls;
	}

	public int getPromptTokens() {
		return promptTokens;
	}

	public int getCompletionTokens() {
		return completionTokens;
	}

	public int getTotalTokens() {
		return totalTokens;
	}

	public long getDurationMs() {
		return durationMs;
	}

	private long durationMs;

	/**
	 * 工具调用条目。
	 */
	public static class ToolCallEntry {
		private final String id;
		private final String name;
		private final StringBuilder arguments;

		public ToolCallEntry(String id, String name, String arguments) {
			this.id = id;
			this.name = name;
			this.arguments = new StringBuilder(arguments != null ? arguments : "");
		}

		public void appendArguments(String partial) {
			if (partial != null) {
				arguments.append(partial);
			}
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getArguments() {
			return arguments.toString();
		}
	}
}
