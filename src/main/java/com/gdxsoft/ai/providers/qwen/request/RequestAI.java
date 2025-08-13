package com.gdxsoft.ai.providers.qwen.request;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.providers.IRequestData;
import com.gdxsoft.ai.providers.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// 通义千问的流式API 网址，openai兼容模式
	public static final String DEFAULT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

	/**
	 * 调用通义千问的流式API
	 * 
	 * @param reqData 用户输入的提示词
	 * @param writer  输出流
	 * @return 完整的响应文本
	 * @throws IOException        IO异常
	 * @throws URISyntaxException
	 */
	@Override
	public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException {
		String u = super.getApiUrl();
		if (StringUtils.isBlank(u)) {
			u = DEFAULT_URL;
		}
		URI url = new URI(u);
		HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
		conn.setRequestMethod("POST");
		if (super.getApiKey() != null && !super.getApiKey().isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + super.getApiKey());
		}
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "text/event-stream");
		conn.setDoOutput(true);

		String jsonInput = reqData.buildJson();
		// System.out.println(jsonInput);

		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				this.handleLine(line, writer);
			}
		}
		return super.getFullText().toString();
	}

	/**
	 * 提取通义千问返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line) {
		/*
		 * data: {"choices":[{"delta":{"content":"旨在为用户提供全面"}
		 * ,"finish_reason":null,"index":0,"logprobs":null}]
		 * ,"object":"chat.completion.chunk","usage":null,"created":1754902913
		 * ,"system_fingerprint":null,"model":"qwen-turbo"
		 * ,"id":"chatcmpl-500b1fb4-2b18-9cc7-82d5-c9d0e9fd38a9"}
		 */
		// 提取 data: 后面的 JSON

		if (!line.startsWith("data:")) {
			return UJSon.rstFalse("没有data:的数据行，" + line);
		}

		String jsonData = line.substring(5).trim();
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		// System.out.println(jsonData);
		// System.out.println();

		try {
			JSONObject json = new JSONObject(jsonData);
			JSONArray choices = json.getJSONArray("choices");
			if (choices.length() > 0) {
				JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
				UJSon.rstSetTrue(delta, null);
				return delta;
			}
			return UJSon.rstFalse("无效数据，choices.length() = 0" + line);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
