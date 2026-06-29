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
import com.gdxsoft.ai.switchproxy.converter.ChatToResponses;
import com.gdxsoft.ai.switchproxy.converter.ResponsesToChat;
import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpExchange;

/**
 * Chat2Responses Handler：接收 OpenAI Chat Completions 格式，转为 Responses API，响应再转回 Chat SSE。
 */
public class Chat2ResponsesHandler extends ProxyHandler {

	private final ResponsesToChat responseConverter = new ResponsesToChat();

	public Chat2ResponsesHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger,
			SwitchConfig config) throws IOException {
		super(route, profile, requestLogger, config);
	}

	@Override
	protected byte[] buildForwardBody(byte[] originalBody, RequestLogEntry entry) {
		JSONObject chatRequest = new JSONObject(new String(originalBody, StandardCharsets.UTF_8));
		JSONObject responsesRequest = ChatToResponses.convert(chatRequest, route, profile);
		entry.setConvertedInput(responsesRequest.toString());
		return responsesRequest.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected Map<String, String> buildForwardHeaders(HttpExchange exchange) {
		Map<String, String> headers = new LinkedHashMap<>();
		String apiKey = route.getEffectiveApiKey(profile);
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	@Override
	protected void handleSseLine(String line, OutputStream clientOut, RequestLogEntry entry) throws IOException {
		String converted = responseConverter.convertSseLine(line);
		if (converted != null) {
			// convertSseLine 可能返回多行（如 response.completed 包含 finish + [DONE]）
			for (String outputLine : converted.split("\n")) {
				clientOut.write((outputLine + "\n").getBytes(StandardCharsets.UTF_8));
			}
		}

		// 提取文本用于日志
		String text = ResponsesToChat.extractTextFromSseLine(line);
		if (text != null) {
			entry.appendOutput(text);
		}
	}

	@Override
	protected String getUpstreamFormat() {
		return "responses";
	}

	@Override
	protected String buildTargetUrl(HttpExchange exchange) {
		String apiUrl = route.getEffectiveApiUrl(profile);
		return apiUrl != null ? apiUrl : "https://api.openai.com/v1/responses";
	}
}
