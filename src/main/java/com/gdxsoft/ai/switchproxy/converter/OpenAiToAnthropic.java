package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;

/**
 * OpenAI Chat Completions → Anthropic Messages API 请求转换。
 */
public class OpenAiToAnthropic {

	/**
	 * 将 OpenAI Chat 格式转换为 Anthropic Messages 格式。
	 */
	public static JSONObject convert(JSONObject openAiRequest, RouteConfig route, ProfileConfig profile) {
		JSONObject anthropicRequest = new JSONObject();

		// model
		String model = route.getEffectiveModel(profile);
		if (model != null) {
			anthropicRequest.put("model", model);
		}

		// max_tokens (Anthropic 必填)
		anthropicRequest.put("max_tokens", profile.getMaxTokens());

		// stream
		if (openAiRequest.has("stream")) {
			anthropicRequest.put("stream", openAiRequest.getBoolean("stream"));
		}

		// temperature
		if (openAiRequest.has("temperature")) {
			anthropicRequest.put("temperature", openAiRequest.getDouble("temperature"));
		}

		// messages 和 system
		JSONArray openAiMessages = openAiRequest.getJSONArray("messages");
		JSONArray anthropicMessages = new JSONArray();
		StringBuilder systemContent = new StringBuilder();

		for (int i = 0; i < openAiMessages.length(); i++) {
			JSONObject msg = openAiMessages.getJSONObject(i);
			String role = msg.getString("role");
			String content = msg.optString("content", "");

			if ("system".equals(role)) {
				if (systemContent.length() > 0) {
					systemContent.append("\n");
				}
				systemContent.append(content);
			} else if ("user".equals(role) || "assistant".equals(role)) {
				JSONObject anthropicMsg = new JSONObject();
				anthropicMsg.put("role", role);
				anthropicMsg.put("content", content);
				anthropicMessages.put(anthropicMsg);
			}
		}

		if (systemContent.length() > 0) {
			anthropicRequest.put("system", systemContent.toString());
		}
		anthropicRequest.put("messages", anthropicMessages);

		// tools
		if (openAiRequest.has("tools")) {
			JSONArray openAiTools = openAiRequest.getJSONArray("tools");
			JSONArray anthropicTools = new JSONArray();

			for (int i = 0; i < openAiTools.length(); i++) {
				JSONObject tool = openAiTools.getJSONObject(i);
				if ("function".equals(tool.optString("type"))) {
					JSONObject function = tool.getJSONObject("function");
					JSONObject anthropicTool = new JSONObject();
					anthropicTool.put("name", function.getString("name"));
					if (function.has("description")) {
						anthropicTool.put("description", function.getString("description"));
					}
					anthropicTool.put("input_schema", function.getJSONObject("parameters"));
					anthropicTools.put(anthropicTool);
				}
			}

			if (anthropicTools.length() > 0) {
				anthropicRequest.put("tools", anthropicTools);
			}
		}

		return anthropicRequest;
	}
}
