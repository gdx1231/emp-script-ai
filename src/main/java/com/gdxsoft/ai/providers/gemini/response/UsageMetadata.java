package com.gdxsoft.ai.providers.gemini.response;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

//UsageMetadata 类
public class UsageMetadata {
	private Integer promptTokenCount;
	private Integer candidatesTokenCount;
	private Integer totalTokenCount;
	private List<TokenDetails> promptTokensDetails;
	private List<TokenDetails> candidatesTokensDetails;

	// 无参构造函数
	public UsageMetadata() {
	}

	// Getters and Setters
	public Integer getPromptTokenCount() {
		return promptTokenCount;
	}

	public void setPromptTokenCount(Integer promptTokenCount) {
		this.promptTokenCount = promptTokenCount;
	}

	public Integer getCandidatesTokenCount() {
		return candidatesTokenCount;
	}

	public void setCandidatesTokenCount(Integer candidatesTokenCount) {
		this.candidatesTokenCount = candidatesTokenCount;
	}

	public Integer getTotalTokenCount() {
		return totalTokenCount;
	}

	public void setTotalTokenCount(Integer totalTokenCount) {
		this.totalTokenCount = totalTokenCount;
	}

	public List<TokenDetails> getPromptTokensDetails() {
		return promptTokensDetails;
	}

	public void setPromptTokensDetails(List<TokenDetails> promptTokensDetails) {
		this.promptTokensDetails = promptTokensDetails;
	}

	public List<TokenDetails> getCandidatesTokensDetails() {
		return candidatesTokensDetails;
	}

	public void setCandidatesTokensDetails(List<TokenDetails> candidatesTokensDetails) {
		this.candidatesTokensDetails = candidatesTokensDetails;
	}

	@Override
	public String toString() {
		return "UsageMetadata{" + "promptTokenCount=" + promptTokenCount + ", candidatesTokenCount="
				+ candidatesTokenCount + ", totalTokenCount=" + totalTokenCount + ", promptTokensDetails="
				+ promptTokensDetails + ", candidatesTokensDetails=" + candidatesTokensDetails + '}';
	}

	/**
     * 静态工厂方法：从 JSONObject 构建 UsageMetadata 对象。
     * @param jsonObject org.json.JSONObject 对象
     * @return 解析后的 UsageMetadata 对象
     * @throws org.json.JSONException 如果 JSONObject 缺少必要字段或类型不匹配
     */
    public static UsageMetadata fromJsonObject(JSONObject jsonObject) throws org.json.JSONException {
        UsageMetadata usageMetadata = new UsageMetadata();
        if (jsonObject.has("promptTokenCount")) {
            usageMetadata.setPromptTokenCount(jsonObject.getInt("promptTokenCount"));
        }
        if (jsonObject.has("candidatesTokenCount")) {
            usageMetadata.setCandidatesTokenCount(jsonObject.getInt("candidatesTokenCount"));
        }
        if (jsonObject.has("totalTokenCount")) {
            usageMetadata.setTotalTokenCount(jsonObject.getInt("totalTokenCount"));
        }

        // 解析 promptTokensDetails 数组
        if (jsonObject.has("promptTokensDetails")) {
            JSONArray rawDetailsArray = jsonObject.getJSONArray("promptTokensDetails");
            List<TokenDetails> detailsList = new ArrayList<>();
            for (int i = 0; i < rawDetailsArray.length(); i++) {
                JSONObject rawDetail = rawDetailsArray.getJSONObject(i);
                detailsList.add(TokenDetails.fromJsonObject(rawDetail));
            }
            usageMetadata.setPromptTokensDetails(detailsList);
        }

        // 解析 candidatesTokensDetails 数组
        if (jsonObject.has("candidatesTokensDetails")) {
            JSONArray rawDetailsArray = jsonObject.getJSONArray("candidatesTokensDetails");
            List<TokenDetails> detailsList = new ArrayList<>();
            for (int i = 0; i < rawDetailsArray.length(); i++) {
                JSONObject rawDetail = rawDetailsArray.getJSONObject(i);
                detailsList.add(TokenDetails.fromJsonObject(rawDetail));
            }
            usageMetadata.setCandidatesTokensDetails(detailsList);
        }
        return usageMetadata;
    }
}
