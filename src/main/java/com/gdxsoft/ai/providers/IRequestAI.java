package com.gdxsoft.ai.providers;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;

public interface IRequestAI {
	/**
	 * 初始化 API URL 和 API Key
	 * 
	 * @param apiUrl
	 * @param apiKey
	 */
	void initUrlAndKey(String apiUrl, String apiKey);

	/**
	 * 调用通义千问的流式API
	 * 
	 * @param reqData 用户输入的提示词
	 * @param writer  输出流
	 * @return 完整的响应文本
	 * @throws IOException        IO异常
	 * @throws URISyntaxException
	 */
	String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException;

	/**
	 * 输出事件数据到客户端
	 * 
	 * @param msg    消息内容
	 * @param writer 输出流
	 */
	void outEvent(Object msg, PrintWriter writer);

	String getApiUrl();

	void setApiUrl(String apiUrl);

	String getApiKey();

	void setApiKey(String apiKey);
}