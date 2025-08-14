package com.gdxsoft.ai.providers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.GZIPOutputStream;

import org.json.JSONObject;

public abstract class RequestAIBase implements IRequestAI {

	/**
	 * 使用 GZIP 压缩数据
	 * 
	 * @param postData
	 * @return
	 * @throws IOException
	 */
	// public static byte[] compressPostData (String postData) throws IOException {
	// // 使用 GZIP 压缩数据
	// ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
	// try (GZIPOutputStream gzipOutputStream = new
	// GZIPOutputStream(byteArrayOutputStream)) {
	// gzipOutputStream.write(postData.getBytes("UTF-8"));
	// }
	// // 获取压缩后的字节数组
	// byte[] compressedData = byteArrayOutputStream.toByteArray();
	//
	// return compressedData;
	// }
	public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException {
		HttpURLConnection conn = this.createApiConn(apiUrl, reqData);
		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				this.handleLine(line, writer);
			}
		}
		return getFullText().toString();
	}

	public HttpURLConnection createApiConn(String u, IRequestData reqData) throws IOException, URISyntaxException {
		String jsonInput = reqData.buildJson();
		byte[] gzipData = null;
		// try {
		// // 使用 GZIP 压缩数据
		// gzipData = RequestAIBase.compressPostData(jsonInput);
		// } catch (Exception e) {
		//
		// }

		URI url = new URI(u);
		HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
		conn.setRequestMethod("POST");
		if (this.getApiKey() != null && !this.getApiKey().isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + this.getApiKey());
		}
		conn.setRequestProperty("Content-Type", "application/json");
		// if (gzipData != null) {
		// // 声明使用 GZIP 压缩
		// conn.setRequestProperty("Content-Encoding", "gzip");
		// }
		conn.setRequestProperty("Accept", "text/event-stream");
		conn.setDoOutput(true);

		if (gzipData != null) {
			// 使用 GZIP
			try (OutputStream os = conn.getOutputStream()) {
				os.write(gzipData, 0, gzipData.length);
			}
		} else {
			byte[] input = jsonInput.getBytes("utf-8");
			try (OutputStream os = conn.getOutputStream()) {
				os.write(input, 0, input.length);
			}
		}
		return conn;
	}

	private int messageCount = 0;

	private String apiUrl;
	private String apiKey;

	private StringBuilder fullText = new StringBuilder();

	public StringBuilder getFullText() {
		return fullText;
	}

	protected ProviderType providerType;

	public ProviderType getProviderType() {
		return providerType;
	}

	public RequestAIBase() {
		// 默认不指定，具体实现类负责设置
		this.providerType = null;
	}

	/**
	 * 获取 AI 提供商类型
	 */
	public String getProviderName() {
		if (providerType == null) {
			return "unknown";
		}
		return providerType.toString();
	}

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
