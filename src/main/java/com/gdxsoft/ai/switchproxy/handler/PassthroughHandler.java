package com.gdxsoft.ai.switchproxy.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.SwitchConfig;
import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpExchange;

/**
 * 直接转发 Handler：请求/响应原样透传。
 */
public class PassthroughHandler extends ProxyHandler {

	public PassthroughHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger,
			SwitchConfig config) throws IOException {
		super(route, profile, requestLogger, config);
	}

	@Override
	protected byte[] buildForwardBody(byte[] originalBody, RequestLogEntry entry) {
		// 替换 model
		String model = route.getEffectiveModel(profile);
		if (model != null && !model.isEmpty()) {
			try {
				org.json.JSONObject json = new org.json.JSONObject(new String(originalBody, "UTF-8"));
				json.put("model", model);
				byte[] modified = json.toString().getBytes("UTF-8");
				entry.setConvertedInput(json.toString());
				return modified;
			} catch (Exception e) {
				// 解析失败，原样转发
			}
		}
		return originalBody;
	}

	@Override
	protected Map<String, String> buildForwardHeaders(HttpExchange exchange) {
		Map<String, String> headers = new LinkedHashMap<>();
		String apiKey = route.getEffectiveApiKey(profile);

		// 根据目标 URL 判断认证方式
		String apiUrl = route.getEffectiveApiUrl(profile);
		if (apiUrl != null && apiUrl.contains("anthropic.com")) {
			headers.put("x-api-key", apiKey);
			headers.put("anthropic-version", "2023-06-01");
		} else {
			headers.put("Authorization", "Bearer " + apiKey);
		}

		// 复制其他有用的 headers
		copyHeader(exchange, headers, "Accept");
		copyHeader(exchange, headers, "Accept-Language");

		return headers;
	}

	@Override
	protected void handleSseLine(String line, OutputStream clientOut, RequestLogEntry entry) throws IOException {
		// 原样写入客户端
		byte[] lineBytes = (line + "\n").getBytes("UTF-8");
		clientOut.write(lineBytes);

		// 提取文本用于日志
		String text = extractTextFromOpenAiSse(line);
		if (text != null) {
			entry.appendOutput(text);
		}
	}

	@Override
	protected String getUpstreamFormat() {
		String apiUrl = route.getEffectiveApiUrl(profile);
		if (apiUrl != null && apiUrl.contains("anthropic.com")) {
			return "anthropic";
		}
		if (apiUrl != null && apiUrl.contains("/responses")) {
			return "responses";
		}
		return "openai";
	}

	private void copyHeader(HttpExchange exchange, Map<String, String> headers, String name) {
		var values = exchange.getRequestHeaders().get(name);
		if (values != null && !values.isEmpty()) {
			headers.put(name, values.get(0));
		}
	}

	private String extractTextFromOpenAiSse(String line) {
		if (line == null || !line.startsWith("data:")) {
			return null;
		}
		String data = line.substring(5).trim();
		if ("[DONE]".equals(data) || data.isEmpty()) {
			return null;
		}
		try {
			org.json.JSONObject json = new org.json.JSONObject(data);
			org.json.JSONArray choices = json.optJSONArray("choices");
			if (choices != null && choices.length() > 0) {
				org.json.JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
				if (delta != null) {
					return delta.optString("content", null);
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}
}
