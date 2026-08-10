package com.gdxsoft.ai.request.style;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.*;

/**
 * Anthropic Messages API 风格请求数据抽象基类。
 * <p>
 * system 使用独立字段，必须指定 max_tokens。
 *
 * @since 1.1.0
 */
public abstract class AnthropicRequestData extends RequestDataBase {

	private static final int DEFAULT_MAX_TOKENS = 4096;
	/**
	 * Anthropic 扩展思考支持的模型名称关键字（忽略大小写）。
	 * <ul>
	 *   <li>Claude 4 系列（opus / sonnet / 1 Opus）</li>
	 *   <li>Claude 3.7 sonnet（命名实际为 {@code claude-3-7-sonnet-*}，故同时匹配连字符写法）</li>
	 * </ul>
	 * Claude 3.5 / 3 / Haiku / 老版本不接收 budget_tokens。
	 */
	private static final String[] EXTENDED_THINKING_MODEL_KEYWORDS = {
			"claude-4", "claude-3-7", "claude-3.7", "claude-opus-4", "claude-sonnet-4"
	};
	protected JSONArray tools;
	protected String toolChoice;

	protected AnthropicRequestData(String defaultModel) {
		super(defaultModel);
	}

	@Override
	public IRequestData systemMessage(String content) {
		parameters.put("system", content);
		return this;
	}

	@Override
	public IRequestData maxTokens(int maxTokens) {
		parameters.put("max_tokens", maxTokens);
		return this;
	}

	@Override
	public IRequestData thinking(boolean thinking) {
		return this; // Anthropic 不直接支持顶层 thinking 参数
	}

	/**
	 * 设置 Anthropic 扩展思考预算。
	 * <p>
	 * 当 {@code budgetToken > 0} 且 model 名包含 {@link #EXTENDED_THINKING_MODEL_KEYWORDS}
	 * 之一时，写入 {@code {"thinking": {"type": "enabled", "budget_tokens": N}}}。
	 * 否则视为不支持，原 thinking 字段保持不变。
	 * 传入 &lt;= 0 视为清除预算（但 Anthropic 协议无单独 "budget_tokens 关闭" 语义，
	 * 因此仅清空缓存的 thinkingBudget 字段，不主动发送 disabled）。
	 */
	@Override
	public IRequestData thinkingBudget(int budgetToken) {
		if (budgetToken <= 0) {
			super.thinkingBudget(0);
			return this;
		}
		if (!isExtendedThinkingModel(this.model)) {
			// 模型不支持，记录缓存值但不写入请求体（避免 400 错误）
			super.thinkingBudget(budgetToken);
			return this;
		}
		JSONObject thinkingObj = parameters.optJSONObject("thinking");
		if (thinkingObj == null) {
			thinkingObj = new JSONObject();
		}
		thinkingObj.put("type", "enabled");
		thinkingObj.put("budget_tokens", budgetToken);
		parameters.put("thinking", thinkingObj);
		super.thinkingBudget(budgetToken);
		return this;
	}

	/**
	 * 判断给定 model 名是否支持 Anthropic 扩展思考（关键词子串匹配，忽略大小写）。
	 *
	 * @param modelName 模型名（可为 null）
	 * @return 支持返回 true
	 */
	public static boolean isExtendedThinkingModel(String modelName) {
		if (modelName == null) {
			return false;
		}
		String lower = modelName.toLowerCase();
		for (String kw : EXTENDED_THINKING_MODEL_KEYWORDS) {
			if (lower.contains(kw)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public IRequestData tools(AiTool... aiTools) {
		if (aiTools != null && aiTools.length > 0) {
			this.tools = AiTool.toAnthropicArray(aiTools);
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
		JSONArray contentArr = new JSONArray();
		JSONObject toolResult = new JSONObject();
		toolResult.put("type", "tool_result");
		toolResult.put("tool_use_id", toolCallId);
		JSONArray resultContent = new JSONArray();
		JSONObject textPart = new JSONObject();
		textPart.put("type", "text");
		textPart.put("text", content);
		resultContent.put(textPart);
		toolResult.put("content", resultContent);
		contentArr.put(toolResult);
		JSONObject message = new JSONObject();
		message.put("role", "user");
		message.put("content", contentArr);
		messages.put(message);
		return this;
	}

	@Override
	public IRequestData addUserMultiPart(AiContent... contents) {
		JSONArray contentArr = new JSONArray();
		for (AiContent c : contents) {
			contentArr.put(toAnthropicContentPart(c));
		}
		JSONObject message = new JSONObject();
		message.put("role", "user");
		message.put("content", contentArr);
		messages.put(message);
		return this;
	}

	protected JSONObject toAnthropicContentPart(AiContent content) {
		JSONObject part = new JSONObject();
		switch (content.getType()) {
			case TEXT:
				part.put("type", "text");
				part.put("text", ((AiTextContent) content).getText());
				break;
			case IMAGE:
				AiImageContent img = (AiImageContent) content;
				part.put("type", "image");
				JSONObject source = new JSONObject();
				source.put("type", "base64");
				source.put("media_type", img.getMimeType() != null ? img.getMimeType() : "image/png");
				source.put("data", img.isUrlMode() ? img.getUrl() : img.getBase64Data());
				part.put("source", source);
				break;
			case TOOL_RESULT:
				AiToolResult tr = (AiToolResult) content;
				part.put("type", "tool_result");
				part.put("tool_use_id", tr.getToolCallId());
				JSONArray textArr = new JSONArray();
				JSONObject textPart = new JSONObject();
				textPart.put("type", "text");
				textPart.put("text", tr.getContent());
				textArr.put(textPart);
				part.put("content", textArr);
				break;
			case AUDIO:
				AiAudioContent audio = (AiAudioContent) content;
				part.put("type", "input_audio");
				JSONObject audioSource = new JSONObject();
				audioSource.put("type", "base64");
				audioSource.put("media_type", audio.getMimeType() != null ? audio.getMimeType() : "audio/wav");
				audioSource.put("data", audio.isUrlMode() ? audio.getUrl() : audio.getBase64Data());
				part.put("source", audioSource);
				break;
			case VIDEO:
				part.put("type", "text");
				part.put("text", "[多媒体: " + content.getType().getName() + " 不支持]");
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
		if (!requestData.has("max_tokens")) {
			requestData.put("max_tokens", DEFAULT_MAX_TOKENS);
		}
		if (tools != null && tools.length() > 0) {
			requestData.put("tools", tools);
			if (toolChoice != null) {
				requestData.put("tool_choice", parseToolChoice(toolChoice));
			}
		}
		return requestData;
	}

	protected JSONObject parseToolChoice(String tc) {
		JSONObject result = new JSONObject();
		switch (tc) {
			case "required": result.put("type", "any"); break;
			case "none": break;
			default:
				if (tc.startsWith("tool:")) {
					result.put("type", "tool");
					result.put("name", tc.substring(5));
				} else {
					result.put("type", "auto");
				}
		}
		return result;
	}
}
