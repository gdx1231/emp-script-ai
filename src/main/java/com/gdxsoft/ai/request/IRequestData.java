package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI 提供商请求数据接口 用于构建发送到不同AI提供商API的请求体
 */
public interface IRequestData {

	JSONObject getParameters();

	boolean isStream();

	String getModel();

	JSONArray getMessages();

	ProviderType getProviderType();

	/**
	 * 获取 AI 提供商类型
	 */
	String getProviderName();

	/**
	 * 设置模型名称
	 */
	IRequestData model(String model);

	/**
	 * 设置模型是否为深度思考
	 */
	IRequestData thinking(boolean thinking);

	/**
	 * 添加消息
	 */
	IRequestData addMessage(String content, String role);

	/**
	 * 添加用户消息
	 */
	IRequestData userMessage(String content);

	/**
	 * 添加助手消息
	 */
	IRequestData assistantMessage(String content);

	/**
	 * 添加系统消息
	 */
	IRequestData systemMessage(String content);

	/**
	 * 设置 temperature
	 */
	IRequestData temperature(double temp);

	/**
	 * 设置 top_p
	 */
	IRequestData topP(double topP);

	/**
	 * 是否启用流式输出
	 */
	IRequestData stream(boolean stream);

	/**
	 * 构建最终的请求 JSON 对象
	 */
	JSONObject build();

	/**
	 * 构建并返回字符串形式
	 */
	String buildJson();
	/**
     * 设置响应格式。返回内容的格式。可选值：{"type": "text"}或{"type": "json_object"}。<br>
     * 例如，设置为 json_object 时，返回结果将是一个 JSON 对象，而不是纯文本。<br>
     * 适用于需要结构化数据的场景，如请求返回一个包含特定字段的 JSON 对象。<br>
     * 示例:
     * {<br>
     * "model": "gpt-4o",<br>
     * "messages": [<br>
     * {<br>
     * "role": "user",<br>
     * "content": "请返回一个包含用户名和年龄的 JSON 对象"<br>
     * }<br>
     * ],<br>
     * "<b>response_format</b>": {<br>
     * "type": "<b>json_object</b>"<br>
     * }<br>
     * }
     */
	IRequestData responseFormat(String format);

	String getResponseFormat();

	// ==================== 以下方法为 1.1.0 新增，使用 default 实现，不破坏现有代码 ====================

	/**
	 * 设置工具列表。AI 模型可以根据需要调用这些工具。
	 * <p>
	 * 默认实现不做任何处理（保持向后兼容）。子类应覆写此方法以支持 tool calling。
	 *
	 * @param tools 工具定义数组
	 * @return this
	 */
	default IRequestData tools(AiTool... tools) {
		// 默认空实现，子类覆写以支持 tool calling
		return this;
	}

	/**
	 * 设置工具选择模式。
	 * <ul>
	 *   <li>{@code "auto"} — AI 自动决定是否调用工具（默认）</li>
	 *   <li>{@code "required"} — AI 必须调用至少一个工具</li>
	 *   <li>{@code "none"} — AI 不调用任何工具</li>
	 * </ul>
	 * <p>
	 * 默认实现不做任何处理。子类应覆写此方法。
	 *
	 * @param toolChoice 工具选择模式
	 * @return this
	 */
	default IRequestData toolChoice(String toolChoice) {
		// 默认空实现
		return this;
	}

	/**
	 * 添加工具调用结果消息（tool 角色）。
	 * <p>
	 * 当 AI 模型请求调用工具后，将工具执行结果回传给模型。
	 * 默认实现将内容作为普通 user 消息添加（降级兼容）。
	 * 子类应覆写此方法以正确处理 tool 角色。
	 *
	 * @param toolCallId 工具调用 ID（对应 AI 返回的 tool_call id）
	 * @param content    工具执行结果
	 * @return this
	 */
	default IRequestData addToolResult(String toolCallId, String content) {
		// 默认降级实现：作为普通消息添加
		return this.addMessage(content, "user");
	}

	/**
	 * 添加用户多部分消息（文本 + 图片/音频/视频等）。
	 * <p>
	 * 默认实现将所有内容合并为纯文本（降级兼容）。
	 * 子类应覆写此方法以支持真正的多模态消息。
	 *
	 * @param contents 内容片段数组
	 * @return this
	 */
	default IRequestData addUserMultiPart(AiContent... contents) {
		// 默认降级实现：合并为纯文本
		StringBuilder sb = new StringBuilder();
		for (AiContent c : contents) {
			if (c instanceof AiTextContent) {
				sb.append(((AiTextContent) c).getText());
			} else if (c instanceof AiImageContent) {
				AiImageContent img = (AiImageContent) c;
				if (img.isUrlMode()) {
					sb.append("[图片: ").append(img.getUrl()).append("]");
				} else {
					sb.append("[图片: base64 ").append(img.getMimeType()).append("]");
				}
			} else if (c instanceof AiAudioContent) {
				AiAudioContent audio = (AiAudioContent) c;
				if (audio.isUrlMode()) {
					sb.append("[音频: ").append(audio.getUrl()).append("]");
				} else {
					sb.append("[音频: base64 ").append(audio.getMimeType()).append("]");
				}
			} else if (c instanceof AiVideoContent) {
				AiVideoContent video = (AiVideoContent) c;
				if (video.isUrlMode()) {
					sb.append("[视频: ").append(video.getUrl()).append("]");
				} else {
					sb.append("[视频: base64]");
				}
			} else if (c instanceof AiToolResult) {
				AiToolResult tr = (AiToolResult) c;
				sb.append("[工具结果: ").append(tr.getToolCallId()).append("] ").append(tr.getContent());
			} else {
				sb.append(c.toString());
			}
		}
		return this.addMessage(sb.toString(), "user");
	}
}
