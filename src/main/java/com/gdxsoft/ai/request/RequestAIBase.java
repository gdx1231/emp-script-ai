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

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.utils.UJSon;

/**
 * AI 请求基类，提供通用的 HTTP 请求处理和流式响应处理。
 * <p>
 * Base class for AI requests, providing common HTTP request handling and
 * streaming response processing.
 */
public abstract class RequestAIBase implements IRequestAI {
	private static Logger LOGGER = LoggerFactory.getLogger(RequestAIBase.class.getName());
	private boolean useGzip = false; // control GZIP compression
	private IOutEvents outEvents;


	/**
	 * 取消正在执行的流式请求。
	 * <p>
	 * Cancel the ongoing doStream call if any. This closes the underlying response
	 * stream so that iteration stops promptly.
	 */
	public void cancelRequest() {
		this.cancelRequested = true;
		Stream<String> s = this.currentResponseStream;
		if (s != null) {
			try {
				s.close(); // Closing the stream cancels the subscription
			} catch (Exception e) {
				LOGGER.warn("Error while closing stream during cancel: {}", e.toString());
			}
		}
	}

	/**
	 * 转成Curl 命令行格式。| Convert to Curl command line format.
	 * @return Curl 命令行字符串 | the Curl command line string
	 */
	public String curl(IRequestData reqData) {
		StringBuilder sb = new StringBuilder();
		sb.append("curl -X POST '").append(this.createUrl(reqData)).append("' \\\n");
		sb.append(" -H 'Content-Type: application/json' \\\n");
		if (!StringUtils.isBlank(this.getApiKey())) {
			if (ProviderType.GEMINI == this.getProviderType()) {
				sb.append(" -H 'x-goog-api-key: ").append(this.getApiKey()).append("' \\\n");
			} else {
				sb.append(" -H 'Authorization: Bearer ").append(this.getApiKey()).append("' \\\n");
			}
		}
		sb.append(" -d '").append(reqData.build().toString(2)).append("'");

		return sb.toString();

	}

	/**
	 * 调用非流式 API。
	 * <p>
	 * Call the non-streaming API.
	 *
	 * @param reqData 用户输入的提示词 | the request data payload
	 * @return 响应字符串 | the raw response body as string
	 * @throws IOException          网络或 IO 错误 | on network/IO errors
	 * @throws URISyntaxException   URL 语法错误 | if the URL is invalid
	 * @throws InterruptedException 线程中断 | if the operation is interrupted
	 */
	public String doPost(IRequestData reqData) throws IOException, URISyntaxException, InterruptedException {
		HttpClient client = createHttpClient();
		HttpRequest request = createHttpRequest(createUrl(reqData), reqData);

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

	public String createUrl(IRequestData reqData) {
		return this.apiUrl;
	}

	/**
	 * 调用流式 API。
	 * <p>
	 * Call the streaming API.
	 *
	 * 套接字以 Server-Sent Events 的形式逐行返回数据。 The server responds with Server-Sent
	 * Events (SSE) lines.
	 *
	 * @param reqData 用户输入的提示词 | the request data payload
	 * @param writer  输出流（用于边接收边输出）| writer for incremental output
	 * @return 完整的响应文本 | the concatenated full response text
	 * @throws IOException          网络或 IO 错误 | on network/IO errors
	 * @throws URISyntaxException   URL 语法错误 | if the URL is invalid
	 * @throws InterruptedException 线程中断 | if the operation is interrupted
	 */
	public String doStream(IRequestData reqData, PrintWriter writer)
			throws IOException, URISyntaxException, InterruptedException {
		// reset cancel flag at the beginning of a new stream
		this.cancelRequested = false;
		HttpClient client = createHttpClient();
		HttpRequest request = createHttpRequest(createUrl(reqData), reqData);

		// Send request and handle response as a stream
		HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());

		int statusCode = response.statusCode();
		if (200 == statusCode) {
			Stream<String> lines = response.body();
			// keep a reference for external cancellation
			this.currentResponseStream = lines;
			try (Stream<String> autoClose = lines) {
				// 显式使用迭代器以便在取消时中断
				for (java.util.Iterator<String> it = autoClose.iterator(); !cancelRequested && it.hasNext();) {
					String line = it.next();
					this.handleLine(line, writer);
				}
				if (cancelRequested) {
					LOGGER.info("Streaming request canceled by user.");
				}
				return getFullText().toString();
			} finally {
				// clear reference and reset flag for next call
				this.currentResponseStream = null;
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

	/**
	 * 创建带有信任所有证书的 HttpClient（开发/内网环境下方便联调）。
	 * <p>
	 * Create an HttpClient that trusts all certificates (useful for dev/intranet).
	 *
	 * @return 配置完成的 HttpClient | configured HttpClient instance
	 * @throws IOException 初始化失败 | if initialization fails
	 */
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

	/**
	 * 创建 HTTP 请求（根据是否流式设置 Accept 头；根据 useGzip 选择压缩）。
	 * <p>
	 * Build the HTTP request (sets Accept for streaming; compresses when useGzip).
	 *
	 * @param u       请求地址 | request URL
	 * @param reqData 请求数据 | request payload object
	 * @return HttpRequest 实例 | the built HttpRequest
	 * @throws URISyntaxException URL 不合法 | if the URL is invalid
	 * @throws IOException        压缩失败 | if compression fails
	 */
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

	/**
	 * 使用 GZIP 压缩请求体字符串。
	 * <p>
	 * Compress the request body using GZIP.
	 *
	 * @param data 原始文本 | raw text data
	 * @return 压缩后的字节数组 | compressed bytes
	 * @throws IOException 压缩异常 | on compression errors
	 */
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

	// --- Cancellation support for streaming ---
	private volatile boolean cancelRequested = false;
	private volatile Stream<String> currentResponseStream;

	/**
	 * 获取累计的完整响应文本缓冲区。
	 * <p>
	 * Get the accumulated full response text buffer.
	 *
	 * @return StringBuilder 响应缓冲 | the response buffer
	 */
	public StringBuilder getFullText() {
		return fullText;
	}

	protected ProviderType providerType;

	/**
	 * 获取当前请求所属的 AI 提供商类型。
	 * <p>
	 * Get the AI provider type for this request.
	 *
	 * @return ProviderType | provider type, may be null if unset
	 */
	public ProviderType getProviderType() {
		return providerType;
	}

	public RequestAIBase() {
		// 默认不指定，具体实现类负责设置
		this.providerType = null;
	}

	/**
	 * 提取行文本中的 JSON 对象（不同厂商格式不同，由子类实现）。
	 * <p>
	 * Extract the JSON object from a text line (provider-specific; implemented by
	 * subclasses).
	 *
	 * @param line 一行文本（SSE 或普通响应）| a single response line (SSE or plain)
	 * @return 标准化的 JSON 对象 | normalized JSON object
	 */
	public JSONObject extraceJson(String line) {
		return extraceJson(line, false);
	}

	/**
	 * 提取OPENAI返回的JSON数据| Extract the JSON data returned by OPENAI.
	 * 
	 * @param jsonText       原始数据| Original data
	 * @param skipDataPrefix 是否跳过以 "data:" 开头的前缀| If true, skip the "data:" prefix
	 * @return 解析后的JSON对象| Parsed JSON object
	 */
	public JSONObject extraceJson(String jsonText, boolean skipDataPrefix) {
		/*
		 * data: {"choices":[{"delta":{"content":"旨在为用户提供全面"}
		 * ,"finish_reason":null,"index":0,"logprobs":null}]
		 * ,"object":"chat.completion.chunk","usage":null,"created":1754902913
		 * ,"system_fingerprint":null,"model":"qwen-turbo"
		 * ,"id":"chatcmpl-500b1fb4-2b18-9cc7-82d5-c9d0e9fd38a9"}
		 */
		String jsonData = null;
		// 提取 data: 后面的 JSON
		if (!skipDataPrefix) {
			if (!jsonText.startsWith("data:")) {
				return UJSon.rstFalse("没有data:的数据行，" + jsonText);
			}

			jsonData = jsonText.substring(5).trim();
			if (jsonData.isEmpty()) {
				return UJSon.rstFalse("data:无数据，" + jsonText);
			}
		} else {
			jsonData = jsonText;
		}

		try {
			JSONObject json = new JSONObject(jsonData);
			JSONArray choices = json.getJSONArray("choices");
			if (choices.length() > 0) {
				JSONObject choice = choices.getJSONObject(0);
				if (choice.has("delta")) {
					JSONObject delta = choice.getJSONObject("delta");

					// 如果没有 content 字段，返回整个 delta 对象
					UJSon.rstSetTrue(delta, null);
					return delta;

				} else if (choice.has("message")) {
					// 如果有 message 字段，返回 message.delta
					JSONObject message = choice.getJSONObject("message");
					UJSon.rstSetTrue(message, null);
					return message;
				} else {
					// 如果没有 delta 和 message 字段，返回整个 choice 对象
					return UJSon.rstFalse("无效数据，choice 中没有 delta 或 message 字段，" + jsonText);
				}
			}
			return UJSon.rstFalse("无效数据，choices.length() = 0" + jsonText);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + jsonText + ", 错误：" + e.getMessage());
		}
	}

	/**
	 * 初始化 API URL 和 API Key。
	 * <p>
	 * Initialize API URL and API key.
	 *
	 * @param apiUrl API 网址 | API endpoint URL
	 * @param apiKey API 密钥 | API key
	 */
	public void initUrlAndKey(String apiUrl, String apiKey) {
		if (apiUrl != null && !apiUrl.isEmpty()) {
			this.apiUrl = apiUrl;
		}
		if (apiKey != null && !apiKey.isEmpty()) {
			this.apiKey = apiKey;
		}
	}

	/**
	 * 自增消息计数并返回当前值。
	 * <p>
	 * Increment the message counter and return the new value.
	 *
	 * @return 当前消息序号 | current message index
	 */
	public int messageCountAdd() {
		this.messageCount++;
		return this.messageCount;
	}

	/**
	 * 处理每一行的响应数据，并回调输出事件。
	 * <p>
	 * Handle each response line and invoke output event callbacks.
	 *
	 * @param line   单行响应 | a response line
	 * @param writer 输出 Writer | writer for emitting output
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

	/**
	 * 获取 API URL。
	 * <p>
	 * Get the API URL.
	 */
	public String getApiUrl() {
		return apiUrl;
	}

	/**
	 * 设置 API URL。
	 * <p>
	 * Set the API URL.
	 */
	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	/**
	 * 获取 API Key。
	 * <p>
	 * Get the API key.
	 */
	public String getApiKey() {
		return apiKey;
	}

	/**
	 * 设置 API Key。
	 * <p>
	 * Set the API key.
	 */
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	/**
	 * 是否启用 GZIP 压缩请求体。
	 * <p>
	 * Whether to enable GZIP compression for request body.
	 *
	 * @return 是否启用 | whether enabled
	 */
	public boolean isUseGzip() {
		return this.useGzip;
	}

	/**
	 * 设置是否启用 GZIP 压缩请求体。
	 * <p>
	 * Enable/disable GZIP compression for request body.
	 *
	 * @param isUseGzip 是否启用 | whether to enable
	 */
	public void setUseGzip(boolean isUseGzip) {
		this.useGzip = isUseGzip;
	}

	/**
	 * 设置消息计数器初始值。
	 * <p>
	 * Set the initial value for message counter.
	 *
	 * @param messageCount 要设置的值 | value to set
	 */
	public void setMessageCount(int messageCount) {
		this.messageCount = messageCount;
	}

	/**
	 * 设置完整响应文本缓冲。
	 * <p>
	 * Set the full response text buffer.
	 *
	 * @param fullText 文本缓冲 | text buffer to use
	 */
	public void setFullText(StringBuilder fullText) {
		this.fullText = fullText;
	}

	/**
	 * 设置 AI 提供商类型。
	 * <p>
	 * Set the AI provider type.
	 *
	 * @param providerType 提供商类型 | provider type to set
	 */
	public void setProviderType(ProviderType providerType) {
		this.providerType = providerType;
	}

	/**
	 * 获取 AI 提供商名称。
	 * <p>
	 * Get AI provider name.
	 *
	 * @return 提供商名称，未设置返回 "unknown" | provider name or "unknown"
	 */
	public String getProviderName() {
		if (providerType == null) {
			return "unknown";
		}
		return providerType.toString();
	}

	/**
	 * 获取输出事件回调实例，如未设置则返回默认实现。
	 * <p>
	 * Get the output events callback instance; returns the default implementation
	 * if not set.
	 *
	 * @return IOutEvents 实例 | the IOutEvents instance
	 */
	public IOutEvents getOutEvents() {
		if (outEvents == null) {
			outEvents = new DefaultOutEvents();
		}
		return outEvents;
	}

	/**
	 * 设置输出事件回调实现。
	 * <p>
	 * Set the output events callback implementation.
	 *
	 * @param outEvents 回调实现 | the callback implementation
	 */
	public void setOutEvents(IOutEvents outEvents) {
		this.outEvents = outEvents;
	}
}
