package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;

/**
 * Responses API → Anthropic Messages API 请求转换。
 */
public class ResponsesToAnthropic {

	/**
	 * 将 Responses API 格式转换为 Anthropic Messages 格式。
	 */
	public static JSONObject convert(JSONObject responsesRequest, RouteConfig route, ProfileConfig profile) {
		JSONObject anthropicRequest = new JSONObject();

		// model
		String model = route.getEffectiveModel(profile);
		if (model != null) {
			anthropicRequest.put("model", model);
		}

		// max_tokens (Anthropic 必填)
		anthropicRequest.put("max_tokens", profile.getMaxTokens());

		// stream
		if (responsesRequest.has("stream")) {
			anthropicRequest.put("stream", responsesRequest.getBoolean("stream"));
		}

		// temperature
		if (responsesRequest.has("temperature")) {
			anthropicRequest.put("temperature", responsesRequest.getDouble("temperature"));
		}

		// instructions → system
		if (responsesRequest.has("instructions")) {
			anthropicRequest.put("system", responsesRequest.getString("instructions"));
		}

		// input → messages
		if (responsesRequest.has("input")) {
			JSONArray inputItems = responsesRequest.getJSONArray("input");
			JSONArray anthropicMessages = new JSONArray();

			for (int i = 0; i < inputItems.length(); i++) {
				Object itemObj = inputItems.get(i);
				
				if (itemObj instanceof String) {
					// 简单字符串，作为 user 消息
					JSONObject msg = new JSONObject();
					msg.put("role", "user");
					msg.put("content", (String) itemObj);
					anthropicMessages.put(msg);
				} else if (itemObj instanceof JSONObject) {
					JSONObject item = (JSONObject) itemObj;
					String type = item.optString("type", "");
					String role = item.optString("role", "user");

					if ("message".equals(type) || role.equals("user") || role.equals("assistant")) {
						JSONObject msg = new JSONObject();
						msg.put("role", role);

						// 处理 content
						if (item.has("content")) {
							Object content = item.get("content");
							if (content instanceof String) {
								msg.put("content", (String) content);
							} else if (content instanceof JSONArray) {
								// 多模态内容，简化处理
								JSONArray contentArr = (JSONArray) content;
								StringBuilder textContent = new StringBuilder();
								for (int j = 0; j < contentArr.length(); j++) {
									JSONObject part = contentArr.getJSONObject(j);
									if ("input_text".equals(part.optString("type")) || "output_text".equals(part.optString("type"))) {
										if (textContent.length() > 0) {
											textContent.append("\n");
										}
										textContent.append(part.optString("text", ""));
									}
								}
								msg.put("content", textContent.toString());
							}
						}

						anthropicMessages.put(msg);
					} else if ("function_call_output".equals(type)) {
						// tool 结果，转换为 user 消息
						JSONObject msg = new JSONObject();
						msg.put("role", "user");
						String toolResult = item.optString("output", "");
						msg.put("content", "Tool result: " + toolResult);
						anthropicMessages.put(msg);
					}
				}
			}

			anthropicRequest.put("messages", anthropicMessages);
		}

		// tools
		if (responsesRequest.has("tools")) {
			JSONArray responsesTools = responsesRequest.getJSONArray("tools");
			JSONArray anthropicTools = new JSONArray();

			for (int i = 0; i < responsesTools.length(); i++) {
				JSONObject tool = responsesTools.getJSONObject(i);
				if ("function".equals(tool.optString("type"))) {
					JSONObject anthropicTool = new JSONObject();
					anthropicTool.put("name", tool.getString("name"));
					if (tool.has("description")) {
						anthropicTool.put("description", tool.getString("description"));
					}
					if (tool.has("parameters")) {
						anthropicTool.put("input_schema", tool.getJSONObject("parameters"));
					} else {
						// 默认 schema
						JSONObject defaultSchema = new JSONObject();
						defaultSchema.put("type", "object");
						defaultSchema.put("properties", new JSONObject());
						anthropicTool.put("input_schema", defaultSchema);
					}
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
