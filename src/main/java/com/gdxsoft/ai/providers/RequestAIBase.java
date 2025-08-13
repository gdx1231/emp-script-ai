package com.gdxsoft.ai.providers;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;

import org.json.JSONObject;

public abstract class RequestAIBase implements IRequestAI {
	// 通义千问的流式API 网址，openai兼容模式

	private int messageCount = 0;

	private String apiUrl;
	private String apiKey;

	private StringBuilder fullText = new StringBuilder();

	public StringBuilder getFullText() {
		return fullText;
	}

	abstract public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException;
	/**
	 * 提取 JSON 对象
	 * 
	 * @param line
	 * @return
	 */
	abstract public JSONObject extraceJson(String line);

	/**
	 * 初始化 API URL 和 API Key
	 * 
	 * @param apiUrl API 网址
	 * @param apiKey API 密钥
	 */
	public void initUrlAndKey(String apiUrl, String apiKey) {
		if (apiUrl != null && !apiUrl.isEmpty()) {
			this.apiUrl = apiUrl;
		}
		if (apiKey != null && !apiKey.isEmpty()) {
			this.apiKey = apiKey;
		}
	}

	public int messageCountAdd() {
		this.messageCount++;
		return this.messageCount;
	}

	

	/**
	 * 处理每一行的响应数据
	 * 
	 * @param line
	 * @param writer
	 */
	public void handleLine(String line, PrintWriter writer) {
		JSONObject json = extraceJson(line);
		if (!json.getBoolean("RST")) {
			return;
		}
		int messageCount = messageCountAdd();
		// System.out.println(messageCount + "." + line);
		json.put("IDX", messageCount);
		if (json.has("content")) {
			this.getFullText().append(json.optString("content"));
		}
		outEvent(json.toString(), writer);
	}

	/**
	 * 输出事件数据到客户端
	 * 
	 * @param msg    消息内容
	 * @param writer 输出流
	 */
	@Override
	public void outEvent(Object msg, PrintWriter writer) {
		writer.print("data: " + msg.toString() + "\n\n");
		writer.flush();
	}

	public String getApiUrl() {
		return apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

}
