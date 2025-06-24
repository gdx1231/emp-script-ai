package com.gdxsoft.ai.providers.gemini.response;


import org.json.JSONObject;

//TokenDetails 类
public class TokenDetails {
	private String modality;
	private Integer tokenCount;

	// 无参构造函数
	public TokenDetails() {
	}

	// Getters and Setters
	public String getModality() {
		return modality;
	}

	public void setModality(String modality) {
		this.modality = modality;
	}

	public Integer getTokenCount() {
		return tokenCount;
	}

	public void setTokenCount(Integer tokenCount) {
		this.tokenCount = tokenCount;
	}

	@Override
	public String toString() {
		return "TokenDetails{" + "modality='" + modality + '\'' + ", tokenCount=" + tokenCount + '}';
	}

	/**
	 * 静态工厂方法：从 JSONObject 构建 TokenDetails 对象。
	 * 
	 * @param jsonObject org.json.JSONObject 对象
	 * @return 解析后的 TokenDetails 对象
	 * @throws org.json.JSONException 如果 JSONObject 缺少必要字段或类型不匹配
	 */
	public static TokenDetails fromJsonObject(JSONObject jsonObject) throws org.json.JSONException {
		TokenDetails tokenDetails = new TokenDetails();
		if (jsonObject.has("modality")) {
			tokenDetails.setModality(jsonObject.getString("modality"));
		}
		if (jsonObject.has("tokenCount")) {
			tokenDetails.setTokenCount(jsonObject.getInt("tokenCount"));
		}
		return tokenDetails;
	}
}
