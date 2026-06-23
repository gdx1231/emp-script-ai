package com.gdxsoft.ai.switchproxy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SwitchConfig 单元测试。
 */
public class SwitchConfigTest {

	@TempDir
	Path tempDir;

	@Test
	void testLoadAndSave() throws IOException {
		// 写入测试配置
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<switch>
				  <server host="127.0.0.1" port="9090" />
				  <log dir="~/.emp-script-ai/logs" />
				  <profiles>
				    <profile name="test"
				             api-url="https://api.test.com/v1/chat/completions"
				             api-key="sk-test-key"
				             model="test-model" />
				    <profile name="claude"
				             api-url="https://api.anthropic.com/v1/messages"
				             api-key="sk-ant-test"
				             model="claude-sonnet-4-20250514"
				             max-tokens="8192" />
				  </profiles>
				  <routes>
				    <route path="/test/openai/v1"
				           mode="passthrough"
				           profile="test" />
				    <route path="/claude/anthropic/v1"
				           mode="chat2anthropic"
				           profile="claude" />
				    <route path="/inline/openai/v1"
				           mode="passthrough"
				           target="inline-target"
				           api-url="https://inline.api/v1/chat"
				           api-key="sk-inline"
				           model="inline-model" />
				  </routes>
				</switch>
				""";

		Path configFile = tempDir.resolve("test-config.xml");
		Files.writeString(configFile, xml);

		// 加载
		SwitchConfig config = SwitchConfig.load(configFile);

		assertEquals("127.0.0.1", config.getHost());
		assertEquals(9090, config.getPort());
		assertEquals("~/.emp-script-ai/logs", config.getLogDir());

		// profiles
		assertEquals(2, config.getProfiles().size());

		ProfileConfig testProfile = config.getProfile("test");
		assertNotNull(testProfile);
		assertEquals("https://api.test.com/v1/chat/completions", testProfile.getApiUrl());
		assertEquals("sk-test-key", testProfile.getApiKey());
		assertEquals("test-model", testProfile.getModel());
		assertEquals(4096, testProfile.getMaxTokens()); // 默认值

		ProfileConfig claudeProfile = config.getProfile("claude");
		assertNotNull(claudeProfile);
		assertEquals(8192, claudeProfile.getMaxTokens());

		// routes
		assertEquals(3, config.getRoutes().size());

		RouteConfig route1 = config.getRoutes().get(0);
		assertEquals("/test/openai/v1", route1.getPath());
		assertEquals("passthrough", route1.getMode());
		assertEquals("test", route1.getProfile());
		assertTrue(route1.hasProfile());

		RouteConfig route3 = config.getRoutes().get(2);
		assertEquals("/inline/openai/v1", route3.getPath());
		assertFalse(route3.hasProfile());
		assertEquals("inline-target", route3.getTarget());
		assertEquals("https://inline.api/v1/chat", route3.getApiUrl());
		assertEquals("sk-inline", route3.getApiKey());
		assertEquals("inline-model", route3.getModel());

		// 保存并重新加载
		Path savedFile = tempDir.resolve("saved-config.xml");
		config.save(savedFile);
		assertTrue(Files.exists(savedFile));

		SwitchConfig reloaded = SwitchConfig.load(savedFile);
		assertEquals(config.getHost(), reloaded.getHost());
		assertEquals(config.getPort(), reloaded.getPort());
		assertEquals(config.getProfiles().size(), reloaded.getProfiles().size());
		assertEquals(config.getRoutes().size(), reloaded.getRoutes().size());
	}

	@Test
	void testRouteEffectiveValues() {
		ProfileConfig profile = new ProfileConfig("test", "https://api.test.com", "sk-profile", "profile-model");
		profile.setMaxTokens(2048);

		// route 引用 profile，无覆盖
		RouteConfig route1 = new RouteConfig();
		route1.setProfile("test");
		assertEquals("https://api.test.com", route1.getEffectiveApiUrl(profile));
		assertEquals("sk-profile", route1.getEffectiveApiKey(profile));
		assertEquals("profile-model", route1.getEffectiveModel(profile));
		assertEquals("test", route1.getEffectiveTarget(profile));

		// route 有 model 覆盖
		RouteConfig route2 = new RouteConfig();
		route2.setProfile("test");
		route2.setModel("override-model");
		assertEquals("override-model", route2.getEffectiveModel(profile));

		// 内联配置
		RouteConfig route3 = new RouteConfig();
		route3.setTarget("inline");
		route3.setApiUrl("https://inline.com");
		route3.setApiKey("sk-inline");
		route3.setModel("inline-model");
		assertEquals("https://inline.com", route3.getEffectiveApiUrl(profile));
		assertEquals("sk-inline", route3.getEffectiveApiKey(profile));
		assertEquals("inline-model", route3.getEffectiveModel(profile));
		assertEquals("inline", route3.getEffectiveTarget(profile));
	}

	@Test
	void testLoadNonExistentFile() {
		Path nonExistent = tempDir.resolve("non-existent.xml");
		assertThrows(IOException.class, () -> SwitchConfig.load(nonExistent));
	}

	@Test
	void testResolveLogDir() {
		SwitchConfig config = new SwitchConfig();
		config.setLogDir("~/.emp-script-ai/logs");

		Path resolved = config.resolveLogDir();
		assertTrue(resolved.isAbsolute());
		assertTrue(resolved.toString().contains(".emp-script-ai"));
		assertFalse(resolved.toString().startsWith("~"));
	}
}
