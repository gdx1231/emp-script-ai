package com.gdxsoft.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 测试 mode=auto 自动路由的 LLM 返回解析
 * {@link ChatManagerBase#extractRoutedModeName(String)}（纯解析，不依赖外部服务）。
 */
class ChatManagerRouteTest {

	@Test
	void testNormalJson() {
		assertEquals("chat", ChatManagerBase.extractRoutedModeName("{\"mode\":\"chat\"}"));
	}

	@Test
	void testJsonWithWhitespace() {
		assertEquals("translate", ChatManagerBase.extractRoutedModeName("  { \"mode\" : \"translate\" } \n"));
	}

	@Test
	void testMarkdownCodeBlock() {
		String content = "```json\n{\"mode\":\"chat\"}\n```";
		assertEquals("chat", ChatManagerBase.extractRoutedModeName(content));
	}

	@Test
	void testMarkdownCodeBlockWithoutLang() {
		String content = "```\n{\"mode\":\"chat\"}\n```";
		assertEquals("chat", ChatManagerBase.extractRoutedModeName(content));
	}

	@Test
	void testMissingModeField() {
		assertNull(ChatManagerBase.extractRoutedModeName("{\"name\":\"chat\"}"));
	}

	@Test
	void testEmptyModeField() {
		assertNull(ChatManagerBase.extractRoutedModeName("{\"mode\":\"\"}"));
		assertNull(ChatManagerBase.extractRoutedModeName("{\"mode\":\"  \"}"));
	}

	@Test
	void testInvalidJson() {
		assertNull(ChatManagerBase.extractRoutedModeName("chat"));
		assertNull(ChatManagerBase.extractRoutedModeName(""));
		assertNull(ChatManagerBase.extractRoutedModeName(null));
	}
}
