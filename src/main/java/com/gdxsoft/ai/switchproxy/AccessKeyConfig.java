package com.gdxsoft.ai.switchproxy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 访问密钥配置。
 * <p>
 * Key 格式：emai-switch-{uuid}
 */
public class AccessKeyConfig {
	private static final String KEY_PREFIX = "emai-switch-";
	private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private String key;
	private String name; // 可选描述
	private String createdAt;
	private String lastUsedAt;
	private boolean enabled = true;

	public AccessKeyConfig() {
	}

	public AccessKeyConfig(String name) {
		this.key = generateKey();
		this.name = name;
		this.createdAt = LocalDateTime.now().format(TIMESTAMP_FMT);
	}

	/**
	 * 生成 emai-switch-{uuid} 格式的 key。
	 */
	public static String generateKey() {
		return KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * 检查字符串是否是合法的 key 格式。
	 */
	public static boolean isValidKey(String key) {
		return key != null && key.startsWith(KEY_PREFIX) && key.length() == KEY_PREFIX.length() + 32;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(String lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 记录使用时间。
	 */
	public void recordUsage() {
		this.lastUsedAt = LocalDateTime.now().format(TIMESTAMP_FMT);
	}
}
