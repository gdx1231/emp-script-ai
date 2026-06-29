package com.gdxsoft.ai.switchproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.gdxsoft.ai.switchproxy.AccessKeyConfig;
import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.SwitchConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * 状态页面 Handler，提供 HTML 主页。
 * 本地访问时显示管理功能（keys、添加/修改 API）。
 */
public class StatusHandler implements HttpHandler {

	private final SwitchConfig config;

	public StatusHandler(SwitchConfig config) {
		this.config = config;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		boolean isLocal = isLocalRequest(exchange);
		String html = generateHtml(isLocal);
		byte[] body = html.getBytes(StandardCharsets.UTF_8);

		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(200, body.length);

		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body);
		}
	}

	/**
	 * 检测是否本地请求。
	 */
	private boolean isLocalRequest(HttpExchange exchange) {
		InetSocketAddress remoteAddr = exchange.getRemoteAddress();
		if (remoteAddr == null) {
			return false;
		}
		String hostAddress = remoteAddr.getAddress().getHostAddress();
		return "127.0.0.1".equals(hostAddress) || "::1".equals(hostAddress) 
				|| "0:0:0:0:0:0:0:1".equals(hostAddress);
	}

	private String generateHtml(boolean isAdmin) {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>\n");
		html.append("<html lang=\"zh-CN\">\n");
		html.append("<head>\n");
		html.append("  <meta charset=\"UTF-8\">\n");
		html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		html.append("  <title>AI API Switch Proxy</title>\n");
		html.append("  <style>\n");
		html.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; background: #f5f5f5; }\n");
		html.append("    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
		html.append("    h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }\n");
		html.append("    h2 { color: #555; margin-top: 30px; }\n");
		html.append("    table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n");
		html.append("    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }\n");
		html.append("    th { background: #007bff; color: white; }\n");
		html.append("    tr:hover { background: #f8f9fa; }\n");
		html.append("    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; }\n");
		html.append("    .url { color: #007bff; word-break: break-all; }\n");
		html.append("    .badge { display: inline-block; padding: 3px 8px; border-radius: 3px; font-size: 0.8em; font-weight: bold; }\n");
		html.append("    .badge-openai { background: #10a37f; color: white; }\n");
		html.append("    .badge-anthropic { background: #d4a574; color: white; }\n");
		html.append("    .badge-responses { background: #6f42c1; color: white; }\n");
		html.append("    .info { background: #e7f3ff; border-left: 4px solid #2196F3; padding: 15px; margin: 20px 0; }\n");
		html.append("    .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }\n");
		html.append("    .copy-btn { cursor: pointer; background: #6c757d; color: white; border: none; padding: 4px 8px; border-radius: 3px; font-size: 0.8em; }\n");
		html.append("    .copy-btn:hover { background: #5a6268; }\n");
		html.append("    .admin-section { background: #f8f9fa; border: 2px solid #28a745; padding: 20px; margin: 20px 0; border-radius: 5px; }\n");
		html.append("    .admin-section h2 { color: #28a745; margin-top: 0; }\n");
		html.append("    .key-display { font-family: monospace; background: #fff; padding: 8px; border: 1px solid #ddd; border-radius: 3px; word-break: break-all; }\n");
		html.append("    form { margin: 15px 0; }\n");
		html.append("    label { display: block; margin: 10px 0 5px; font-weight: bold; }\n");
		html.append("    input[type=text], input[type=password], select { width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 3px; box-sizing: border-box; }\n");
		html.append("    button[type=submit] { background: #28a745; color: white; border: none; padding: 10px 20px; border-radius: 3px; cursor: pointer; margin-top: 10px; }\n");
		html.append("    button[type=submit]:hover { background: #218838; }\n");
		html.append("    .btn { display: inline-block; padding: 10px 20px; background: #28a745; color: white; text-decoration: none; border-radius: 3px; font-size: 14px; }\n");
		html.append("    .btn:hover { background: #218838; }\n");
		html.append("  </style>\n");
		html.append("</head>\n");
		html.append("<body>\n");
		html.append("  <div class=\"container\">\n");
		html.append("    <h1>🤖 AI API Switch Proxy");
		if (isAdmin) {
			html.append(" <span style=\"color: #28a745; font-size: 0.6em;\">[管理员模式]</span>");
		}
		html.append("</h1>\n");

		// Server info
		String displayHost = getDisplayHost();
		html.append("    <div class=\"info\">\n");
		html.append("      <strong>服务地址:</strong> http://").append(displayHost).append(":").append(config.getPort()).append("<br>\n");
		html.append("      <strong>日志目录:</strong> ").append(config.getLogDir()).append("\n");
		if (isAdmin) {
			html.append("      <br><strong>访问模式:</strong> <span style=\"color: #28a745;\">本地管理（显示敏感信息）</span>\n");
		}
		html.append("    </div>\n");

		// Admin section - Access Keys
		if (isAdmin) {
			html.append("    <div class=\"admin-section\">\n");
			html.append("      <h2>🔑 Access Keys</h2>\n");
			List<AccessKeyConfig> keys = config.getAccessKeys();
			if (keys.isEmpty()) {
				html.append("      <p>暂无 Access Key</p>\n");
			} else {
				html.append("      <table>\n");
				html.append("        <tr><th>Key</th><th>名称</th><th>创建时间</th><th>最后使用</th></tr>\n");
				for (AccessKeyConfig key : keys) {
					html.append("        <tr>\n");
					html.append("          <td class=\"key-display\">").append(escapeHtml(key.getKey())).append("</td>\n");
					html.append("          <td>").append(escapeHtml(key.getName() != null ? key.getName() : "-")).append("</td>\n");
					html.append("          <td>").append(escapeHtml(key.getCreatedAt())).append("</td>\n");
					html.append("          <td>").append(escapeHtml(key.getLastUsedAt() != null ? key.getLastUsedAt() : "从未")).append("</td>\n");
					html.append("        </tr>\n");
				}
				html.append("      </table>\n");
			}
			html.append("      <p style=\"margin-top: 15px;\"><a href=\"/admin\" class=\"btn\">🔧 进入管理面板</a></p>\n");
			html.append("    </div>\n");
		}

		// Profiles table
		html.append("    <h2>📋 可用模型 (Profiles)</h2>\n");
		html.append("    <table>\n");
		html.append("      <tr><th>名称</th><th>模型</th><th>格式</th><th>API URL</th>");
		if (isAdmin) {
			html.append("<th>API Key</th>");
		}
		html.append("</tr>\n");

		Map<String, ProfileConfig> profiles = config.getProfiles();
		for (Map.Entry<String, ProfileConfig> entry : profiles.entrySet()) {
			ProfileConfig p = entry.getValue();
			String format = p.getFormat();
			if (format == null || format.isEmpty()) {
				format = inferFormat(p.getApiUrl());
			}
			String badgeClass = "badge-" + format;

			html.append("      <tr>\n");
			html.append("        <td><strong>").append(escapeHtml(p.getName())).append("</strong></td>\n");
			html.append("        <td><code>").append(escapeHtml(p.getModel() != null ? p.getModel() : "-")).append("</code></td>\n");
			html.append("        <td><span class=\"badge ").append(badgeClass).append("\">").append(format.toUpperCase()).append("</span></td>\n");
			html.append("        <td class=\"url\"><code>").append(escapeHtml(p.getApiUrl())).append("</code></td>\n");
			if (isAdmin) {
				html.append("        <td class=\"key-display\">").append(escapeHtml(p.getApiKey())).append("</td>\n");
			}
			html.append("      </tr>\n");
		}
		html.append("    </table>\n");

		// Admin section - Add Provider
		if (isAdmin) {
			html.append("    <div class=\"admin-section\">\n");
			html.append("      <h2>➕ 添加供应商</h2>\n");
			html.append("      <p><a href=\"/admin/add-provider\" class=\"btn\">📝 使用 Web 界面添加</a></p>\n");
			html.append("      <p style=\"margin-top: 15px;\">或命令行: <code>./bin/start.sh add-provider</code></p>\n");
			html.append("    </div>\n");
		}

		// Routes table
		html.append("    <h2>🔗 调用地址 (Routes)</h2>\n");
		html.append("    <table>\n");
		html.append("      <tr><th>路径</th><th>模式</th><th>Profile</th><th>完整 URL</th><th></th></tr>\n");

		List<RouteConfig> routes = config.getRoutes();
		String baseUrl = "http://" + displayHost + ":" + config.getPort();
		for (RouteConfig route : routes) {
			String fullUrl = baseUrl + route.getPath() + getApiPath(route.getMode());
			String profileName = route.hasProfile() ? route.getProfile() : "inline";

			html.append("      <tr>\n");
			html.append("        <td><code>").append(escapeHtml(route.getPath())).append("</code></td>\n");
			html.append("        <td>").append(escapeHtml(route.getMode())).append("</td>\n");
			html.append("        <td>").append(escapeHtml(profileName)).append("</td>\n");
			html.append("        <td class=\"url\"><code id=\"url-").append(route.getPath().hashCode()).append("\">").append(escapeHtml(fullUrl)).append("</code></td>\n");
			html.append("        <td><button class=\"copy-btn\" onclick=\"copyToClipboard('url-").append(route.getPath().hashCode()).append("')\">复制</button></td>\n");
			html.append("      </tr>\n");
		}
		html.append("    </table>\n");

		// Usage example
		html.append("    <h2>💡 使用示例</h2>\n");
		html.append("    <div class=\"info\">\n");
		html.append("      <strong>curl 命令:</strong><br>\n");
		html.append("      <code>curl -X POST ").append(baseUrl).append(routes.isEmpty() ? "/{provider}/{format}/v1/chat/completions" : routes.get(0).getPath() + "/chat/completions").append(" \\<br>\n");
		html.append("      &nbsp;&nbsp;-H \"Content-Type: application/json\" \\<br>\n");
		html.append("      &nbsp;&nbsp;-d '{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}],\"stream\":true}'</code>\n");
		html.append("    </div>\n");

		// Tool config export section
		html.append("    <h2>📤 工具配置</h2>\n");
		html.append("    <div class=\"info\">\n");
		if (isAdmin) {
			html.append("      <p>生成 Claude Code 和 Codex 的配置文件，可直接导出到本地</p>\n");
			html.append("      <a href=\"/admin/claude-config\" class=\"btn\">🤖 Claude Code 配置</a>\n");
			html.append("      <a href=\"/admin/codex-config\" class=\"btn\">💻 Codex 配置</a>\n");
		} else {
			html.append("      <p>查看 Claude Code 和 Codex 的配置（JSON 格式）</p>\n");
			html.append("      <a href=\"/admin/claude-config\" class=\"btn\">🤖 Claude Code 配置</a>\n");
			html.append("      <a href=\"/admin/codex-config\" class=\"btn\">💻 Codex 配置</a>\n");
		}
		html.append("    </div>\n");

		if (isAdmin) {
			html.append("    <div class=\"warning\">\n");
			html.append("      <strong>⚠️ 安全提示:</strong> 当前为本地管理模式，显示了 API Key 等敏感信息。<br>\n");
			html.append("      从远程访问时，这些信息将被隐藏。\n");
			html.append("    </div>\n");
		}

		html.append("  </div>\n");

		// JavaScript
		html.append("  <script>\n");
		html.append("    function copyToClipboard(elementId) {\n");
		html.append("      const text = document.getElementById(elementId).textContent;\n");
		html.append("      navigator.clipboard.writeText(text).then(() => {\n");
		html.append("        alert('已复制: ' + text);\n");
		html.append("      });\n");
		html.append("    }\n");
		html.append("  </script>\n");
		html.append("</body>\n");
		html.append("</html>\n");

		return html.toString();
	}

	private String getApiPath(String mode) {
		switch (mode) {
			case "chat2anthropic":
				return "/chat/completions";
			case "chat2responses":
				return "/responses";
			default:
				return "/chat/completions";
		}
	}

	private String inferFormat(String apiUrl) {
		if (apiUrl == null) {
			return "openai";
		}
		if (apiUrl.contains("anthropic.com") || apiUrl.contains("/messages")) {
			return "anthropic";
		}
		if (apiUrl.contains("/responses")) {
			return "responses";
		}
		return "openai";
	}

	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	/**
	 * 获取用于显示的 host（将 0.0.0.0 转换为 localhost 或实际主机名）。
	 */
	private String getDisplayHost() {
		String host = config.getHost();
		if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) {
			return "localhost";
		}
		if ("::".equals(host)) {
			return "[::1]";
		}
		// 如果是逗号分隔的多地址，取第一个
		if (host.contains(",")) {
			String first = host.split(",")[0].trim();
			if ("0.0.0.0".equals(first)) {
				return "localhost";
			}
			if ("::".equals(first)) {
				return "[::1]";
			}
			// IPv6 地址加方括号
			if (first.contains(":") && !first.startsWith("[")) {
				return "[" + first + "]";
			}
			return first;
		}
		// IPv6 地址加方括号
		if (host.contains(":") && !host.startsWith("[")) {
			return "[" + host + "]";
		}
		return host;
	}
}
