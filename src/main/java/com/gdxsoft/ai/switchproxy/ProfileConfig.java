package com.gdxsoft.ai.switchproxy;

/**
 * Profile 配置 POJO，对应 XML 中的 &lt;profile&gt; 元素。
 */
public class ProfileConfig {
	private String name;
	private String apiUrl;
	private String apiKey;
	private String model;
	private String format; // openai / anthropic / responses
	private int maxTokens = 4096;

	public ProfileConfig() {
	}

	public ProfileConfig(String name, String apiUrl, String apiKey, String model) {
		this.name = name;
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
		this.model = model;
	}

	public ProfileConfig(String name, String apiUrl, String apiKey, String model, String format) {
		this.name = name;
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
		this.model = model;
		this.format = format;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getApiUrl() {
		return apiUrl;
	}

	public void setApiUrl(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(int maxTokens) {
		this.maxTokens = maxTokens;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}
}
