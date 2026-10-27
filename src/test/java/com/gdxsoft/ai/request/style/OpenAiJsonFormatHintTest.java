package com.gdxsoft.ai.request.style;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.gdxsoft.ai.request.AiTextContent;
import com.gdxsoft.ai.request.IRequestData;

/**
 * 验证 {@code response_format=json_object} 时自动补齐 messages 中的 "json" 关键字。
 * <p>
 * OpenAI 与 DashScope 等兼容服务在 messages 不含 "json" 时直接返回 400：
 * {@code 'messages' must contain the word 'json' in some form}。
 */
class OpenAiJsonFormatHintTest {

	private IRequestData newRequest() {
		return new com.gdxsoft.ai.providers.qwen.RequestData();
	}

	@Test
	void jsonObjectWithoutJsonWord_prependsSystemHint() {
		IRequestData rd = newRequest().responseFormat("json_object");
		rd.userMessage("航班号：UA888");

		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(2, messages.length());
		JSONObject first = messages.getJSONObject(0);
		assertEquals("system", first.getString("role"));
		assertTrue(first.getString("content").toLowerCase().contains("json"));
		assertEquals("航班号：UA888", messages.getJSONObject(1).getString("content"));
	}

	@Test
	void jsonObjectWithExistingSystemMessage_appendsHint() {
		IRequestData rd = newRequest().responseFormat("json_object");
		rd.systemMessage("你是一个航空专家，禁止编造数据");
		rd.userMessage("航班号：UA888");

		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(2, messages.length(), "已有 system 消息时不应新增消息");
		JSONObject system = messages.getJSONObject(0);
		assertEquals("system", system.getString("role"));
		assertTrue(system.getString("content").startsWith("你是一个航空专家"));
		assertTrue(system.getString("content").toLowerCase().contains("json"));
	}

	@Test
	void jsonObjectWhenJsonAlreadyMentioned_keepsMessages() {
		IRequestData rd = newRequest().responseFormat("json_object");
		rd.systemMessage("你是一个航空专家");
		rd.userMessage("请按 JSON 格式返回航班信息");

		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(2, messages.length());
		assertEquals("你是一个航空专家", messages.getJSONObject(0).getString("content"));
		assertEquals("请按 JSON 格式返回航班信息", messages.getJSONObject(1).getString("content"));
	}

	@Test
	void jsonObjectWithMultimodalJsonPart_keepsMessages() {
		IRequestData rd = newRequest().responseFormat("json_object");
		rd.addUserMultiPart(new AiTextContent("按 json 返回图片描述"));

		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(1, messages.length());
	}

	@Test
	void textFormat_doesNotAddHint() {
		IRequestData rd = newRequest();
		rd.userMessage("你好");

		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(1, messages.length());
		assertEquals("user", messages.getJSONObject(0).getString("role"));
	}

	@Test
	void buildTwice_doesNotDuplicateHint() {
		IRequestData rd = newRequest().responseFormat("json_object");
		rd.userMessage("航班号：UA888");

		rd.build();
		JSONArray messages = rd.build().getJSONArray("messages");
		assertEquals(2, messages.length(), "重复 build 不应重复插入提示");
	}
}
