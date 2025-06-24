package com.gdxsoft.ai.providers.gemini.response;

import org.json.JSONObject;


//Candidate 类
public class Candidate {
	private Content content;
	private String finishReason;
	private Double avgLogprobs;

	// 无参构造函数
	public Candidate() {
	}

	// Getters and Setters
	public Content getContent() {
		return content;
	}

	public void setContent(Content content) {
		this.content = content;
	}

	public String getFinishReason() {
		return finishReason;
	}

	public void setFinishReason(String finishReason) {
		this.finishReason = finishReason;
	}

	public Double getAvgLogprobs() {
		return avgLogprobs;
	}

	public void setAvgLogprobs(Double avgLogprobs) {
		this.avgLogprobs = avgLogprobs;
	}

	@Override
	public String toString() {
		return "Candidate{" + "content=" + content + ", finishReason='" + finishReason + '\'' + ", avgLogprobs="
				+ avgLogprobs + '}';
	}
	/**
     * 静态工厂方法：从 JSONObject 构建 Candidate 对象。
     * @param jsonObject org.json.JSONObject 对象
     * @return 解析后的 Candidate 对象
     * @throws org.json.JSONException 如果 JSONObject 缺少必要字段或类型不匹配
     */
    public static Candidate fromJsonObject(JSONObject jsonObject) throws org.json.JSONException {
        Candidate candidate = new Candidate();
        if (jsonObject.has("finishReason")) {
            candidate.setFinishReason(jsonObject.getString("finishReason"));
        }
        if (jsonObject.has("avgLogprobs")) {
            candidate.setAvgLogprobs(jsonObject.getDouble("avgLogprobs"));
        }

        // 解析 Content 对象
        if (jsonObject.has("content")) {
            JSONObject rawContent = jsonObject.getJSONObject("content");
            candidate.setContent(Content.fromJsonObject(rawContent));
        }
        return candidate;
    }
}