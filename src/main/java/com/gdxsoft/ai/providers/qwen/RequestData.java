package com.gdxsoft.ai.providers.qwen;

import org.json.JSONObject;

import com.gdxsoft.ai.providers.ProviderType;
import com.gdxsoft.ai.providers.RequestDataBase;

/**
 * 用于构建发送到 Qwen（通义千问）API 的请求体
 */
public class RequestData extends RequestDataBase {
	public static String DEFAULT_MODEL_NAME = "qwen-plus";

	public RequestData() {
		super(DEFAULT_MODEL_NAME); // 默认模型
		this.providerType = ProviderType.QWEN;
	}

	/**
	 * 设置模型是否为深度思考
	 * 
	 * @param thinking
	 * @return
	 */
	@Override
	public RequestData thinking(boolean thinking) {
		parameters.put("thinking", thinking);
		return this;
	}

	/**
	 * 构建最终的请求 JSON 对象 Qwen API 的请求体格式
	 * 
	 * @return 返回构建好的 JSON 对象
	 */
	@Override
	public JSONObject build() {
		JSONObject requestData = new JSONObject(parameters.toString());
		requestData.put("model", this.model);
		requestData.put("messages", messages);
		return requestData;
	}
}