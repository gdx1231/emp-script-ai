package com.gdxsoft.ai.request;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * AI 提供商请求数据接口 用于构建发送到不同AI提供商API的请求体
 */
public interface IRequestData {

	public JSONObject getParameters();

	public boolean isStream();

	public String getModel();

	public JSONArray getMessages();

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
}
