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
	private static final String QWEN_KEY = "sk-b74f3b1805d24fdc82bd4e8fb34313ba";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		  this.doPost(req, resp);
	}
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String prompt = req.getParameter("prompt");
		String model = req.getParameter("model"); // "qwen" or "gemini"

		resp.setContentType("text/event-stream");
		resp.setCharacterEncoding("UTF-8");
		resp.setHeader("Cache-Control", "no-cache");
		resp.setHeader("Connection", "keep-alive");
		resp.setHeader("X-Accel-Buffering", "no"); // Nginx 关闭缓冲
		PrintWriter writer = resp.getWriter();

		try {
			if ("qwen".equalsIgnoreCase(model)) {
				fetchQwenStream(resp, prompt, writer);
			} else {
				writer.write("data: 不支持的模型\n\n");
			}
			writer.write("data: [DONE]\n\n");
		} catch (Exception e) {
			writer.write("data: [ERROR] " + e.getMessage() + "\n\n");
		} finally {
			writer.flush();
		}
	}

	private void fetchQwenStream(HttpServletResponse resp, String prompt, PrintWriter writer) throws IOException {
		URL url = new URL(QWEN_API);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", "Bearer " + QWEN_KEY);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		RequestData reqData = new RequestData();
		reqData.stream(true).model("qwen-plus")
			.systemMessage("你是一个AI助手，回答用户的问题。")
			.userMessage(prompt);

		String jsonInput = reqData.buildJson();
		System.out.println(jsonInput);

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

}
