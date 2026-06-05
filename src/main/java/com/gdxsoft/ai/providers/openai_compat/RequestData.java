package com.gdxsoft.ai.providers.openai_compat;

import org.json.JSONObject;

import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestDataBase;

/**
 * 通用的 OpenAI 兼容模式请求体
 * 可指向任意 OpenAI 兼容端点（通过 initUrlAndKey 自定义 URL）
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "";

	public RequestData() {
		super(DEFAULT_MODEL_NAME);
		this.providerType = ProviderType.OPENAI_COMPAT;
	}

	/**
	 * 构建 OpenAI 兼容格式请求体
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		return requestData;
	}
}
