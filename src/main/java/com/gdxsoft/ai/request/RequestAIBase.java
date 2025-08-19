package com.gdxsoft.ai.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * AI 请求基类，提供通用的 HTTP 请求处理和流式响应处理
 */
public abstract class RequestAIBase implements IRequestAI {
	private static Logger LOGGER = LoggerFactory.getLogger(RequestAIBase.class.getName());
	private boolean useGzip = false; // control GZIP compression
	private IOutEvents outEvents;

	public IOutEvents getOutEvents() {
		if (outEvents == null) {
			outEvents = new DefaultOutEvents();
		}
		return outEvents;
	}

	public void setOutEvents(IOutEvents outEvents) {
		this.outEvents = outEvents;
	}
	 
	
	/**
	 * 调用非流式API
	 * 
	 * @param reqData 用户输入的提示词
	 */
	public String doPost(IRequestData reqData) throws IOException, URISyntaxException, InterruptedException {
		HttpClient client = createHttpClient();
		HttpRequest request = createHttpRequest(apiUrl, reqData);

		// Send request and handle response as a String
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		int statusCode = response.statusCode();
		if (statusCode == 200) {
			return response.body();
		} else {
			LOGGER.error("HTTP error code: {}, Response: {}", statusCode, response.body());
			throw new IOException("HTTP error code: " + statusCode + ", Response: " + response.body());
		}
	}

	/**
	 * 调用流式API
	 * 
	 * @param reqData 用户输入的提示词
	 * @param writer  输出流
	 * @return 完整的响应文本
	 */
	public String doStream(IRequestData reqData, PrintWriter writer)
			throws IOException, URISyntaxException, InterruptedException {
		HttpClient client = createHttpClient();
		HttpRequest request = createHttpRequest(apiUrl, reqData);

		// Send request and handle response as a stream
		HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());

		int statusCode = response.statusCode();
		if (200 == statusCode) {
			try (Stream<String> lines = response.body()) {
				// 处理每一行的响应数据
				lines.forEach(line -> this.handleLine(line, writer));
				return getFullText().toString();
			}
		} else {
			// Handle non-200 response
			StringBuilder errorResponse = new StringBuilder();
			try (Stream<String> lines = response.body()) {
				lines.forEach(line -> errorResponse.append(line).append("\n"));
			}
			LOGGER.error("HTTP error code: {}, Response: {}", statusCode, errorResponse.toString());
			throw new IOException("HTTP error code: " + statusCode + ", Response: " + errorResponse.toString());
		}
	}

	 
	
	private HttpClient createHttpClient() throws IOException {
		try {
			// Create a trust manager that trusts all certificates
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			} };

			// Initialize SSL context with the trust manager
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());

			// Build HttpClient with HTTP/2 support and custom SSL context
			return HttpClient.newBuilder().version(HttpClient.Version.HTTP_2) // Explicitly prefer HTTP/2
					.sslContext(sc) // Bypass SSL verification
					.connectTimeout(Duration.ofSeconds(20)).build();
		} catch (Exception e) {
			LOGGER.error("Failed to configure HttpClient: " + e.getMessage(), e);
			throw new IOException("Failed to configure HttpClient: " + e.getMessage(), e);
		}
	}

	private HttpRequest createHttpRequest(String u, IRequestData reqData) throws URISyntaxException, IOException {
		String jsonInput = reqData.buildJson();
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(new URI(u)) //
				.header("Content-Type", "application/json");
		// Set Accept header for doStream to text/event-stream
		if (Thread.currentThread().getStackTrace()[2].getMethodName().equals("doStream")) {
			builder.header("Accept", "text/event-stream");
		}
		if (useGzip) {
			byte[] gzipData = compressPostData(jsonInput);
			builder.header("Content-Encoding", "gzip").POST(HttpRequest.BodyPublishers.ofByteArray(gzipData));
		} else {
			builder.POST(HttpRequest.BodyPublishers.ofString(jsonInput));
		}
		// Add Authorization header if API key is present
		if (this.getApiKey() != null && !this.getApiKey().isEmpty()) {
			if (ProviderType.GEMINI == this.getProviderType()) {
				builder.header("x-goog-api-key", this.getApiKey());
			} else {
				builder.header("Authorization", "Bearer " + this.getApiKey());
			}
		}
		LOGGER.info("RequestAI URL: {}", u);
		return builder.build();
	}

	private byte[] compressPostData(String data) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
			gzipOut.write(data.getBytes("utf-8"));
			gzipOut.finish(); // Ensure all data is flushed
			return baos.toByteArray();
		}
	}

	private int messageCount = -1;

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

		IOutEvents oe = this.getOutEvents();
		oe.setLine(line);
		oe.setContenJson(json);
		oe.setMessageCount(messageCount);

		this.getOutEvents().outEvent(json.toString(), writer);
		// outEvent(json.toString(), writer);

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

	/**
	 * @return the useGzip
	 */
	public boolean isUseGzip() {
		return this.useGzip;
	}

	public void setUseGzip(boolean isUseGzip) {
		this.useGzip = isUseGzip;
	}

	/**
	 * @param messageCount the messageCount to set
	 */
	public void setMessageCount(int messageCount) {
		this.messageCount = messageCount;
	}

	/**
	 * @param fullText the fullText to set
	 */
	public void setFullText(StringBuilder fullText) {
		this.fullText = fullText;
	}

	/**
	 * @param providerType the providerType to set
	 */
	public void setProviderType(ProviderType providerType) {
		this.providerType = providerType;
	}

}
