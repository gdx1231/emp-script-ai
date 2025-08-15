package com.gdxsoft.ai.providers;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;

public interface IRequestAI {
	ProviderType getProviderType();

	/**
	 * 获取 AI 提供商类型
	 */
	String getProviderName();

	/**
	 * 初始化 API URL 和 API Key
	 * 
	 * @param apiUrl
	 * @param apiKey
	 */
	void initUrlAndKey(String apiUrl, String apiKey);

	/**
	 * 调用非流式API
	 * @param reqData
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 */
	String doPost(IRequestData reqData) throws IOException, URISyntaxException, InterruptedException;
	/**
	 * 调用流式API
	 * 
	 * @param reqData 用户输入的提示词
	 * @param writer  输出流
	 * @return 完整的响应文本
	 * @throws IOException        IO异常
	 * @throws URISyntaxException
	 */
	String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException, InterruptedException;

	IOutEvents getOutEvents();

	void setOutEvents(IOutEvents outEvents);

	String getApiUrl();

	void setApiUrl(String apiUrl);

	String getApiKey();

	void setApiKey(String apiKey);
}