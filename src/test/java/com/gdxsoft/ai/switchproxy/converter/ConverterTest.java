package com.gdxsoft.ai.switchproxy.converter;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;

/**
 * 格式转换器单元测试。
 */
public class ConverterTest {

	// === OpenAiToAnthropic ===

	@Test
	void testOpenAiToAnthropic_basicConversion() {
		JSONObject openAiReq = new JSONObject();
		openAiReq.put("model", "gpt-4o");
		openAiReq.put("stream", true);
		openAiReq.put("temperature", 0.7);

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "system").put("content", "You are helpful."));
		messages.put(new JSONObject().put("role", "user").put("content", "Hello"));
		openAiReq.put("messages", messages);

		RouteConfig route = new RouteConfig();
		route.setModel("claude-sonnet-4-20250514");

		ProfileConfig profile = new ProfileConfig();
		profile.setMaxTokens(4096);

		JSONObject result = OpenAiToAnthropic.convert(openAiReq, route, profile);

		assertEquals("claude-sonnet-4-20250514", result.getString("model"));
		assertEquals(4096, result.getInt("max_tokens"));
		assertTrue(result.getBoolean("stream"));
		assertEquals(0.7, result.getDouble("temperature"));
		assertEquals("You are helpful.", result.getString("system"));

		JSONArray anthropicMsgs = result.getJSONArray("messages");
		assertEquals(1, anthropicMsgs.length());
		assertEquals("user", anthropicMsgs.getJSONObject(0).getString("role"));
		assertEquals("Hello", anthropicMsgs.getJSONObject(0).getString("content"));
	}

	@Test
	void testOpenAiToAnthropic_multipleSystemMessages() {
		JSONObject openAiReq = new JSONObject();
		openAiReq.put("model", "gpt-4o");

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "system").put("content", "Part 1"));
		messages.put(new JSONObject().put("role", "system").put("content", "Part 2"));
		messages.put(new JSONObject().put("role", "user").put("content", "Hi"));
		openAiReq.put("messages", messages);

		RouteConfig route = new RouteConfig();
		ProfileConfig profile = new ProfileConfig();

		JSONObject result = OpenAiToAnthropic.convert(openAiReq, route, profile);

		String system = result.getString("system");
		assertTrue(system.contains("Part 1"));
		assertTrue(system.contains("Part 2"));
	}

	@Test
	void testOpenAiToAnthropic_toolConversion() {
		JSONObject openAiReq = new JSONObject();
		openAiReq.put("model", "gpt-4o");

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "user").put("content", "What's the weather?"));
		openAiReq.put("messages", messages);

		JSONArray tools = new JSONArray();
		JSONObject tool = new JSONObject();
		tool.put("type", "function");
		JSONObject function = new JSONObject();
		function.put("name", "get_weather");
		function.put("description", "Get weather info");
		JSONObject parameters = new JSONObject();
		parameters.put("type", "object");
		parameters.put("properties", new JSONObject().put("city", new JSONObject().put("type", "string")));
		function.put("parameters", parameters);
		tool.put("function", function);
		tools.put(tool);
		openAiReq.put("tools", tools);

		RouteConfig route = new RouteConfig();
		ProfileConfig profile = new ProfileConfig();

		JSONObject result = OpenAiToAnthropic.convert(openAiReq, route, profile);

		JSONArray anthropicTools = result.getJSONArray("tools");
		assertEquals(1, anthropicTools.length());

		JSONObject aTool = anthropicTools.getJSONObject(0);
		assertEquals("get_weather", aTool.getString("name"));
		assertEquals("Get weather info", aTool.getString("description"));
		assertNotNull(aTool.getJSONObject("input_schema"));
	}

	// === AnthropicToOpenAi ===

	@Test
	void testAnthropicToOpenAi_textStream() {
		AnthropicToOpenAi converter = new AnthropicToOpenAi();

		// message_start
		String result1 = converter.convertSseLine(
				"data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_001\"}}");
		assertNotNull(result1);
		assertTrue(result1.contains("\"role\":\"assistant\""));

		// content_block_delta (text)
		String result2 = converter.convertSseLine(
				"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
		assertNotNull(result2);
		assertTrue(result2.contains("\"content\":\"Hello\""));

		// message_delta (finish)
		String result3 = converter.convertSseLine(
				"data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}");
		assertNotNull(result3);
		assertTrue(result3.contains("\"finish_reason\":\"stop\""));

		// message_stop
		String result4 = converter.convertSseLine("data: {\"type\":\"message_stop\"}");
		assertNotNull(result4);
		assertTrue(result4.contains("[DONE]"));
	}

	@Test
	void testAnthropicToOpenAi_toolUse() {
		AnthropicToOpenAi converter = new AnthropicToOpenAi();

		// message_start
		converter.convertSseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_tc\"}}");

		// content_block_start (tool_use)
		String result1 = converter.convertSseLine(
				"data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"search\"}}");
		assertNotNull(result1);
		assertTrue(result1.contains("\"name\":\"search\""));
		assertTrue(result1.contains("\"tool_calls\""));

		// content_block_delta (input_json_delta)
		String result2 = converter.convertSseLine(
				"data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"q\\\":\"}}");
		assertNotNull(result2);
		assertTrue(result2.contains("\"arguments\""));

		// message_delta (tool_use finish)
		String result3 = converter.convertSseLine(
				"data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":10}}");
		assertNotNull(result3);
		assertTrue(result3.contains("\"finish_reason\":\"tool_calls\""));
	}

	@Test
	void testAnthropicToOpenAi_thinking() {
		AnthropicToOpenAi converter = new AnthropicToOpenAi();

		converter.convertSseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_th\"}}");

		String result = converter.convertSseLine(
				"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me think...\"}}");
		assertNotNull(result);
		assertTrue(result.contains("\"reasoning_content\":\"Let me think...\""));
	}

	@Test
	void testAnthropicToOpenAi_skipUnknownEvents() {
		AnthropicToOpenAi converter = new AnthropicToOpenAi();

		assertNull(converter.convertSseLine("data: {\"type\":\"ping\"}"));
		assertNull(converter.convertSseLine("data: {\"type\":\"unknown_event\"}"));
		assertNull(converter.convertSseLine("not a data line"));
	}

	@Test
	void testAnthropicToOpenAi_extractText() {
		String text = AnthropicToOpenAi.extractTextFromSseLine(
				"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello world\"}}");
		assertEquals("Hello world", text);

		assertNull(AnthropicToOpenAi.extractTextFromSseLine("data: [DONE]"));
		assertNull(AnthropicToOpenAi.extractTextFromSseLine("data: {\"type\":\"message_start\"}"));
	}

	@Test
	void testAnthropicToOpenAi_extractThinking() {
		String thinking = AnthropicToOpenAi.extractThinkingFromSseLine(
				"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Hmm...\"}}");
		assertEquals("Hmm...", thinking);

		assertNull(AnthropicToOpenAi.extractThinkingFromSseLine(
				"data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"nope\"}}"));
	}

	// === ChatToResponses ===

	@Test
	void testChatToResponses_basicConversion() {
		JSONObject chatReq = new JSONObject();
		chatReq.put("model", "gpt-4o");
		chatReq.put("stream", true);
		chatReq.put("temperature", 0.5);
		chatReq.put("max_tokens", 1000);

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "system").put("content", "Be concise."));
		messages.put(new JSONObject().put("role", "user").put("content", "Hello"));
		chatReq.put("messages", messages);

		RouteConfig route = new RouteConfig();
		route.setModel("codex-mini");

		ProfileConfig profile = new ProfileConfig();

		JSONObject result = ChatToResponses.convert(chatReq, route, profile);

		assertEquals("codex-mini", result.getString("model"));
		assertTrue(result.getBoolean("stream"));
		assertEquals(0.5, result.getDouble("temperature"));
		assertEquals(1000, result.getInt("max_output_tokens"));
		assertEquals("Be concise.", result.getString("instructions"));

		JSONArray input = result.getJSONArray("input");
		assertEquals(1, input.length());
		assertEquals("user", input.getJSONObject(0).getString("role"));
	}

	@Test
	void testChatToResponses_toolMessages() {
		JSONObject chatReq = new JSONObject();
		chatReq.put("model", "gpt-4o");

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "user").put("content", "What's the weather?"));

		// assistant with tool_calls
		JSONObject assistantMsg = new JSONObject();
		assistantMsg.put("role", "assistant");
		assistantMsg.put("content", "");
		JSONArray toolCalls = new JSONArray();
		JSONObject tc = new JSONObject();
		tc.put("id", "call_01");
		JSONObject fn = new JSONObject();
		fn.put("name", "get_weather");
		fn.put("arguments", "{\"city\":\"Beijing\"}");
		tc.put("function", fn);
		toolCalls.put(tc);
		assistantMsg.put("tool_calls", toolCalls);
		messages.put(assistantMsg);

		// tool result
		JSONObject toolMsg = new JSONObject();
		toolMsg.put("role", "tool");
		toolMsg.put("tool_call_id", "call_01");
		toolMsg.put("content", "{\"temp\":22}");
		messages.put(toolMsg);

		chatReq.put("messages", messages);

		RouteConfig route = new RouteConfig();
		ProfileConfig profile = new ProfileConfig();

		JSONObject result = ChatToResponses.convert(chatReq, route, profile);

		JSONArray input = result.getJSONArray("input");
		assertEquals(3, input.length());

		// user message
		assertEquals("user", input.getJSONObject(0).getString("role"));

		// assistant function_call
		assertEquals("function_call", input.getJSONObject(1).getString("type"));
		assertEquals("call_01", input.getJSONObject(1).getString("call_id"));
		assertEquals("get_weather", input.getJSONObject(1).getString("name"));

		// tool output
		assertEquals("function_call_output", input.getJSONObject(2).getString("type"));
		assertEquals("call_01", input.getJSONObject(2).getString("call_id"));
	}

	@Test
	void testChatToResponses_toolChoiceConversion() {
		JSONObject chatReq = new JSONObject();
		chatReq.put("model", "gpt-4o");

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "user").put("content", "Hi"));
		chatReq.put("messages", messages);

		// tool_choice: auto
		chatReq.put("tool_choice", new JSONObject().put("type", "auto"));

		RouteConfig route = new RouteConfig();
		ProfileConfig profile = new ProfileConfig();

		JSONObject result = ChatToResponses.convert(chatReq, route, profile);
		assertEquals("auto", result.getString("tool_choice"));

		// tool_choice: function
		JSONObject fnChoice = new JSONObject();
		fnChoice.put("type", "function");
		fnChoice.put("function", new JSONObject().put("name", "search"));
		chatReq.put("tool_choice", fnChoice);

		result = ChatToResponses.convert(chatReq, route, profile);
		JSONObject tc = result.getJSONObject("tool_choice");
		assertEquals("function", tc.getString("type"));
		assertEquals("search", tc.getString("name"));
	}

	// === ResponsesToChat ===

	@Test
	void testResponsesToChat_textStream() {
		ResponsesToChat converter = new ResponsesToChat();

		// response.created
		String result1 = converter.convertSseLine(
				"data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_001\"}}");
		assertNotNull(result1);
		assertTrue(result1.contains("\"role\":\"assistant\""));

		// response.output_text.delta
		String result2 = converter.convertSseLine(
				"data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}");
		assertNotNull(result2);
		assertTrue(result2.contains("\"content\":\"Hello\""));

		// response.completed
		String result3 = converter.convertSseLine(
				"data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_001\",\"usage\":{\"input_tokens\":5,\"output_tokens\":3,\"total_tokens\":8},\"output\":[]}}");
		assertNotNull(result3);
		assertTrue(result3.contains("\"finish_reason\":\"stop\""));
		assertTrue(result3.contains("\"prompt_tokens\":5"));
		assertTrue(result3.contains("[DONE]"));
	}

	@Test
	void testResponsesToChat_functionCall() {
		ResponsesToChat converter = new ResponsesToChat();

		converter.convertSseLine("data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_fc\"}}");

		// function_call_arguments.delta
		String result1 = converter.convertSseLine(
				"data: {\"type\":\"response.function_call_arguments.delta\",\"delta\":\"{\\\"q\\\":\"}");
		assertNotNull(result1);
		assertTrue(result1.contains("\"arguments\""));

		// function_call_arguments.done
		String result2 = converter.convertSseLine(
				"data: {\"type\":\"response.function_call_arguments.done\"}");
		assertNull(result2); // done 事件不输出

		// response.completed with function_call in output
		JSONObject completed = new JSONObject();
		completed.put("type", "response.completed");
		JSONObject response = new JSONObject();
		response.put("id", "resp_fc");
		response.put("usage", new JSONObject()
				.put("input_tokens", 10)
				.put("output_tokens", 8)
				.put("total_tokens", 18));
		JSONArray output = new JSONArray();
		output.put(new JSONObject()
				.put("type", "function_call")
				.put("name", "search")
				.put("call_id", "fc_01")
				.put("arguments", "{\"q\":\"test\"}"));
		response.put("output", output);
		completed.put("response", response);

		String result3 = converter.convertSseLine("data: " + completed.toString());
		assertNotNull(result3);
		assertTrue(result3.contains("\"finish_reason\":\"tool_calls\""));
	}

	@Test
	void testResponsesToChat_extractText() {
		String text = ResponsesToChat.extractTextFromSseLine(
				"data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello world\"}");
		assertEquals("Hello world", text);

		assertNull(ResponsesToChat.extractTextFromSseLine("data: [DONE]"));
		assertNull(ResponsesToChat.extractTextFromSseLine("data: {\"type\":\"response.created\"}"));
	}
}
