package com.gdxsoft.ai.servlets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gdxsoft.ai.providers.qwen.request.RequestData;

public class StreamServlet extends HttpServlet {
	private static final long serialVersionUID = -6656872942956615438L;

	private static final String QWEN_API = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
	private static final String OPENAI_API = "https://api.openai.com/v1/chat/completions";
	private static final String GROK_API = "https://api.x.ai/v1/chat/completions";
	private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String prompt = req.getParameter("prompt");
		String provider = req.getParameter("provider");
		if (provider == null || provider.isEmpty()) {
			// 兼容旧参数：使用 model 作为提供商标识（qwen/openai/grok）
			provider = req.getParameter("model");
		}
		String modelName = req.getParameter("modelName"); // 可选：覆盖具体模型名

		resp.setContentType("text/event-stream");
		resp.setCharacterEncoding("UTF-8");
		resp.setHeader("Cache-Control", "no-cache");
		resp.setHeader("Connection", "keep-alive");
		resp.setHeader("X-Accel-Buffering", "no"); // Nginx 关闭缓冲
		PrintWriter writer = resp.getWriter();

		try {
			if ("qwen".equalsIgnoreCase(provider)) {
				fetchQwenStream(resp, prompt, modelName, writer);
			} else if ("openai".equalsIgnoreCase(provider)) {
				fetchOpenAIStream(resp, prompt, modelName, writer);
			} else if ("grok".equalsIgnoreCase(provider)) {
				fetchGrokStream(resp, prompt, modelName, writer);
			} else if ("gemini".equalsIgnoreCase(provider)) {
				fetchGeminiStream(resp, prompt, modelName, writer);
			} else {
				writer.write("data: 不支持的模型或provider\n\n");
			}
			writer.write("data: [DONE]\n\n");
		} catch (Exception e) {
			writer.write("data: [ERROR] " + e.getMessage() + "\n\n");
		} finally {
			writer.flush();
		}
	}

	private void fetchQwenStream(HttpServletResponse resp, String prompt, String modelName, PrintWriter writer) throws IOException {
		URL url = new URL(QWEN_API);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		String apiKey = System.getenv("QWEN_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new IOException("缺少 QWEN_API_KEY 环境变量");
		}
		conn.setRequestProperty("Authorization", "Bearer " + apiKey);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		RequestData reqData = new RequestData();
		reqData.stream(true)
			.model(modelName != null && !modelName.isEmpty() ? modelName : "qwen-plus")
			.systemMessage("你是一个AI助手，回答用户的问题。")
			.userMessage(prompt);

		String jsonInput = reqData.buildJson();
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null && !resp.isCommitted()) {
				writer.write(line + "\n\n");
				writer.flush();
			}
		}
	}

	private void fetchOpenAIStream(HttpServletResponse resp, String prompt, String modelName, PrintWriter writer) throws IOException {
		URL url = new URL(OPENAI_API);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new IOException("缺少 OPENAI_API_KEY 环境变量");
		}
		conn.setRequestProperty("Authorization", "Bearer " + apiKey);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		com.gdxsoft.ai.providers.openai.request.RequestData reqData = new com.gdxsoft.ai.providers.openai.request.RequestData();
		reqData.stream(true)
			.model(modelName != null && !modelName.isEmpty() ? modelName : "gpt-4o-mini")
			.systemMessage("You are a helpful assistant.")
			.userMessage(prompt);

		String jsonInput = reqData.buildJson();
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null && !resp.isCommitted()) {
				writer.write(line + "\n\n");
				writer.flush();
			}
		}
	}

	private void fetchGrokStream(HttpServletResponse resp, String prompt, String modelName, PrintWriter writer) throws IOException {
		URL url = new URL(GROK_API);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		String apiKey = System.getenv("GROK_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new IOException("缺少 GROK_API_KEY 环境变量");
		}
		conn.setRequestProperty("Authorization", "Bearer " + apiKey);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		com.gdxsoft.ai.providers.grok.request.RequestData reqData = new com.gdxsoft.ai.providers.grok.request.RequestData();
		reqData.stream(true)
			.model(modelName != null && !modelName.isEmpty() ? modelName : "grok-2")
			.systemMessage("You are a helpful assistant.")
			.userMessage(prompt);

		String jsonInput = reqData.buildJson();
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null && !resp.isCommitted()) {
				writer.write(line + "\n\n");
				writer.flush();
			}
		}
	}

	private void fetchGeminiStream(HttpServletResponse resp, String prompt, String modelName, PrintWriter writer) throws IOException {
		String apiKey = System.getenv("GEMINI_API_KEY");
		if (apiKey == null || apiKey.isEmpty()) {
			throw new IOException("缺少 GEMINI_API_KEY 环境变量");
		}
		String model = (modelName != null && !modelName.isEmpty()) ? modelName : "gemini-1.5-flash";
		// 使用 Gemini 流式接口
		URL url = new URL(GEMINI_API_BASE + model + ":streamGenerateContent?key=" + apiKey);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		// 构建 Gemini 请求体
		com.gdxsoft.ai.providers.gemini.request.Part part = new com.gdxsoft.ai.providers.gemini.request.Part();
		part.setText(prompt);
		java.util.List<com.gdxsoft.ai.providers.gemini.request.Part> parts = new java.util.ArrayList<>();
		parts.add(part);
		com.gdxsoft.ai.providers.gemini.request.Content content = new com.gdxsoft.ai.providers.gemini.request.Content();
		content.setParts(parts);
		java.util.List<com.gdxsoft.ai.providers.gemini.request.Content> contents = new java.util.ArrayList<>();
		contents.add(content);
		com.gdxsoft.ai.providers.gemini.request.RequestData reqData = new com.gdxsoft.ai.providers.gemini.request.RequestData();
		reqData.setContents(contents);

		String jsonInput = reqData.toJSONObject().toString();
		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = jsonInput.getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
			String line;
			while ((line = br.readLine()) != null && !resp.isCommitted()) {
				// Gemini 的流返回可能不是以 data: 前缀，我们直接透传；前端按行处理
				writer.write(line + "\n\n");
				writer.flush();
			}
		}
	}
}
