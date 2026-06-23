package com.gdxsoft.ai.switchproxy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * AccessKeyConfig 单元测试。
 */
public class AccessKeyConfigTest {

	@Test
	void testGenerateKeyFormat() {
		String key = AccessKeyConfig.generateKey();
		assertNotNull(key);
		assertTrue(key.startsWith("emai-switch-"), "Key 应以 emai-switch- 开头: " + key);
		// emai-switch- (12 chars) + 32 hex chars = 44 chars
		assertEquals(44, key.length(), "Key 长度应为 44: " + key);
	}

	@Test
	void testIsValidKey() {
		String key = AccessKeyConfig.generateKey();
		assertTrue(AccessKeyConfig.isValidKey(key));

		assertFalse(AccessKeyConfig.isValidKey(null));
		assertFalse(AccessKeyConfig.isValidKey(""));
		assertFalse(AccessKeyConfig.isValidKey("invalid-key"));
		assertFalse(AccessKeyConfig.isValidKey("emai-switch-")); // 太短
		assertFalse(AccessKeyConfig.isValidKey("emai-switch-123")); // 太短
	}

	@Test
	void testUniqueKeys() {
		String key1 = AccessKeyConfig.generateKey();
		String key2 = AccessKeyConfig.generateKey();
		assertNotEquals(key1, key2, "每次生成的 key 应唯一");
	}

	@Test
	void testConstructorWithName() {
		AccessKeyConfig config = new AccessKeyConfig("test-key");
		assertNotNull(config.getKey());
		assertTrue(config.getKey().startsWith("emai-switch-"));
		assertEquals("test-key", config.getName());
		assertNotNull(config.getCreatedAt());
		assertTrue(config.isEnabled());
	}

	@Test
	void testRecordUsage() {
		AccessKeyConfig config = new AccessKeyConfig("test");
		assertNull(config.getLastUsedAt());

		config.recordUsage();
		assertNotNull(config.getLastUsedAt());
	}

	@Test
	void testEnableDisable() {
		AccessKeyConfig config = new AccessKeyConfig("test");
		assertTrue(config.isEnabled());

		config.setEnabled(false);
		assertFalse(config.isEnabled());
	}
}
