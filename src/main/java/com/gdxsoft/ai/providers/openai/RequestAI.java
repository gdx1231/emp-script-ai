package com.gdxsoft.ai.providers.openai;

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

import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.easyweb.utils.UJSon;

public class RequestAI extends RequestAIBase {
	// OpenAI的流式API网址
	public static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

	public RequestAI() {
		this.providerType = ProviderType.OPENAI;
	}

	/**
	 * 调用OpenAI的流式API
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
	 * 提取OpenAI返回的JSON数据
	 * 
	 * @param line 原始数据行
	 * @return 解析后的JSON对象
	 */
	public JSONObject extraceJson(String line, boolean skipDataPrefix) {
		/*
		 * data:
		 * {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,
		 * "model":"gpt-3.5-turbo-0125","system_fingerprint":"fp_44709d6fcb","choices":[
		 * {"index":0,
		 * "delta":{"content":"Hello"},"logprobs":null,"finish_reason":null}]}
		 */
		// 提取 data: 后面的 JSON
		String jsonData = null;
		if (!skipDataPrefix) {
			if (!line.startsWith("data:")) {
				return UJSon.rstFalse("没有data:的数据行，" + line);
			}

			jsonData = line.substring(5).trim();
		} else {
			jsonData = line.trim();
		}
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		// 检查是否是结束标记
		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
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
