package com.gdxsoft.ai.switchproxy.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.gdxsoft.ai.switchproxy.AccessKeyConfig;
import com.gdxsoft.ai.switchproxy.ProfileConfig;
import com.gdxsoft.ai.switchproxy.RouteConfig;
import com.gdxsoft.ai.switchproxy.SwitchConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * 管理界面 Handler，提供 Web UI 添加/修改供应商和模型。
 */
public class AdminHandler implements HttpHandler {

	private static final Path DEFAULT_CONFIG = Paths.get(System.getProperty("user.home"),
			".emp-script-ai", "switch.settings.xml");

	private SwitchConfig config;

	public AdminHandler(SwitchConfig config) {
		this.config = config;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		boolean isLocal = isLocalRequest(exchange);
		String method = exchange.getRequestMethod();
		String path = exchange.getRequestURI().getPath();

		try {
			if ("GET".equals(method)) {
				if (path.equals("/admin")) {
					if (!isLocal) {
						sendError(exchange, 403, "Access denied: admin functions only available from localhost");
						return;
					}
					showAdminPage(exchange);
				} else if (path.equals("/admin/add-provider")) {
					if (!isLocal) {
						sendError(exchange, 403, "Access denied");
						return;
					}
					showAddProviderForm(exchange);
				} else if (path.equals("/admin/edit-provider")) {
					if (!isLocal) {
						sendError(exchange, 403, "Access denied");
						return;
					}
					showEditProviderForm(exchange);
				} else if (path.equals("/admin/add-key")) {
					if (!isLocal) {
						sendError(exchange, 403, "Access denied");
						return;
					}
					showAddKeyForm(exchange);
				} else if (path.equals("/admin/claude-config")) {
					showClaudeConfig(exchange, isLocal);
				} else if (path.equals("/admin/codex-config")) {
					showCodexConfig(exchange, isLocal);
				} else {
					sendError(exchange, 404, "Not found");
				}
			} else if ("POST".equals(method)) {
				if (!isLocal) {
					sendError(exchange, 403, "Access denied");
					return;
				}
				if (path.equals("/admin/add-provider")) {
					handleAddProvider(exchange);
				} else if (path.equals("/admin/edit-provider")) {
					handleEditProvider(exchange);
				} else if (path.equals("/admin/delete-provider")) {
					handleDeleteProvider(exchange);
				} else if (path.equals("/admin/add-key")) {
					handleAddKey(exchange);
				} else if (path.equals("/admin/delete-key")) {
					handleDeleteKey(exchange);
				} else if (path.equals("/admin/export-claude")) {
					handleExportClaude(exchange);
				} else if (path.equals("/admin/export-codex")) {
					handleExportCodex(exchange);
				} else {
					sendError(exchange, 404, "Not found");
				}
			}
		} catch (Exception e) {
			sendError(exchange, 500, "Error: " + e.getMessage());
		}
	}

	private boolean isLocalRequest(HttpExchange exchange) {
		InetSocketAddress remoteAddr = exchange.getRemoteAddress();
		if (remoteAddr == null) {
			return false;
		}
		String hostAddress = remoteAddr.getAddress().getHostAddress();
		return "127.0.0.1".equals(hostAddress) || "::1".equals(hostAddress)
				|| "0:0:0:0:0:0:0:1".equals(hostAddress);
	}

	private void showAdminPage(HttpExchange exchange) throws IOException {
		// 重新加载配置
		try {
			config = SwitchConfig.load();
		} catch (Exception e) {
			// 使用现有配置
		}

		StringBuilder html = new StringBuilder();
		appendHeader(html, "管理面板");

		html.append("<h1>🔧 管理面板</h1>\n");
		html.append("<div class=\"admin-section\">\n");

		// Providers
		html.append("<h2>📋 供应商管理</h2>\n");
		html.append("<a href=\"/admin/add-provider\" class=\"btn\">➕ 添加供应商</a>\n");
		html.append("<table>\n");
		html.append("<tr><th>名称</th><th>模型</th><th>格式</th><th>API URL</th><th>操作</th></tr>\n");

		for (Map.Entry<String, ProfileConfig> entry : config.getProfiles().entrySet()) {
			ProfileConfig p = entry.getValue();
			String format = p.getFormat();
			if (format == null || format.isEmpty()) {
				format = inferFormat(p.getApiUrl());
			}
			html.append("<tr>\n");
			html.append("  <td><strong>").append(escapeHtml(p.getName())).append("</strong></td>\n");
			html.append("  <td><code>").append(escapeHtml(p.getModel() != null ? p.getModel() : "-")).append("</code></td>\n");
			html.append("  <td><span class=\"badge badge-").append(format).append("\">").append(format.toUpperCase()).append("</span></td>\n");
			html.append("  <td class=\"url\"><code>").append(escapeHtml(p.getApiUrl())).append("</code></td>\n");
			html.append("  <td>\n");
			html.append("    <a href=\"/admin/edit-provider?name=").append(escapeHtml(p.getName())).append("\" class=\"btn btn-small\">编辑</a>\n");
			html.append("    <form method=\"POST\" action=\"/admin/delete-provider\" style=\"display:inline;\" onsubmit=\"return confirm('确定删除?');\">\n");
			html.append("      <input type=\"hidden\" name=\"name\" value=\"").append(escapeHtml(p.getName())).append("\">\n");
			html.append("      <button type=\"submit\" class=\"btn btn-small btn-danger\">删除</button>\n");
			html.append("    </form>\n");
			html.append("  </td>\n");
			html.append("</tr>\n");
		}
		html.append("</table>\n");

		// Access Keys
		html.append("<h2 style=\"margin-top: 30px;\">🔑 Access Keys</h2>\n");
		html.append("<a href=\"/admin/add-key\" class=\"btn\">➕ 添加 Key</a>\n");

		List<AccessKeyConfig> keys = config.getAccessKeys();
		if (keys.isEmpty()) {
			html.append("<p>暂无 Access Key</p>\n");
		} else {
			html.append("<table>\n");
			html.append("<tr><th>Key</th><th>名称</th><th>创建时间</th><th>操作</th></tr>\n");
			for (AccessKeyConfig key : keys) {
				html.append("<tr>\n");
				html.append("  <td class=\"key-display\">").append(escapeHtml(key.getKey())).append("</td>\n");
				html.append("  <td>").append(escapeHtml(key.getName() != null ? key.getName() : "-")).append("</td>\n");
				html.append("  <td>").append(escapeHtml(key.getCreatedAt())).append("</td>\n");
				html.append("  <td>\n");
				html.append("    <form method=\"POST\" action=\"/admin/delete-key\" style=\"display:inline;\" onsubmit=\"return confirm('确定删除?');\">\n");
				html.append("      <input type=\"hidden\" name=\"key\" value=\"").append(escapeHtml(key.getKey())).append("\">\n");
				html.append("      <button type=\"submit\" class=\"btn btn-small btn-danger\">删除</button>\n");
				html.append("    </form>\n");
				html.append("  </td>\n");
				html.append("</tr>\n");
			}
			html.append("</table>\n");
		}

		// Claude Code / Codex 配置导出
		html.append("<h2 style=\"margin-top: 30px;\">📤 工具配置导出</h2>\n");
		html.append("<div class=\"export-section\">\n");
		html.append("<p>生成 Claude Code 和 Codex 的配置文件，指向本代理服务器</p>\n");
		html.append("<a href=\"/admin/claude-config\" class=\"btn\">🤖 Claude Code 配置</a>\n");
		html.append("<a href=\"/admin/codex-config\" class=\"btn\">💻 Codex 配置</a>\n");
		html.append("</div>\n");

		html.append("</div>\n");
		html.append("<p><a href=\"/\">← 返回首页</a></p>\n");
		appendFooter(html);

		sendHtml(exchange, html.toString());
	}

	private void showAddProviderForm(HttpExchange exchange) throws IOException {
		StringBuilder html = new StringBuilder();
		appendHeader(html, "添加供应商");

		html.append("<h1>➕ 添加供应商</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<form method=\"POST\" action=\"/admin/add-provider\">\n");

		html.append("<label>名称 *</label>\n");
		html.append("<input type=\"text\" name=\"name\" required placeholder=\"如: qwen, claude, codex\">\n");

		html.append("<label>API URL *</label>\n");
		html.append("<input type=\"text\" name=\"apiUrl\" required placeholder=\"https://api.openai.com/v1/chat/completions\">\n");
		html.append("<small>常用: OpenAI=https://api.openai.com/v1/chat/completions, Anthropic=https://api.anthropic.com/v1/messages</small>\n");

		html.append("<label>API Key *</label>\n");
		html.append("<input type=\"text\" name=\"apiKey\" required placeholder=\"sk-xxx\">\n");

		html.append("<label>格式</label>\n");
		html.append("<select name=\"format\">\n");
		html.append("  <option value=\"openai\">OpenAI</option>\n");
		html.append("  <option value=\"anthropic\">Anthropic</option>\n");
		html.append("  <option value=\"responses\">Responses API</option>\n");
		html.append("</select>\n");

		html.append("<label>默认模型</label>\n");
		html.append("<input type=\"text\" name=\"model\" placeholder=\"如: gpt-4o, claude-sonnet-4-20250514\">\n");

		html.append("<button type=\"submit\" class=\"btn\">添加</button>\n");
		html.append("<a href=\"/admin\" class=\"btn btn-secondary\">取消</a>\n");

		html.append("</form>\n");
		html.append("</div>\n");

		appendFooter(html);
		sendHtml(exchange, html.toString());
	}

	private void showEditProviderForm(HttpExchange exchange) throws IOException {
		String name = getQueryParam(exchange, "name");
		ProfileConfig profile = config.getProfile(name);

		if (profile == null) {
			sendError(exchange, 404, "Provider not found: " + name);
			return;
		}

		String currentFormat = profile.getFormat();
		if (currentFormat == null || currentFormat.isEmpty()) {
			currentFormat = inferFormat(profile.getApiUrl());
		}

		StringBuilder html = new StringBuilder();
		appendHeader(html, "编辑供应商");

		html.append("<h1>✏️ 编辑供应商: ").append(escapeHtml(name)).append("</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<form method=\"POST\" action=\"/admin/edit-provider\">\n");
		html.append("<input type=\"hidden\" name=\"originalName\" value=\"").append(escapeHtml(name)).append("\">\n");

		html.append("<label>名称</label>\n");
		html.append("<input type=\"text\" name=\"name\" value=\"").append(escapeHtml(profile.getName())).append("\" required>\n");

		html.append("<label>API URL</label>\n");
		html.append("<input type=\"text\" name=\"apiUrl\" value=\"").append(escapeHtml(profile.getApiUrl())).append("\" required>\n");

		html.append("<label>API Key</label>\n");
		html.append("<input type=\"text\" name=\"apiKey\" value=\"").append(escapeHtml(profile.getApiKey())).append("\" required>\n");

		html.append("<label>格式</label>\n");
		html.append("<select name=\"format\">\n");
		html.append("  <option value=\"openai\"").append("openai".equals(currentFormat) ? " selected" : "").append(">OpenAI</option>\n");
		html.append("  <option value=\"anthropic\"").append("anthropic".equals(currentFormat) ? " selected" : "").append(">Anthropic</option>\n");
		html.append("  <option value=\"responses\"").append("responses".equals(currentFormat) ? " selected" : "").append(">Responses API</option>\n");
		html.append("</select>\n");
		html.append("<small>⚠️ 修改格式会改变路由路径和代理模式</small>\n");

		html.append("<label>默认模型</label>\n");
		html.append("<input type=\"text\" name=\"model\" value=\"").append(escapeHtml(profile.getModel() != null ? profile.getModel() : "")).append("\">\n");

		html.append("<button type=\"submit\" class=\"btn\">保存</button>\n");
		html.append("<a href=\"/admin\" class=\"btn btn-secondary\">取消</a>\n");

		html.append("</form>\n");
		html.append("</div>\n");

		appendFooter(html);
		sendHtml(exchange, html.toString());
	}

	private void showAddKeyForm(HttpExchange exchange) throws IOException {
		StringBuilder html = new StringBuilder();
		appendHeader(html, "添加 Access Key");

		html.append("<h1>➕ 添加 Access Key</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<form method=\"POST\" action=\"/admin/add-key\">\n");

		html.append("<label>名称（可选）</label>\n");
		html.append("<input type=\"text\" name=\"name\" placeholder=\"如: dev-key, prod-key\">\n");

		html.append("<button type=\"submit\" class=\"btn\">生成 Key</button>\n");
		html.append("<a href=\"/admin\" class=\"btn btn-secondary\">取消</a>\n");

		html.append("</form>\n");
		html.append("</div>\n");

		appendFooter(html);
		sendHtml(exchange, html.toString());
	}

	private void handleAddProvider(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);

		String name = params.get("name");
		String apiUrl = params.get("apiUrl");
		String apiKey = params.get("apiKey");
		String format = params.getOrDefault("format", "openai");
		String model = params.get("model");

		if (name == null || apiUrl == null || apiKey == null) {
			sendError(exchange, 400, "Missing required fields");
			return;
		}

		// 添加 profile
		ProfileConfig profile = new ProfileConfig(name, apiUrl, apiKey, model.isEmpty() ? null : model, format);
		config.addProfile(profile);

		// 添加 route
		String mode = inferMode(format);
		RouteConfig route = new RouteConfig();
		route.setPath("/" + name + "/" + format + "/v1");
		route.setMode(mode);
		route.setProfile(name);
		config.addRoute(route);

		config.save(DEFAULT_CONFIG);

		// 重定向到管理页面
		exchange.getResponseHeaders().set("Location", "/admin");
		exchange.sendResponseHeaders(302, -1);
	}

	private void handleEditProvider(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);

		String originalName = params.get("originalName");
		String newName = params.get("name");
		String apiUrl = params.get("apiUrl");
		String apiKey = params.get("apiKey");
		String format = params.getOrDefault("format", "openai");
		String model = params.get("model");

		ProfileConfig profile = config.getProfile(originalName);
		if (profile == null) {
			sendError(exchange, 404, "Provider not found");
			return;
		}

		// 检查是否有变化
		String oldFormat = profile.getFormat();
		if (oldFormat == null || oldFormat.isEmpty()) {
			oldFormat = inferFormat(profile.getApiUrl());
		}
		boolean nameChanged = !originalName.equals(newName);
		boolean formatChanged = !oldFormat.equals(format);

		// 更新 profile
		profile.setApiUrl(apiUrl);
		profile.setApiKey(apiKey);
		profile.setModel(model.isEmpty() ? null : model);
		profile.setFormat(format);

		// 如果名称或格式改变，需要更新 route
		if (nameChanged || formatChanged) {
			// 删除旧路由
			config.getRoutes().removeIf(r -> originalName.equals(r.getProfile()));

			// 创建新路由
			String mode = inferMode(format);
			RouteConfig route = new RouteConfig();
			route.setPath("/" + newName + "/" + format + "/v1");
			route.setMode(mode);
			route.setProfile(newName);
			config.addRoute(route);

			// 更新 profile 名称
			if (nameChanged) {
				profile.setName(newName);
				config.getProfiles().remove(originalName);
				config.getProfiles().put(newName, profile);
			}
		}

		config.save(DEFAULT_CONFIG);
		exchange.getResponseHeaders().set("Location", "/admin");
		exchange.sendResponseHeaders(302, -1);
	}

	private void handleDeleteProvider(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);
		String name = params.get("name");

		config.getProfiles().remove(name);
		config.getRoutes().removeIf(r -> name.equals(r.getProfile()));
		config.save(DEFAULT_CONFIG);

		exchange.getResponseHeaders().set("Location", "/admin");
		exchange.sendResponseHeaders(302, -1);
	}

	private void handleAddKey(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);
		String name = params.get("name");

		AccessKeyConfig keyConfig = new AccessKeyConfig(name.isEmpty() ? null : name);
		config.addAccessKey(keyConfig);
		config.save(DEFAULT_CONFIG);

		exchange.getResponseHeaders().set("Location", "/admin");
		exchange.sendResponseHeaders(302, -1);
	}

	private void handleDeleteKey(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);
		String key = params.get("key");

		config.removeAccessKey(key);
		config.save(DEFAULT_CONFIG);

		exchange.getResponseHeaders().set("Location", "/admin");
		exchange.sendResponseHeaders(302, -1);
	}

	// === 辅助方法 ===

	private Map<String, String> parseFormParams(HttpExchange exchange) throws IOException {
		Map<String, String> params = new HashMap<>();
		try (InputStream is = exchange.getRequestBody()) {
			String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			for (String pair : body.split("&")) {
				String[] kv = pair.split("=", 2);
				if (kv.length == 2) {
					params.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
				}
			}
		}
		return params;
	}

	private String getQueryParam(HttpExchange exchange, String name) {
		String query = exchange.getRequestURI().getQuery();
		if (query == null) {
			return null;
		}
		for (String pair : query.split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv.length == 2 && kv[0].equals(name)) {
				try {
					return URLDecoder.decode(kv[1], "UTF-8");
				} catch (Exception e) {
					return null;
				}
			}
		}
		return null;
	}

	private void sendHtml(HttpExchange exchange, String html) throws IOException {
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body);
		}
	}

	private void sendError(HttpExchange exchange, int code, String message) throws IOException {
		String html = "<html><body><h1>Error " + code + "</h1><p>" + escapeHtml(message) + "</p><a href=\"/admin\">返回</a></body></html>";
		byte[] body = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(code, body.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(body);
		}
	}

	private void appendHeader(StringBuilder html, String title) {
		html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
		html.append("<meta charset=\"UTF-8\">\n");
		html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		html.append("<title>").append(escapeHtml(title)).append(" - AI API Switch</title>\n");
		html.append("<style>\n");
		html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; background: #f5f5f5; }\n");
		html.append(".container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
		html.append("h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }\n");
		html.append("h2 { color: #555; margin-top: 30px; }\n");
		html.append("table { width: 100%; border-collapse: collapse; margin-top: 15px; }\n");
		html.append("th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }\n");
		html.append("th { background: #007bff; color: white; }\n");
		html.append("tr:hover { background: #f8f9fa; }\n");
		html.append("code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; }\n");
		html.append(".url { color: #007bff; word-break: break-all; }\n");
		html.append(".badge { display: inline-block; padding: 3px 8px; border-radius: 3px; font-size: 0.8em; font-weight: bold; }\n");
		html.append(".badge-openai { background: #10a37f; color: white; }\n");
		html.append(".badge-anthropic { background: #d4a574; color: white; }\n");
		html.append(".badge-responses { background: #6f42c1; color: white; }\n");
		html.append(".admin-section { background: #f8f9fa; border: 2px solid #28a745; padding: 20px; margin: 20px 0; border-radius: 5px; }\n");
		html.append(".key-display { font-family: monospace; background: #fff; padding: 8px; border: 1px solid #ddd; border-radius: 3px; word-break: break-all; font-size: 0.85em; }\n");
		html.append("label { display: block; margin: 15px 0 5px; font-weight: bold; }\n");
		html.append("input[type=text], input[type=password], select { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 3px; box-sizing: border-box; font-size: 14px; }\n");
		html.append("small { color: #666; display: block; margin: 5px 0 10px; }\n");
		html.append(".btn { display: inline-block; padding: 10px 20px; background: #28a745; color: white; text-decoration: none; border-radius: 3px; border: none; cursor: pointer; font-size: 14px; margin: 5px 5px 5px 0; }\n");
		html.append(".btn:hover { background: #218838; }\n");
		html.append(".btn-secondary { background: #6c757d; }\n");
		html.append(".btn-secondary:hover { background: #5a6268; }\n");
		html.append(".btn-small { padding: 5px 10px; font-size: 12px; }\n");
		html.append(".btn-danger { background: #dc3545; }\n");
		html.append(".btn-danger:hover { background: #c82333; }\n");
		html.append("form { margin: 0; }\n");
		html.append(".config-json { background: #1e1e1e; color: #d4d4d4; padding: 20px; border-radius: 5px; overflow-x: auto; font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; }\n");
		html.append(".warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }\n");
		html.append("h3 { color: #333; margin-top: 20px; }\n");
		html.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");
	}

	private void appendFooter(StringBuilder html) {
		html.append("</div>\n</body>\n</html>\n");
	}

	private String inferMode(String format) {
		switch (format) {
			case "anthropic":
				return "chat2anthropic";
			case "responses":
				return "chat2responses";
			default:
				return "passthrough";
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

	// === Claude Code / Codex 配置导出 ===

	private void showClaudeConfig(HttpExchange exchange, boolean isLocal) throws IOException {
		String selectedProfile = getQueryParam(exchange, "profile");
		String selectedKey = getQueryParam(exchange, "key");

		// 如果没有选择，显示选择界面
		if (selectedProfile == null || selectedKey == null) {
			showConfigSelection(exchange, "claude", isLocal);
			return;
		}

		ProfileConfig profile = config.getProfile(selectedProfile);
		AccessKeyConfig accessKey = findKeyByKey(selectedKey);

		if (profile == null || accessKey == null) {
			sendError(exchange, 404, "未找到选择的供应商或 Key");
			return;
		}

		JSONObject configJson = generateClaudeConfig(profile, accessKey);
		String json = configJson.toString(2);

		StringBuilder html = new StringBuilder();
		appendHeader(html, "Claude Code 配置");

		html.append("<h1>🤖 Claude Code 配置</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<p>供应商: <strong>").append(escapeHtml(profile.getName())).append("</strong> | Key: <strong>")
				.append(escapeHtml(accessKey.getName() != null ? accessKey.getName() : accessKey.getKey().substring(0, 20) + "...")).append("</strong></p>\n");

		html.append("<h3>配置文件内容</h3>\n");
		html.append("<pre class=\"config-json\">").append(escapeHtml(json)).append("</pre>\n");

		if (isLocal) {
			html.append("<form method=\"POST\" action=\"/admin/export-claude\">\n");
			html.append("<input type=\"hidden\" name=\"profile\" value=\"").append(escapeHtml(selectedProfile)).append("\">\n");
			html.append("<input type=\"hidden\" name=\"key\" value=\"").append(escapeHtml(selectedKey)).append("\">\n");
			html.append("<button type=\"submit\" class=\"btn\" onclick=\"return confirm('确定覆盖本地 Claude Code 配置?');\">📥 导出到本地</button>\n");
			html.append("</form>\n");
			html.append("<p><small>将保存到 ~/.claude/settings.json</small></p>\n");
		} else {
			html.append("<div class=\"warning\">\n");
			html.append("<strong>⚠️ 远程访问:</strong> 请手动复制上述 JSON 内容到本地配置文件\n");
			html.append("</div>\n");
		}

		html.append("<p><a href=\"/admin/claude-config\">← 重新选择</a> | <a href=\"/admin\">返回管理面板</a></p>\n");
		html.append("</div>\n");
		appendFooter(html);

		sendHtml(exchange, html.toString());
	}

	private void showCodexConfig(HttpExchange exchange, boolean isLocal) throws IOException {
		String selectedProfile = getQueryParam(exchange, "profile");
		String selectedKey = getQueryParam(exchange, "key");

		// 如果没有选择，显示选择界面
		if (selectedProfile == null || selectedKey == null) {
			showConfigSelection(exchange, "codex", isLocal);
			return;
		}

		ProfileConfig profile = config.getProfile(selectedProfile);
		AccessKeyConfig accessKey = findKeyByKey(selectedKey);

		if (profile == null || accessKey == null) {
			sendError(exchange, 404, "未找到选择的供应商或 Key");
			return;
		}

		JSONObject configJson = generateCodexConfig(profile, accessKey);
		String json = configJson.toString(2);

		StringBuilder html = new StringBuilder();
		appendHeader(html, "Codex 配置");

		html.append("<h1>💻 Codex 配置</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<p>供应商: <strong>").append(escapeHtml(profile.getName())).append("</strong> | Key: <strong>")
				.append(escapeHtml(accessKey.getName() != null ? accessKey.getName() : accessKey.getKey().substring(0, 20) + "...")).append("</strong></p>\n");

		html.append("<h3>配置文件内容</h3>\n");
		html.append("<pre class=\"config-json\">").append(escapeHtml(json)).append("</pre>\n");

		if (isLocal) {
			html.append("<form method=\"POST\" action=\"/admin/export-codex\">\n");
			html.append("<input type=\"hidden\" name=\"profile\" value=\"").append(escapeHtml(selectedProfile)).append("\">\n");
			html.append("<input type=\"hidden\" name=\"key\" value=\"").append(escapeHtml(selectedKey)).append("\">\n");
			html.append("<button type=\"submit\" class=\"btn\" onclick=\"return confirm('确定覆盖本地 Codex 配置?');\">📥 导出到本地</button>\n");
			html.append("</form>\n");
			html.append("<p><small>将保存到 ~/.codex/config.json</small></p>\n");
		} else {
			html.append("<div class=\"warning\">\n");
			html.append("<strong>⚠️ 远程访问:</strong> 请手动复制上述 JSON 内容到本地配置文件\n");
			html.append("</div>\n");
		}

		html.append("<p><a href=\"/admin/codex-config\">← 重新选择</a> | <a href=\"/admin\">返回管理面板</a></p>\n");
		html.append("</div>\n");
		appendFooter(html);

		sendHtml(exchange, html.toString());
	}

	private void showConfigSelection(HttpExchange exchange, String type, boolean isLocal) throws IOException {
		StringBuilder html = new StringBuilder();
		appendHeader(html, type.equals("claude") ? "选择 Claude Code 配置" : "选择 Codex 配置");

		html.append("<h1>").append(type.equals("claude") ? "🤖 选择 Claude Code 配置" : "💻 选择 Codex 配置").append("</h1>\n");
		html.append("<div class=\"admin-section\">\n");
		html.append("<form method=\"GET\" action=\"/admin/").append(type).append("-config\">\n");

		// 选择供应商
		html.append("<label>选择供应商</label>\n");
		html.append("<select name=\"profile\" required>\n");
		html.append("<option value=\"\">-- 请选择 --</option>\n");
		for (ProfileConfig p : config.getProfiles().values()) {
			String format = p.getFormat();
			if (format == null || format.isEmpty()) {
				format = inferFormat(p.getApiUrl());
			}
			// Claude 只显示 anthropic 格式
			if (type.equals("claude") && !"anthropic".equals(format)) {
				continue;
			}
			// Codex 显示 openai/responses/anthropic 格式（anthropic 会通过 responses2anthropic 转换）
			if (type.equals("codex") && !"openai".equals(format) && !"responses".equals(format) && !"anthropic".equals(format)) {
				continue;
			}
			html.append("<option value=\"").append(escapeHtml(p.getName())).append("\">")
					.append(escapeHtml(p.getName())).append(" (").append(format).append(")")
					.append("</option>\n");
		}
		html.append("</select>\n");

		// 选择 Access Key
		html.append("<label>选择 Access Key</label>\n");
		html.append("<select name=\"key\" required>\n");
		html.append("<option value=\"\">-- 请选择 --</option>\n");
		if (config.getAccessKeys().isEmpty()) {
			html.append("<option value=\"\" disabled>暂无 Access Key，请先添加</option>\n");
		} else {
			for (AccessKeyConfig k : config.getAccessKeys()) {
				String displayName = k.getName() != null ? k.getName() : k.getKey().substring(0, 20) + "...";
				html.append("<option value=\"").append(escapeHtml(k.getKey())).append("\">")
						.append(escapeHtml(displayName)).append("</option>\n");
			}
		}
		html.append("</select>\n");

		if (config.getAccessKeys().isEmpty()) {
			html.append("<p class=\"warning\"><strong>⚠️</strong> 暂无 Access Key，请先到 <a href=\"/admin\">管理面板</a> 添加</p>\n");
		}

		html.append("<button type=\"submit\" class=\"btn\">生成配置</button>\n");
		html.append("<a href=\"/\" class=\"btn btn-secondary\">取消</a>\n");

		html.append("</form>\n");
		html.append("</div>\n");
		appendFooter(html);

		sendHtml(exchange, html.toString());
	}

	private void handleExportClaude(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);
		String profileName = params.get("profile");
		String keyValue = params.get("key");

		ProfileConfig profile = config.getProfile(profileName);
		AccessKeyConfig accessKey = findKeyByKey(keyValue);

		if (profile == null || accessKey == null) {
			sendError(exchange, 404, "未找到选择的供应商或 Key");
			return;
		}

		JSONObject configJson = generateClaudeConfig(profile, accessKey);
		Path configPath = Paths.get(System.getProperty("user.home"), ".claude", "settings.json");

		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, configJson.toString(2));
			exchange.getResponseHeaders().set("Location", "/admin?exported=claude");
			exchange.sendResponseHeaders(302, -1);
		} catch (Exception e) {
			sendError(exchange, 500, "导出失败: " + e.getMessage());
		}
	}

	private void handleExportCodex(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseFormParams(exchange);
		String profileName = params.get("profile");
		String keyValue = params.get("key");

		ProfileConfig profile = config.getProfile(profileName);
		AccessKeyConfig accessKey = findKeyByKey(keyValue);

		if (profile == null || accessKey == null) {
			sendError(exchange, 404, "未找到选择的供应商或 Key");
			return;
		}

		JSONObject configJson = generateCodexConfig(profile, accessKey);
		Path configPath = Paths.get(System.getProperty("user.home"), ".codex", "config.json");

		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, configJson.toString(2));
			exchange.getResponseHeaders().set("Location", "/admin?exported=codex");
			exchange.sendResponseHeaders(302, -1);
		} catch (Exception e) {
			sendError(exchange, 500, "导出失败: " + e.getMessage());
		}
	}

	private AccessKeyConfig findKeyByKey(String key) {
		for (AccessKeyConfig k : config.getAccessKeys()) {
			if (k.getKey().equals(key)) {
				return k;
			}
		}
		return null;
	}

	private JSONObject generateClaudeConfig(ProfileConfig profile, AccessKeyConfig accessKey) {
		JSONObject config = new JSONObject();
		JSONObject env = new JSONObject();

		String baseUrl = "http://localhost:" + this.config.getPort() + "/" + profile.getName() + "/anthropic/v1";
		env.put("ANTHROPIC_BASE_URL", baseUrl);
		env.put("ANTHROPIC_AUTH_TOKEN", accessKey.getKey()); // 使用 Access Key
		env.put("ANTHROPIC_MODEL", profile.getModel() != null ? profile.getModel() : "claude-sonnet-4-20250514");
		env.put("ANTHROPIC_DEFAULT_SONNET_MODEL", profile.getModel() != null ? profile.getModel() : "claude-sonnet-4-20250514");
		env.put("ANTHROPIC_DEFAULT_OPUS_MODEL", profile.getModel() != null ? profile.getModel() : "claude-sonnet-4-20250514");
		env.put("ANTHROPIC_DEFAULT_HAIKU_MODEL", profile.getModel() != null ? profile.getModel() : "claude-sonnet-4-20250514");
		env.put("CLAUDE_CODE_SUBAGENT_MODEL", profile.getModel() != null ? profile.getModel() : "claude-sonnet-4-20250514");
		env.put("API_TIMEOUT_MS", "3000000");
		env.put("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1");
		env.put("CLAUDE_CODE_SIMPLE", "1");

		config.put("env", env);
		config.put("skipDangerousModePermissionPrompt", true);

		return config;
	}

	private JSONObject generateCodexConfig(ProfileConfig profile, AccessKeyConfig accessKey) {
		JSONObject config = new JSONObject();
		JSONObject env = new JSONObject();

		String format = profile.getFormat();
		if (format == null || format.isEmpty()) {
			format = inferFormat(profile.getApiUrl());
		}

		// 根据目标格式确定路由路径和模式
		String routePath;
		if ("anthropic".equals(format)) {
			// 对于 anthropic 格式，使用 responses2anthropic 模式
			// 路由路径格式：/{provider}/responses/v1
			routePath = "/" + profile.getName() + "/responses/v1";
		} else {
			// 对于 openai/responses 格式，使用 passthrough 或 chat2responses
			routePath = "/" + profile.getName() + "/" + format + "/v1";
		}

		String baseUrl = "http://localhost:" + this.config.getPort() + routePath;

		env.put("OPENAI_BASE_URL", baseUrl);
		env.put("OPENAI_API_KEY", accessKey.getKey()); // 使用 Access Key
		env.put("OPENAI_MODEL", profile.getModel() != null ? profile.getModel() : "gpt-4o");
		env.put("CODEX_MODEL", profile.getModel() != null ? profile.getModel() : "gpt-4o");
		env.put("API_TIMEOUT_MS", "3000000");

		config.put("env", env);
		config.put("skipDangerousModePermissionPrompt", true);

		return config;
	}
}
