package com.gdxsoft.ai.switchproxy.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.SwitchConfig;
import com.gdxsoft.ai.switchproxy.converter.AnthropicToResponses;
import com.gdxsoft.ai.switchproxy.converter.ResponsesToAnthropic;
import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpExchange;

/**
 * Responses2Anthropic Handler：接收 Responses API 格式，转为 Anthropic Messages API，响应再转回 Responses SSE。
 */
public class Responses2AnthropicHandler extends ProxyHandler {

	private final AnthropicToResponses responseConverter = new AnthropicToResponses();

	public Responses2AnthropicHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger,
			SwitchConfig config) throws IOException {
		super(route, profile, requestLogger, config);
	}

	@Override
	protected byte[] buildForwardBody(byte[] originalBody, RequestLogEntry entry) {
		JSONObject responsesRequest = new JSONObject(new String(originalBody, StandardCharsets.UTF_8));
		JSONObject anthropicRequest = ResponsesToAnthropic.convert(responsesRequest, route, profile);
		entry.setConvertedInput(anthropicRequest.toString());
		return anthropicRequest.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected Map<String, String> buildForwardHeaders(HttpExchange exchange) {
		Map<String, String> headers = new LinkedHashMap<>();
		String apiKey = route.getEffectiveApiKey(profile);
		headers.put("x-api-key", apiKey);
		headers.put("anthropic-version", "2023-06-01");
		headers.put("Content-Type", "application/json");
		return headers;
	}

	@Override
	protected void handleSseLine(String line, OutputStream clientOut, RequestLogEntry entry) throws IOException {
		// 调试：记录原始 SSE 行
		System.err.println("[DEBUG] Raw SSE line: " + line);

		String converted = responseConverter.convertSseLine(line);
		if (converted != null) {
			// 调试：记录转换后的 SSE 行
			System.err.println("[DEBUG] Converted SSE: " + converted.replace("\n", "\\n"));
			clientOut.write(converted.getBytes(StandardCharsets.UTF_8));
			clientOut.flush();
		}

		// 提取文本/思考用于日志
		String text = AnthropicToResponses.extractTextFromSseLine(line);
		if (text != null) {
			entry.appendOutput(text);
		}
		String thinking = AnthropicToResponses.extractThinkingFromSseLine(line);
		if (thinking != null) {
			entry.appendThinking(thinking);
		}
	}

	@Override
	protected String getUpstreamFormat() {
		return "anthropic";
	}

	@Override
	protected String buildTargetUrl(HttpExchange exchange) {
		String apiUrl = route.getEffectiveApiUrl(profile);
		return apiUrl != null ? apiUrl : "https://api.anthropic.com/v1/messages";
	}
}
