package com.gdxsoft.ai.providers.openai_compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * 通用的 OpenAI 兼容模式 AI 请求实现
 * 可指向任意 OpenAI 兼容端点（通过 initUrlAndKey 自定义 URL）
 * 使用 Bearer Token 认证，SSE 格式与 OpenAI 一致
 */
public class RequestAI extends RequestAIBase {

	public RequestAI() {
		this.providerType = ProviderType.OPENAI_COMPAT;
	}

	/**
	 * 调用 OpenAI 兼容端点的流式 API
	 */
	@Override
	public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException {
		String u = super.getApiUrl();
		if (StringUtils.isBlank(u)) {
			throw new IOException("OpenAI 兼容模式必须通过 initUrlAndKey(url, key) 指定 API 地址");
		}
		URI url = new URI(u);
		HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
		conn.setRequestMethod("POST");
		if (super.getApiKey() != null && !super.getApiKey().isEmpty()) {
			conn.setRequestProperty("Authorization", "Bearer " + super.getApiKey());
		}
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "text/event-stream");
		conn.setDoOutput(true);

		String jsonInput = reqData.buildJson();

		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null) {
				this.handleLine(line, writer);
			}
		}
		return super.getFullText().toString();
	}

	/**
	 * 提取 OpenAI 兼容格式的 JSON 数据
	 */
	@Override
	public JSONObject extraceJson(String line, boolean skipDataPrefix) {
		String jsonData = null;
		if (!skipDataPrefix) {
			if (!line.startsWith("data:")) {
				return UJSon.rstFalse("没有data:的数据行，" + line);
			}
			jsonData = line.substring(5).trim();
		} else {
			jsonData = line.trim();
		}
		if (jsonData.isEmpty()) {
			return UJSon.rstFalse("data:无数据，" + line);
		}

		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
		}

		try {
			JSONObject json = new JSONObject(jsonData);
			JSONArray choices = json.getJSONArray("choices");
			if (choices.length() > 0) {
				JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
				UJSon.rstSetTrue(delta, null);
				return delta;
			}
			return UJSon.rstFalse("无效数据，choices.length() = 0" + line);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
