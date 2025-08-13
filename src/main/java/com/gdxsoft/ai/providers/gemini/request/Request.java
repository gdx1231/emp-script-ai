package com.gdxsoft.ai.providers.gemini.request;

import com.gdxsoft.ai.providers.gemini.response.ApiResponse;
import com.gdxsoft.easyweb.utils.UNet;

/**
 * 请求数据 {<br>
 * "contents": [<br>
 * {<br>
 * "parts": [<br>
 * {<br>
 * "text": "你是谁"<br>
 * }<br>
 * ]<br>
 * }<br>
 * ]<br>
 * }
 */
public class Request {
	public static String END_PONIT = "https://generativelanguage.googleapis.com/v1beta/models/";

	private String endPoint;
	private String key;
	private String model;

	public Request(String endPoint, String key, String model) {
		this.endPoint = endPoint;
		this.key = key;
		this.model = model;
	}

	public ApiResponse doRequstSimple(String user) {
		RequestData data = new RequestData();
		data.userMessage(user);
		return this.doRequest(data);
	}

	public ApiResponse doRequest(RequestData data) {
		UNet net = new UNet();
		String url = this.endPoint + this.model;
		if (key != null && key.length() > 0) {
			url += "?" + key;
		}

		String content = net.doPost(url, data.toJSONObject().toString());

		ApiResponse res = ApiResponse.fromJsonString(content);
		return res;
	}

}
