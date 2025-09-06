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
}
