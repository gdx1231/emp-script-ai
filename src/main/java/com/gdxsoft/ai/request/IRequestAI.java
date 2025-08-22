package com.gdxsoft.ai.request;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;

import org.json.JSONObject;

public interface IRequestAI {
	/**
	 * 转成Curl 命令行格式。| Convert to Curl command line format.
	 * @return Curl 命令行字符串 | the Curl command line string
	 */
	String curl(IRequestData reqData) ;
	
	/**
	 * 提取行文本中的 JSON 对象（不同厂商格式不同，由子类实现）。
	 * 
	 * @param jsonText       JSON文本 | JSON text
	 * @param skipDataPrefix 如果为 true，则跳过以 "data:" 开头的前缀 | If true, skip the "data:"
	 *                       prefix
	 * @return 标准化的 JSON 对象 | normalized JSON object
	 */
	JSONObject extraceJson(String jsonText, boolean skipDataPrefix);

	/**
	 * 提取行文本中的 JSON 对象（不同厂商格式不同，由子类实现）。
	 * <p>
	 * Extract the JSON object from a text line (provider-specific; implemented by
	 * subclasses).
	 *
	 * @param line 一行文本（SSE 或普通响应）| a single response line (SSE or plain)
	 * @return 标准化的 JSON 对象 | normalized JSON object
	 */
	JSONObject extraceJson(String line);

	/**
	 * 创建请求的 URL。主要解决GEMINI 的URL不规范问题。| Create the request URL, mainly to handle the
	 * non-standard URLs of GEMINI.
	 * 
	 * @param reqData 请求数据 | Request data
	 * @return 请求的完整 URL | Complete request URL
	 */
	String createUrl(IRequestData reqData);

	/**
	 * 取消正在执行的请求。
	 * <p>
	 * Cancel the ongoing request if any.
	 */
	void cancelRequest();

	/**
	 * 获取 AI 提供商类型| Get the AI provider type.
	 * 
	 * @return AI 提供商类型 | AI provider type
	 */
	ProviderType getProviderType();

	/**
	 * 获取 AI 提供商类型名称| Get the AI provider name.
	 * 
	 * @return AI 提供商名称 | AI provider name
	 */
	String getProviderName();

	/**
	 * 初始化 API URL 和 API Key| Initialize the API URL and API Key.
	 * 
	 * @param apiUrl API的URL地址 | The URL of the API
	 * @param apiKey API的访问密钥 | The access key for the API
	 */
	void initUrlAndKey(String apiUrl, String apiKey);

	/**
	 * 调用非流式API| Call the non-streaming API.
	 * 
	 * @param reqData 用户输入的提示词 | User input prompt data
	 * @return 完整的响应文本 | Complete response text
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	String doPost(IRequestData reqData) throws IOException, URISyntaxException, InterruptedException;

	/**
	 * 调用流式API| Call the streaming API.
	 * 
	 * @param reqData 用户输入的提示词| User input prompt data
	 * @param writer  输出流| Output stream
	 * @return 完整的响应文本| Complete response text
	 * @throws IOException        IO异常
	 * @throws URISyntaxException
	 */
	String doStream(IRequestData reqData, PrintWriter writer)
			throws IOException, URISyntaxException, InterruptedException;

	/**
	 * 获取输出事件接口| Get the output events interface.
	 * 
	 * @return
	 */
	IOutEvents getOutEvents();

	/**
	 * 设置输出事件接口| Set the output events interface.
	 * 
	 * @param outEvents 输出事件接口 | Output events interface
	 */
	void setOutEvents(IOutEvents outEvents);

	/**
	 * 获取API的URL地址| Get the API URL.
	 * 
	 * @return API的URL地址 | The URL of the API
	 */
	String getApiUrl();

	/**
	 * 设置API的URL地址| Set the API URL.
	 * 
	 * @param apiUrl API的URL地址 | The URL of the API
	 */
	void setApiUrl(String apiUrl);

	/**
	 * 获取API访问密钥| Get the API access key.
	 * 
	 * @return API访问密钥 | The API access key
	 */
	String getApiKey();

	/**
	 * 设置API访问密钥| Set the API access key.
	 * 
	 * @param apiKey API访问密钥 | The API access key
	 */
	void setApiKey(String apiKey);
}