package com.gdxsoft.ai.providers.anthropic_compat;

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
 * 通用的 Anthropic 兼容模式 AI 请求实现
 * 可指向任意 Anthropic 格式端点（通过 initUrlAndKey 自定义 URL）
 * 使用 x-api-key + anthropic-version 认证，SSE 格式与 Anthropic 一致
 */
public class RequestAI extends RequestAIBase {
	// API 版本
	public static final String API_VERSION = "2023-06-01";

	public RequestAI() {
		this.providerType = ProviderType.ANTHROPIC_COMPAT;
	}

	/**
	 * 调用 Anthropic 兼容端点的流式 API
	 */
	@Override
	public String doStream(IRequestData reqData, PrintWriter writer) throws IOException, URISyntaxException {
		String u = super.getApiUrl();
		if (StringUtils.isBlank(u)) {
			throw new IOException("Anthropic 兼容模式必须通过 initUrlAndKey(url, key) 指定 API 地址");
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
	 * 提取 Anthropic 兼容格式的 JSON 数据
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
