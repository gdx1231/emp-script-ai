package com.gdxsoft.ai.providers.gemini.response;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.AiMessageUtils;

import java.util.ArrayList; // 用于实例化 List

/**
 * {<br>
 *   "candidates": [<br>
 *     {<br>
 *       "content": {<br>
 *         "parts": [<br>
 *           {<br>
 *             "text": "我是一个大型语言模型，由 Google 训练。\n"<br>
 *           }<br>
 *         ],<br>
 *         "role": "model"<br>
 *       },<br>
 *       "finishReason": "STOP",<br>
 *       "avgLogprobs": -0.0074767172336578369<br>
 *     }<br>
 *   ],<br>
 *   "usageMetadata": {<br>
 *     "promptTokenCount": 2,<br>
 *     "candidatesTokenCount": 12,<br>
 *     "totalTokenCount": 14,<br>
 *     "promptTokensDetails": [<br>
 *       {<br>
 *         "modality": "TEXT",<br>
 *         "tokenCount": 2<br>
 *       }<br>
 *     ],<br>
 *     "candidatesTokensDetails": [<br>
 *       {<br>
 *         "modality": "TEXT",<br>
 *         "tokenCount": 12<br>
 *       }<br>
 *     ]<br>
 *   },<br>
 *   "modelVersion": "gemini-2.0-flash",<br>
 *   "responseId": "47dYaJS-BfOVsbQPxaKksAc"<br>
 * }
 */
public class ApiResponse {
	private List<Candidate> candidates;
	private UsageMetadata usageMetadata;
	private String modelVersion;
	private String responseId;

	private JSONObject rowJson;

	// 无参构造函数
	public ApiResponse() {
	}

	/**
	 * 只返回响应的正文
	 * @return
	 */
	public String  getResponseText() {

		String text = this.getCandidates().get(0).getContent().getParts().get(0).getText();
		return text;
	}
	/**
	 * 去除正文开始的 ```json 和尾部的 ```
	 * @return 所有代码
	 */
	public List<Map<String, String>>  getResponseCodes() {
		String text = this.getResponseText();
		List<Map<String, String>> codes = AiMessageUtils.extractCodeBlocks(text);
		return codes;
	}
	
	// Getters and Setters
	public List<Candidate> getCandidates() {
		return candidates;
	}

	public void setCandidates(List<Candidate> candidates) {
		this.candidates = candidates;
	}

	public UsageMetadata getUsageMetadata() {
		return usageMetadata;
	}

	public void setUsageMetadata(UsageMetadata usageMetadata) {
		this.usageMetadata = usageMetadata;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public String getResponseId() {
		return responseId;
	}

	public void setResponseId(String responseId) {
		this.responseId = responseId;
	}

	public JSONObject getRowJson() {
		return rowJson;
	}

	public void setRowJson(JSONObject rowJson) {
		this.rowJson = rowJson;
	}

	@Override
	public String toString() {
		return "ApiResponse{" + "candidates=" + candidates + ", usageMetadata=" + usageMetadata + ", modelVersion='"
				+ modelVersion + '\'' + ", responseId='" + responseId + '\'' + '}';
	}

	/**
	 * 静态工厂方法：直接从 JSON 字符串构建 ApiResponse 对象。 使用 org.json 库进行内部解析。
	 * 
	 * @param jsonString 完整的 JSON 字符串
	 * @return 解析后的 ApiResponse 对象
	 * @throws org.json.JSONException 如果 JSON 字符串格式不正确
	 */
	public static ApiResponse fromJsonString(String jsonString) throws org.json.JSONException {
		// 创建一个 JSONObject 来解析传入的 JSON 字符串
		JSONObject rawJsonObject = new JSONObject(jsonString);

		ApiResponse apiResponse = new ApiResponse();

		apiResponse.rowJson = rawJsonObject;

		// 直接从 JSONObject 获取顶层字段
		if (rawJsonObject.has("modelVersion")) {
			apiResponse.setModelVersion(rawJsonObject.getString("modelVersion"));
		}
		if (rawJsonObject.has("responseId")) {
			apiResponse.setResponseId(rawJsonObject.getString("responseId"));
		}

		// 解析 usageMetadata 对象
		if (rawJsonObject.has("usageMetadata")) {
			JSONObject rawUsageMetadata = rawJsonObject.getJSONObject("usageMetadata");
			apiResponse.setUsageMetadata(UsageMetadata.fromJsonObject(rawUsageMetadata));
		}

		// 解析 candidates 数组
		if (rawJsonObject.has("candidates")) {
			JSONArray rawCandidatesArray = rawJsonObject.getJSONArray("candidates");
			List<Candidate> candidatesList = new ArrayList<>();
			for (int i = 0; i < rawCandidatesArray.length(); i++) {
				JSONObject rawCandidate = rawCandidatesArray.getJSONObject(i);
				candidatesList.add(Candidate.fromJsonObject(rawCandidate));
			}
			apiResponse.setCandidates(candidatesList);
		}
		return apiResponse;
	}
}
