package com.gdxsoft.ai.switchproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.HttpUtils;
import com.gdxsoft.ai.switchproxy.AccessKeyConfig;
import com.gdxsoft.ai.switchproxy.IpAccessController;
import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.SwitchConfig;
import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * HttpHandler 基类，封装公共的代理逻辑。
 */
public abstract class ProxyHandler implements HttpHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);
	private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);
	private static final DateTimeFormatter ID_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	protected final RouteConfig route;
	protected final ProfileConfig profile;
	protected final RequestLogger requestLogger;
	protected final HttpClient httpClient;
	protected final SwitchConfig config;
	protected final IpAccessController ipAccessController;

	protected ProxyHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger,
			SwitchConfig config) throws IOException {
		this.route = route;
		this.profile = profile;
		this.requestLogger = requestLogger;
		this.httpClient = HttpUtils.createHttpClient();
		this.config = config;
		this.ipAccessController = config != null ? config.createIpAccessController() : null;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		// IP 访问控制
		if (ipAccessController != null && ipAccessController.hasRules()) {
			String clientIp = getClientIp(exchange);
			if (!ipAccessController.isAllowed(clientIp)) {
				LOGGER.warn("IP 拒绝: {} {}", clientIp, route.getPath());
				sendError(exchange, 403, "Access denied: IP not allowed");
				return;
			}
		}

		// Access Key 校验
		if (config != null && config.hasAccessKeys()) {
			String accessKey = extractAccessKey(exchange);
			AccessKeyConfig keyConfig = config.validateAccessKey(accessKey);
			if (keyConfig == null) {
				LOGGER.warn("无效的 Access Key: {}", route.getPath());
				sendError(exchange, 401, "Access denied: invalid or missing access key");
				return;
			}
			keyConfig.recordUsage();
		}

		RequestLogEntry entry = new RequestLogEntry();
		entry.setId("req-" + LocalDateTime.now().format(ID_FMT) + "-" + REQUEST_COUNTER.incrementAndGet());
		entry.setMode(route.getMode());
		entry.setTarget(route.getEffectiveTarget(profile));
		entry.setModel(route.getEffectiveModel(profile));

		try {
			// 1. 读取请求 body
			byte[] originalBody = readBody(exchange);
			String originalBodyStr = new String(originalBody, "UTF-8");
			entry.setInput(originalBodyStr);

			// 记录请求头
			Map<String, String> headers = new LinkedHashMap<>();
			exchange.getRequestHeaders().forEach((k, v) -> {
				if (v != null && !v.isEmpty()) {
					headers.put(k, v.get(0));
				}
			});
			entry.setHeaders(headers);

			// 2. 构建转发请求
			byte[] forwardBody = buildForwardBody(originalBody, entry);
			Map<String, String> forwardHeaders = buildForwardHeaders(exchange);
			String targetUrl = buildTargetUrl(exchange);

			// 3. 发起 HTTP 请求
			HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
					.uri(URI.create(targetUrl))
					.timeout(Duration.ofMinutes(5));

			for (Map.Entry<String, String> h : forwardHeaders.entrySet()) {
				String key = h.getKey().toLowerCase();
				// 跳过 hop-by-hop headers
				if (key.equals("host") || key.equals("content-length") || key.equals("transfer-encoding")) {
					continue;
				}
				reqBuilder.header(h.getKey(), h.getValue());
			}
			reqBuilder.header("Content-Type", "application/json");
			reqBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(forwardBody));

			HttpRequest forwardRequest = reqBuilder.build();

			// 4. 处理响应
			boolean isStream = isStreamRequest(originalBodyStr);

			if (isStream) {
				handleStreamResponse(exchange, forwardRequest, entry);
			} else {
				handleNonStreamResponse(exchange, forwardRequest, entry);
			}

		} catch (Exception e) {
			LOGGER.error("代理请求失败: {} {}", route.getPath(), e.getMessage(), e);
			sendError(exchange, 502, "Proxy error: " + e.getMessage());
		} finally {
			// 9. 写日志
			requestLogger.log(entry);
		}
	}

	private void handleStreamResponse(HttpExchange exchange, HttpRequest forwardRequest, RequestLogEntry entry)
			throws IOException, InterruptedException {

		HttpResponse<Stream<String>> response = httpClient.send(forwardRequest,
				HttpResponse.BodyHandlers.ofLines());

		// 设置 SSE 响应头
		exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
		exchange.getResponseHeaders().set("Cache-Control", "no-cache");
		exchange.getResponseHeaders().set("Connection", "keep-alive");
		exchange.sendResponseHeaders(200, 0);

		OutputStream clientOut = exchange.getResponseBody();

		try (Stream<String> lines = response.body()) {
			lines.forEach(line -> {
				try {
					entry.appendRawSseLine(line);
					handleSseLine(line, clientOut, entry);
					clientOut.flush();
				} catch (IOException e) {
					LOGGER.debug("客户端断开: {}", e.getMessage());
				}
			});
		} catch (Exception e) {
			LOGGER.error("SSE 流处理异常: {}", e.getMessage(), e);
		} finally {
			entry.finalize(getUpstreamFormat());
			clientOut.close();
		}
	}

	private void handleNonStreamResponse(HttpExchange exchange, HttpRequest forwardRequest, RequestLogEntry entry)
			throws IOException, InterruptedException {

		HttpResponse<String> response = httpClient.send(forwardRequest, HttpResponse.BodyHandlers.ofString());

		exchange.getResponseHeaders().set("Content-Type",
				response.headers().firstValue("Content-Type").orElse("application/json"));
		byte[] body = response.body().getBytes("UTF-8");
		exchange.sendResponseHeaders(response.statusCode(), body.length);

		OutputStream clientOut = exchange.getResponseBody();
		clientOut.write(body);
		clientOut.close();

		entry.appendOutput(response.body());
		entry.finalize(getUpstreamFormat());
	}

	// === 子类需要实现的方法 ===

	/**
	 * 构建转发请求 body。
	 */
	protected abstract byte[] buildForwardBody(byte[] originalBody, RequestLogEntry entry);

	/**
	 * 构建转发请求 headers。
	 */
	protected abstract Map<String, String> buildForwardHeaders(HttpExchange exchange);

	/**
	 * 处理单行 SSE（写入客户端 + 日志累积）。
	 */
	protected abstract void handleSseLine(String line, OutputStream clientOut, RequestLogEntry entry)
			throws IOException;

	/**
	 * 上游 API 格式标识（用于日志 finalize 选择解析策略）。
	 */
	protected abstract String getUpstreamFormat();

	// === 辅助方法 ===

	/**
	 * 构建目标 URL：将客户端请求 path 中的路由前缀替换为实际 API URL。
	 */
	protected String buildTargetUrl(HttpExchange exchange) {
		String requestPath = exchange.getRequestURI().getPath();
		String routePath = route.getPath();

		// 去掉路由前缀，保留后面的 API path
		String apiPath = "";
		if (requestPath.length() > routePath.length()) {
			apiPath = requestPath.substring(routePath.length());
		}

		String apiUrl = route.getEffectiveApiUrl(profile);
		// apiUrl 已经包含完整的 URL（如 https://api.openai.com/v1/chat/completions）
		// 如果 apiPath 不为空，拼接到 apiUrl 后面
		if (!apiPath.isEmpty()) {
			// 如果 apiUrl 已经包含 path（如 /v1/chat/completions），则 apiPath 可能是多余的
			// 这里简单处理：如果 apiUrl 以 /v1/ 结尾，直接拼 apiPath
			if (apiUrl.endsWith("/v1") || apiUrl.endsWith("/v1/")) {
				return apiUrl + (apiPath.startsWith("/") ? apiPath : "/" + apiPath);
			}
			// 否则用 apiUrl 的 base + apiPath
			return apiUrl;
		}
		return apiUrl;
	}

	protected byte[] readBody(HttpExchange exchange) throws IOException {
		try (InputStream is = exchange.getRequestBody()) {
			return is.readAllBytes();
		}
	}

	protected boolean isStreamRequest(String body) {
		try {
			JSONObject json = new JSONObject(body);
			return json.optBoolean("stream", false);
		} catch (Exception e) {
			return false;
		}
	}

	protected void sendError(HttpExchange exchange, int code, String message) throws IOException {
		JSONObject error = new JSONObject();
		error.put("error", new JSONObject().put("message", message).put("type", "proxy_error"));
		byte[] body = error.toString().getBytes("UTF-8");
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(code, body.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body);
		}
	}

	/**
	 * 获取客户端 IP（支持 X-Forwarded-For / X-Real-IP 代理头）。
	 */
	protected String getClientIp(HttpExchange exchange) {
		// 优先从代理头获取
		String forwarded = getHeader(exchange, "X-Forwarded-For");
		if (forwarded != null && !forwarded.isEmpty()) {
			// 取第一个 IP（客户端真实 IP）
			return forwarded.split(",")[0].trim();
		}
		String realIp = getHeader(exchange, "X-Real-IP");
		if (realIp != null && !realIp.isEmpty()) {
			return realIp;
		}
		// 从 socket 获取
		return exchange.getRemoteAddress().getAddress().getHostAddress();
	}

	/**
	 * 从请求中提取 access key。
	 * 支持：Authorization: Bearer {key} 或 X-Access-Key: {key}
	 */
	protected String extractAccessKey(HttpExchange exchange) {
		// 优先从 X-Access-Key 头获取
		String key = getHeader(exchange, "X-Access-Key");
		if (key != null && !key.isEmpty()) {
			return key;
		}
		// 从 Authorization: Bearer {key} 获取
		String auth = getHeader(exchange, "Authorization");
		if (auth != null && auth.startsWith("Bearer ")) {
			return auth.substring(7).trim();
		}
		return null;
	}

	private String getHeader(HttpExchange exchange, String name) {
		var values = exchange.getRequestHeaders().get(name);
		if (values != null && !values.isEmpty()) {
			return values.get(0);
		}
		// 尝试大小写不敏感匹配
		for (var entry : exchange.getRequestHeaders().entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
				return entry.getValue().get(0);
			}
		}
		return null;
	}
}
