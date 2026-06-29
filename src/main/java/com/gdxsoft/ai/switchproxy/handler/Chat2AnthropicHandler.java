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
import com.gdxsoft.ai.switchproxy.converter.AnthropicToOpenAi;
import com.gdxsoft.ai.switchproxy.converter.OpenAiToAnthropic;
import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpExchange;

/**
 * Chat2Anthropic Handler：接收 OpenAI Chat 格式，转为 Anthropic Messages API，响应再转回 OpenAI SSE。
 */
public class Chat2AnthropicHandler extends ProxyHandler {

	private final AnthropicToOpenAi responseConverter = new AnthropicToOpenAi();

	public Chat2AnthropicHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger,
			SwitchConfig config) throws IOException {
		super(route, profile, requestLogger, config);
	}

	@Override
	protected byte[] buildForwardBody(byte[] originalBody, RequestLogEntry entry) {
		JSONObject openAiRequest = new JSONObject(new String(originalBody, StandardCharsets.UTF_8));
		JSONObject anthropicRequest = OpenAiToAnthropic.convert(openAiRequest, route, profile);
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
		String converted = responseConverter.convertSseLine(line);
		if (converted != null) {
			clientOut.write((converted + "\n\n").getBytes(StandardCharsets.UTF_8));
		}

		// 提取文本/思考用于日志
		String text = AnthropicToOpenAi.extractTextFromSseLine(line);
		if (text != null) {
			entry.appendOutput(text);
		}
		String thinking = AnthropicToOpenAi.extractThinkingFromSseLine(line);
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
		// Anthropic Messages API 固定 URL
		String apiUrl = route.getEffectiveApiUrl(profile);
		return apiUrl != null ? apiUrl : "https://api.anthropic.com/v1/messages";
	}
}
