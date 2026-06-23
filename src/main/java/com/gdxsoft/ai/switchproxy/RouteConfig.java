package com.gdxsoft.ai.switchproxy;

/**
 * 路由配置 POJO，对应 XML 中的 &lt;route&gt; 元素。
 */
public class RouteConfig {
	private String path;
	private String mode; // passthrough / chat2anthropic / chat2responses
	private String profile; // 引用的 profile name
	private String target; // 内联配置时的目标标识
	private String apiUrl; // 内联配置
	private String apiKey; // 内联配置
	private String model; // 可选，覆盖 profile 的 model

	public RouteConfig() {
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getProfile() {
		return profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
	}

	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
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

	/**
	 * 是否引用 profile（而非内联配置）。
	 */
	public boolean hasProfile() {
		return profile != null && !profile.isEmpty();
	}

	/**
	 * 获取有效的 API URL（内联优先，否则从 profile 获取）。
	 */
	public String getEffectiveApiUrl(ProfileConfig profileConfig) {
		return apiUrl != null && !apiUrl.isEmpty() ? apiUrl : profileConfig.getApiUrl();
	}

	/**
	 * 获取有效的 API Key。
	 */
	public String getEffectiveApiKey(ProfileConfig profileConfig) {
		return apiKey != null && !apiKey.isEmpty() ? apiKey : profileConfig.getApiKey();
	}

	/**
	 * 获取有效的 model（route 级覆盖 > profile > 空）。
	 */
	public String getEffectiveModel(ProfileConfig profileConfig) {
		if (model != null && !model.isEmpty()) {
			return model;
		}
		return profileConfig != null ? profileConfig.getModel() : null;
	}

	/**
	 * 获取有效的 target 标识（用于日志）。
	 */
	public String getEffectiveTarget(ProfileConfig profileConfig) {
		if (target != null && !target.isEmpty()) {
			return target;
		}
		return profileConfig != null ? profileConfig.getName() : "unknown";
	}
}
