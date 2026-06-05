package com.gdxsoft.ai.providers.anthropic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Anthropic OpenAI 兼容模式 AI 请求实现
 * 使用 OpenAI 兼容的 SSE 格式（event: message_delta / data: {...} 格式与原生 Anthropic 相同）
 * 但请求体使用 OpenAI 格式（messages 数组包含 system 角色）
 */
public class RequestAIOpenAICompat extends RequestAIBase {
	// Anthropic OpenAI 兼容 API 网址
	public static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";

	public RequestAIOpenAICompat() {
		this.providerType = ProviderType.ANTHROPIC;
	}

	/**
	 * 调用 Anthropic 的流式 API（OpenAI 兼容格式）
	 * 请求体使用 OpenAI 格式，但认证头仍然使用 Anthropic 的 x-api-key
	 */
	@Override
	public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException {
		String u = super.getApiUrl();
		if (StringUtils.isBlank(u)) {
			u = DEFAULT_URL;
		}
		URI url = new URI(u);
		HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
		conn.setRequestMethod("POST");
		if (super.getApiKey() != null && !super.getApiKey().isEmpty()) {
			conn.setRequestProperty("x-api-key", super.getApiKey());
		}
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "text/event-stream");
		conn.setRequestProperty("anthropic-version", "2023-06-01");
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
	 * 复用原生 Anthropic 的 extraceJson 逻辑（SSE 格式相同）
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
			String type = json.optString("type", "");

			if ("content_block_delta".equals(type) && json.has("delta")) {
				JSONObject delta = json.getJSONObject("delta");
				if ("text_delta".equals(delta.optString("type")) && delta.has("text")) {
					UJSon.rstSetTrue(delta, null);
					return delta;
				}
			}

			if ("message_delta".equals(type) && json.has("usage")) {
				JSONObject usage = json.getJSONObject("usage");
				UJSon.rstSetTrue(usage, null);
				this.setTokensUsage(usage);
				return usage;
			}

			if ("message_start".equals(type) && json.has("message")) {
				JSONObject message = json.getJSONObject("message");
				if (message.has("usage")) {
					JSONObject usage = message.getJSONObject("usage");
					UJSon.rstSetTrue(usage, null);
					this.setTokensUsage(usage);
				}
			}

			return UJSon.rstFalse("未处理的事件类型: " + type);
		} catch (Exception e) {
			return UJSon.rstFalse("无效 JSON，" + line + ", 错误：" + e.getMessage());
		}
	}
}
