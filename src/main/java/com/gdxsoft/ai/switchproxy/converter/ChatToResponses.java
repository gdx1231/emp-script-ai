package com.gdxsoft.ai.switchproxy.converter;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;

/**
 * OpenAI Chat Completions → Responses API 请求转换。
 */
public class ChatToResponses {

	/**
	 * 将 OpenAI Chat Completions 格式转换为 Responses API 格式。
	 */
	public static JSONObject convert(JSONObject chatRequest, RouteConfig route, ProfileConfig profile) {
		JSONObject responsesRequest = new JSONObject();

		// model
		String model = route.getEffectiveModel(profile);
		if (model != null) {
			responsesRequest.put("model", model);
		}

		// stream
		if (chatRequest.has("stream")) {
			responsesRequest.put("stream", chatRequest.getBoolean("stream"));
		}

		// temperature
		if (chatRequest.has("temperature")) {
			responsesRequest.put("temperature", chatRequest.getDouble("temperature"));
		}

		// max_tokens → max_output_tokens
		if (chatRequest.has("max_tokens")) {
			responsesRequest.put("max_output_tokens", chatRequest.getInt("max_tokens"));
		}

		// messages → instructions + input[]
		JSONArray chatMessages = chatRequest.getJSONArray("messages");
		JSONArray inputItems = new JSONArray();
		StringBuilder instructions = new StringBuilder();

		for (int i = 0; i < chatMessages.length(); i++) {
			JSONObject msg = chatMessages.getJSONObject(i);
			String role = msg.getString("role");
			String content = msg.optString("content", "");

			switch (role) {
				case "system":
					if (instructions.length() > 0) {
						instructions.append("\n");
					}
					instructions.append(content);
					break;

				case "user":
					inputItems.put(buildInputItem("user", content));
					break;

				case "assistant":
					// assistant 消息可能有 tool_calls
					JSONObject assistantItem = buildAssistantItem(msg);
					inputItems.put(assistantItem);
					break;

				case "tool":
					// tool 结果
					JSONObject toolOutput = new JSONObject();
					toolOutput.put("type", "function_call_output");
					toolOutput.put("call_id", msg.getString("tool_call_id"));
					toolOutput.put("output", content);
					inputItems.put(toolOutput);
					break;
			}
		}

		if (instructions.length() > 0) {
			responsesRequest.put("instructions", instructions.toString());
		}
		if (inputItems.length() > 0) {
			responsesRequest.put("input", inputItems);
		}

		// tools
		if (chatRequest.has("tools")) {
			JSONArray chatTools = chatRequest.getJSONArray("tools");
			JSONArray responsesTools = new JSONArray();

			for (int i = 0; i < chatTools.length(); i++) {
				JSONObject tool = chatTools.getJSONObject(i);
				if ("function".equals(tool.optString("type"))) {
					JSONObject function = tool.getJSONObject("function");
					JSONObject responsesTool = new JSONObject();
					responsesTool.put("type", "function");
					responsesTool.put("name", function.getString("name"));
					if (function.has("description")) {
						responsesTool.put("description", function.getString("description"));
					}
					responsesTool.put("parameters", function.getJSONObject("parameters"));
					responsesTools.put(responsesTool);
				}
			}

			if (responsesTools.length() > 0) {
				responsesRequest.put("tools", responsesTools);
			}
		}

		// tool_choice
		if (chatRequest.has("tool_choice")) {
			JSONObject chatToolChoice = chatRequest.getJSONObject("tool_choice");
			String tcType = chatToolChoice.optString("type", "auto");

			if ("auto".equals(tcType) || "none".equals(tcType) || "required".equals(tcType)) {
				responsesRequest.put("tool_choice", tcType);
			} else if ("function".equals(tcType)) {
				JSONObject fn = chatToolChoice.getJSONObject("function");
				JSONObject responsesTc = new JSONObject();
				responsesTc.put("type", "function");
				responsesTc.put("name", fn.getString("name"));
				responsesRequest.put("tool_choice", responsesTc);
			}
		}

		return responsesRequest;
	}

	private static JSONObject buildInputItem(String role, String content) {
		JSONObject item = new JSONObject();
		item.put("role", role);

		JSONArray contentArr = new JSONArray();
		JSONObject textItem = new JSONObject();
		textItem.put("type", "input_text");
		textItem.put("text", content);
		contentArr.put(textItem);

		item.put("content", contentArr);
		return item;
	}

	private static JSONObject buildAssistantItem(JSONObject msg) {
		// 如果有 tool_calls，构建 function_call output items
		if (msg.has("tool_calls")) {
			JSONArray toolCalls = msg.getJSONArray("tool_calls");
			if (toolCalls.length() > 0) {
				JSONObject tc = toolCalls.getJSONObject(0);
				JSONObject fn = tc.getJSONObject("function");
				JSONObject item = new JSONObject();
				item.put("type", "function_call");
				item.put("call_id", tc.optString("id", ""));
				item.put("name", fn.getString("name"));
				item.put("arguments", fn.optString("arguments", "{}"));
				return item;
			}
		}

		// 普通文本 assistant 消息
		String content = msg.optString("content", "");
		JSONObject item = new JSONObject();
		item.put("role", "assistant");

		JSONArray contentArr = new JSONArray();
		JSONObject textItem = new JSONObject();
		textItem.put("type", "output_text");
		textItem.put("text", content);
		contentArr.put(textItem);

		item.put("content", contentArr);
		return item;
	}
}
