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
import org.json.JSONArray;
import org.json.JSONObject;

import com.gdxsoft.ai.request.IRequestData;
import com.gdxsoft.ai.request.ProviderType;
import com.gdxsoft.ai.request.RequestAIBase;
import com.gdxsoft.easyweb.utils.UJSon;

/**
 * Anthropic AI 请求实现
 * Anthropic 使用自己的 Messages API 格式（非 OpenAI 兼容）
 */
public class RequestAI extends RequestAIBase {
	// Anthropic 的流式 API 网址
	public static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
	// API 版本
	public static final String API_VERSION = "2023-06-01";

	public RequestAI() {
		this.providerType = ProviderType.ANTHROPIC;
	}

	/**
	 * 调用 Anthropic 的流式 API
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
		conn.setRequestProperty("anthropic-version", API_VERSION);
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
	 * 提取 Anthropic 返回的 JSON 数据
	 * SSE 格式: event: content_block_delta
	 *          data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
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

		// 检查是否是结束标记
		if ("[DONE]".equals(jsonData)) {
			return UJSon.rstTrue("流结束标记");
		}

		try {
			JSONObject json = new JSONObject(jsonData);
			String type = json.optString("type", "");

			// content_block_delta: 实际的文本内容
			if ("content_block_delta".equals(type) && json.has("delta")) {
				JSONObject delta = json.getJSONObject("delta");
				if ("text_delta".equals(delta.optString("type")) && delta.has("text")) {
					UJSon.rstSetTrue(delta, null);
					return delta;
				}
			}

			// message_delta: 包含 usage 信息
			if ("message_delta".equals(type) && json.has("usage")) {
				JSONObject usage = json.getJSONObject("usage");
				UJSon.rstSetTrue(usage, null);
				this.setTokensUsage(usage);
				return usage;
			}

			// message_start: 包含初始 usage
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
