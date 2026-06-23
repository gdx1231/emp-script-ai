package com.gdxsoft.ai.switchproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.ai.switchproxy.handler.AdminHandler;
import com.gdxsoft.ai.switchproxy.handler.Chat2AnthropicHandler;
import com.gdxsoft.ai.switchproxy.handler.Chat2ResponsesHandler;
import com.gdxsoft.ai.switchproxy.handler.PassthroughHandler;
import com.gdxsoft.ai.switchproxy.handler.ProxyHandler;
import com.gdxsoft.ai.switchproxy.handler.Responses2AnthropicHandler;
import com.gdxsoft.ai.switchproxy.handler.StatusHandler;
import com.gdxsoft.ai.switchproxy.logger.RequestLogger;
import com.sun.net.httpserver.HttpServer;

/**
 * JDK HttpServer 启动/停止入口。
 * <p>
 * 支持多地址监听（IPv4/IPv6），host 配置为逗号分隔的地址列表。
 * 每个地址创建独立的 HttpServer 实例，共享路由和线程池。
 */
public class SwitchServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SwitchServer.class);

	private final SwitchConfig config;
	private final List<HttpServer> servers = new ArrayList<>();
	private ExecutorService executor;

	public SwitchServer(SwitchConfig config) {
		this.config = config;
	}

	public void start() throws IOException {
		Path logDir = config.resolveLogDir();
		RequestLogger requestLogger = new RequestLogger(logDir);

		// 解析多地址
		List<String> hosts = parseHosts(config.getHost());
		int port = config.getPort();

		executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

		for (String host : hosts) {
			HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);

			// 注册状态页面（根路径）
			server.createContext("/", new StatusHandler(config));

			// 注册管理界面（仅本地访问）
			server.createContext("/admin", new AdminHandler(config));

			// 注册路由
			for (RouteConfig route : config.getRoutes()) {
				ProfileConfig profile = route.hasProfile() ? config.getProfile(route.getProfile()) : null;

				// 内联配置：创建临时 profile
				if (profile == null && !route.hasProfile()) {
					profile = new ProfileConfig();
					profile.setName(route.getTarget() != null ? route.getTarget() : "inline");
					profile.setApiUrl(route.getApiUrl());
					profile.setApiKey(route.getApiKey());
					profile.setModel(route.getModel());
				}

				if (profile == null) {
					LOGGER.warn("路由 {} 引用了不存在的 profile: {}，跳过", route.getPath(), route.getProfile());
					continue;
				}

				ProxyHandler handler = createHandler(route, profile, requestLogger);
				if (handler != null) {
					server.createContext(route.getPath(), handler);
				}
			}

			server.setExecutor(executor);
			server.start();
			servers.add(server);

			LOGGER.info("监听: http://[{}]:{}", host, port);
		}

		// 打印路由注册信息（只打一次）
		for (RouteConfig route : config.getRoutes()) {
			LOGGER.info("路由: {} → {} (profile={})", route.getPath(), route.getMode(),
					route.hasProfile() ? route.getProfile() : "inline");
		}

		LOGGER.info("日志目录: {}", logDir);
		LOGGER.info("代理服务启动完成，共 {} 个监听地址", servers.size());

		// 注册关闭钩子
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("正在关闭代理服务...");
			stop();
		}));
	}

	public void stop() {
		for (HttpServer server : servers) {
			try {
				server.stop(2);
			} catch (Exception e) {
				LOGGER.warn("关闭 server 异常: {}", e.getMessage());
			}
		}
		servers.clear();

		if (executor != null) {
			executor.shutdown();
		}
		LOGGER.info("代理服务已关闭");
	}

	/**
	 * 解析 host 配置，支持逗号分隔的多地址（IPv4/IPv6）。
	 * <p>
	 * 示例：
	 * <ul>
	 *   <li>0.0.0.0 → 单个 IPv4</li>
	 *   <li>0.0.0.0,:: → 双栈</li>
	 *   <li>192.168.1.2,::1,fe80::1 → 多地址</li>
	 * </ul>
	 */
	static List<String> parseHosts(String hostConfig) {
		List<String> hosts = new ArrayList<>();
		if (hostConfig == null || hostConfig.trim().isEmpty()) {
			hosts.add("0.0.0.0");
			return hosts;
		}

		for (String host : hostConfig.split(",")) {
			host = host.trim();
			// 去掉 IPv6 的方括号
			if (host.startsWith("[") && host.endsWith("]")) {
				host = host.substring(1, host.length() - 1);
			}
			if (!host.isEmpty()) {
				hosts.add(host);
			}
		}

		if (hosts.isEmpty()) {
			hosts.add("0.0.0.0");
		}
		return hosts;
	}

	private ProxyHandler createHandler(RouteConfig route, ProfileConfig profile, RequestLogger requestLogger)
			throws IOException {
		String mode = route.getMode();
		switch (mode) {
			case "passthrough":
				return new PassthroughHandler(route, profile, requestLogger, config);
			case "chat2anthropic":
				return new Chat2AnthropicHandler(route, profile, requestLogger, config);
			case "chat2responses":
				return new Chat2ResponsesHandler(route, profile, requestLogger, config);
			case "responses2anthropic":
				return new Responses2AnthropicHandler(route, profile, requestLogger, config);
			default:
				LOGGER.warn("未知的 mode: {}，路由 {} 跳过", mode, route.getPath());
				return null;
		}
	}

	public static void main(String[] args) throws Exception {
		SwitchConfig config = SwitchConfig.load();
		SwitchServer server = new SwitchServer(config);
		server.start();
	}
}
