package com.gdxsoft.ai.switchproxy;

import java.io.Console;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;

/**
 * CLI 子命令入口。
 */
public class SwitchCli {

	private static final Path DEFAULT_CONFIG = Paths.get(System.getProperty("user.home"),
			".emp-script-ai", "switch.settings.xml");

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			printUsage();
			return;
		}

		String command = args[0];
		switch (command) {
			case "start":
				cmdStart();
				break;
			case "add-provider":
				cmdAddProvider(args);
				break;
			case "add-model":
				cmdAddModel(args);
				break;
			case "use-model":
				cmdUseModel(args);
				break;
			case "list":
				cmdList();
				break;
			case "add-key":
				cmdAddKey(args);
				break;
			case "list-keys":
				cmdListKeys();
				break;
			case "remove-key":
				cmdRemoveKey(args);
				break;
			case "allow-ip":
				cmdAllowIp(args);
				break;
			default:
				System.err.println("未知命令: " + command);
				printUsage();
		}
	}

	private static void cmdStart() throws Exception {
		SwitchConfig config = SwitchConfig.load();
		SwitchServer server = new SwitchServer(config);
		server.start();
		// 阻塞主线程
		Thread.currentThread().join();
	}

	private static void cmdAddProvider(String[] args) throws Exception {
		String name = null, apiUrl = null, apiKey = null, format = null, model = null;

		// 解析命令行参数
		for (int i = 1; i < args.length; i++) {
			switch (args[i]) {
				case "--name":
					name = args[++i];
					break;
				case "--api-url":
					apiUrl = args[++i];
					break;
				case "--api-key":
					apiKey = args[++i];
					break;
				case "--format":
					format = args[++i];
					break;
				case "--model":
					model = args[++i];
					break;
			}
		}

		// 缺少必填参数时进入交互模式
		if (name == null || apiUrl == null || apiKey == null) {
			System.out.println("=== 添加供应商（交互模式）===");
			System.out.println("提示: 也可通过命令行参数直接指定，如 --name qwen --api-url ... --api-key ...");
			System.out.println();

			Scanner scanner = new Scanner(System.in);
			Console console = System.console();

			if (name == null) {
				name = prompt(scanner, "供应商名称 (如 qwen, claude, codex)");
				if (name == null || name.isEmpty()) {
					System.err.println("名称不能为空");
					return;
				}
			}

			if (apiUrl == null) {
				System.out.println("常用 API 地址:");
				System.out.println("  OpenAI:    https://api.openai.com/v1/chat/completions");
				System.out.println("  Anthropic: https://api.anthropic.com/v1/messages");
				System.out.println("  Qwen:      https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
				System.out.println("  Responses: https://api.openai.com/v1/responses");
				apiUrl = prompt(scanner, "API 地址");
				if (apiUrl == null || apiUrl.isEmpty()) {
					System.err.println("API 地址不能为空");
					return;
				}
			}

			if (apiKey == null) {
				if (console != null) {
					// 有 console 时用 readPassword 隐藏输入
					char[] password = console.readPassword("API Key (输入不回显): ");
					apiKey = password != null ? new String(password) : null;
				} else {
					apiKey = prompt(scanner, "API Key");
				}
				if (apiKey == null || apiKey.isEmpty()) {
					System.err.println("API Key 不能为空");
					return;
				}
			}

			if (format == null) {
				String inferred = inferFormat(apiUrl);
				format = prompt(scanner, "目标格式 (openai/anthropic/responses) [" + inferred + "]");
				if (format == null || format.isEmpty()) {
					format = inferred;
				}
			}

			if (model == null) {
				model = prompt(scanner, "默认模型 (可选，直接回车跳过)");
			}
		}

		SwitchConfig config = loadOrCreateConfig();

		// 添加 profile
		ProfileConfig profile = new ProfileConfig(name, apiUrl, apiKey, model != null && !model.isEmpty() ? model : null, format);

		// Anthropic 需要 max-tokens
		if ("anthropic".equals(format) || inferFormat(apiUrl).equals("anthropic")) {
			profile.setMaxTokens(4096);
		}

		config.addProfile(profile);

		// 自动生成路由
		String mode = inferMode(format);
		RouteConfig route = new RouteConfig();
		route.setPath("/" + name + "/" + format + "/v1");
		route.setMode(mode);
		route.setProfile(name);
		config.addRoute(route);

		config.save(DEFAULT_CONFIG);
		System.out.println();
		System.out.println("✓ 已添加 provider: " + name);
		System.out.println("  路由: " + route.getPath() + " → " + mode);
		System.out.println("  模型: " + (model != null ? model : "(使用客户端指定的模型)"));
		System.out.println("  配置: " + DEFAULT_CONFIG);
	}

	private static String prompt(Scanner scanner, String message) {
		System.out.print(message + ": ");
		System.out.flush();
		if (scanner.hasNextLine()) {
			return scanner.nextLine().trim();
		}
		return null;
	}

	private static void cmdAddModel(String[] args) throws Exception {
		String provider = null, model = null, apiUrl = null;

		for (int i = 1; i < args.length; i++) {
			switch (args[i]) {
				case "--provider":
					provider = args[++i];
					break;
				case "--model":
					model = args[++i];
					break;
				case "--api-url":
					apiUrl = args[++i];
					break;
			}
		}

		if (provider == null || model == null) {
			System.err.println("缺少必填参数: --provider, --model");
			return;
		}

		SwitchConfig config = SwitchConfig.load();
		ProfileConfig profile = config.getProfile(provider);
		if (profile == null) {
			System.err.println("provider 不存在: " + provider);
			return;
		}

		// 更新 model
		profile.setModel(model);
		if (apiUrl != null) {
			profile.setApiUrl(apiUrl);
		}

		config.save(DEFAULT_CONFIG);
		System.out.println("已更新 " + provider + " 的模型为: " + model);
	}

	private static void cmdUseModel(String[] args) throws Exception {
		String provider = null, model = null;

		for (int i = 1; i < args.length; i++) {
			switch (args[i]) {
				case "--provider":
					provider = args[++i];
					break;
				case "--model":
					model = args[++i];
					break;
			}
		}

		if (provider == null || model == null) {
			System.err.println("缺少必填参数: --provider, --model");
			return;
		}

		SwitchConfig config = SwitchConfig.load();
		ProfileConfig profile = config.getProfile(provider);
		if (profile == null) {
			System.err.println("provider 不存在: " + provider);
			return;
		}

		profile.setModel(model);
		config.save(DEFAULT_CONFIG);
		System.out.println("已切换 " + provider + " 的模型为: " + model);
	}

	private static void cmdList() throws Exception {
		SwitchConfig config;
		try {
			config = SwitchConfig.load();
		} catch (Exception e) {
			System.out.println("配置文件不存在或格式错误");
			return;
		}

		System.out.println("Profiles:");
		for (Map.Entry<String, ProfileConfig> entry : config.getProfiles().entrySet()) {
			ProfileConfig p = entry.getValue();
			String format = inferFormat(p.getApiUrl());
			System.out.printf("  %-12s model=%-20s format=%-12s url=%s%n",
					p.getName(), p.getModel(), format, p.getApiUrl());
		}

		System.out.println();
		System.out.println("Routes:");
		for (RouteConfig route : config.getRoutes()) {
			System.out.printf("  %-25s → %-16s profile=%s%n",
					route.getPath(), route.getMode(),
					route.hasProfile() ? route.getProfile() : "inline");
		}
	}

	private static String inferMode(String format) {
		switch (format) {
			case "anthropic":
				return "chat2anthropic";
			case "responses":
				return "chat2responses";
			default:
				return "passthrough";
		}
	}

	private static String inferModeForClient(String clientFormat, String targetFormat) {
		// 根据客户端格式和目标格式推断 mode
		if ("responses".equals(clientFormat)) {
			// 客户端使用 Responses API 格式
			if ("anthropic".equals(targetFormat)) {
				return "responses2anthropic";
			} else if ("responses".equals(targetFormat)) {
				return "passthrough";
			} else {
				return "passthrough"; // openai 格式暂不支持从 responses 转换
			}
		} else if ("chat".equals(clientFormat)) {
			// 客户端使用 Chat Completions 格式
			if ("anthropic".equals(targetFormat)) {
				return "chat2anthropic";
			} else if ("responses".equals(targetFormat)) {
				return "chat2responses";
			} else {
				return "passthrough";
			}
		}
		return "passthrough";
	}

	private static String inferFormat(String apiUrl) {
		if (apiUrl == null) {
			return "unknown";
		}
		if (apiUrl.contains("anthropic.com") || apiUrl.contains("/messages")) {
			return "anthropic";
		}
		if (apiUrl.contains("/responses")) {
			return "responses";
		}
		return "openai";
	}

	private static SwitchConfig loadOrCreateConfig() {
		try {
			return SwitchConfig.load();
		} catch (Exception e) {
			return new SwitchConfig();
		}
	}

	// === Access Key 管理 ===

	private static void cmdAddKey(String[] args) throws Exception {
		String name = null;
		for (int i = 1; i < args.length; i++) {
			if ("--name".equals(args[i]) && i + 1 < args.length) {
				name = args[++i];
			}
		}

		// 交互模式
		if (name == null) {
			Scanner scanner = new Scanner(System.in);
			name = prompt(scanner, "Key 描述 (可选，直接回车跳过)");
		}

		SwitchConfig config = loadOrCreateConfig();
		AccessKeyConfig keyConfig = new AccessKeyConfig(name != null && !name.isEmpty() ? name : null);
		config.addAccessKey(keyConfig);
		config.save(DEFAULT_CONFIG);

		System.out.println();
		System.out.println("✓ 已生成 Access Key:");
		System.out.println("  Key:  " + keyConfig.getKey());
		if (keyConfig.getName() != null) {
			System.out.println("  名称: " + keyConfig.getName());
		}
		System.out.println("  创建: " + keyConfig.getCreatedAt());
		System.out.println();
		System.out.println("使用方式:");
		System.out.println("  Header:  X-Access-Key: " + keyConfig.getKey());
		System.out.println("  Header:  Authorization: Bearer " + keyConfig.getKey());
	}

	private static void cmdListKeys() throws Exception {
		SwitchConfig config;
		try {
			config = SwitchConfig.load();
		} catch (Exception e) {
			System.out.println("配置文件不存在");
			return;
		}

		if (config.getAccessKeys().isEmpty()) {
			System.out.println("暂无 Access Key");
			System.out.println("使用 add-key 命令创建");
			return;
		}

		System.out.println("Access Keys:");
		for (AccessKeyConfig key : config.getAccessKeys()) {
			String status = key.isEnabled() ? "✓" : "✗";
			System.out.printf("  %s %s%n", status, key.getKey());
			if (key.getName() != null) {
				System.out.printf("      名称: %s%n", key.getName());
			}
			System.out.printf("      创建: %s%n", key.getCreatedAt());
			if (key.getLastUsedAt() != null) {
				System.out.printf("      最后使用: %s%n", key.getLastUsedAt());
			}
		}
	}

	private static void cmdRemoveKey(String[] args) throws Exception {
		String keyValue = null;
		for (int i = 1; i < args.length; i++) {
			if ("--key".equals(args[i]) && i + 1 < args.length) {
				keyValue = args[++i];
			}
		}

		// 交互模式
		if (keyValue == null) {
			SwitchConfig config = loadOrCreateConfig();
			if (config.getAccessKeys().isEmpty()) {
				System.out.println("暂无 Access Key");
				return;
			}

			System.out.println("当前 Access Keys:");
			int idx = 1;
			for (AccessKeyConfig key : config.getAccessKeys()) {
				String name = key.getName() != null ? " (" + key.getName() + ")" : "";
				System.out.printf("  %d. %s%s%n", idx++, key.getKey(), name);
			}

			Scanner scanner = new Scanner(System.in);
			String input = prompt(scanner, "输入序号删除 (直接回车取消)");
			if (input == null || input.isEmpty()) {
				return;
			}
			try {
				int i = Integer.parseInt(input);
				if (i < 1 || i > config.getAccessKeys().size()) {
					System.err.println("无效序号");
					return;
				}
				keyValue = config.getAccessKeys().get(i - 1).getKey();
			} catch (NumberFormatException e) {
				// 可能是直接输入了 key
				keyValue = input;
			}
		}

		SwitchConfig config = SwitchConfig.load();
		if (config.removeAccessKey(keyValue)) {
			config.save(DEFAULT_CONFIG);
			System.out.println("✓ 已删除 Access Key");
		} else {
			System.err.println("未找到该 Key");
		}
	}

	// === IP 访问控制 ===

	private static void cmdAllowIp(String[] args) throws Exception {
		String ips = null;
		for (int i = 1; i < args.length; i++) {
			if ("--ips".equals(args[i]) && i + 1 < args.length) {
				ips = args[++i];
			}
		}

		// 交互模式
		if (ips == null) {
			SwitchConfig config = loadOrCreateConfig();
			String current = config.getAllowIps();

			System.out.println("=== IP 访问控制 ===");
			if (current != null && !current.isEmpty()) {
				System.out.println("当前规则: " + current);
			} else {
				System.out.println("当前: 无限制（允许所有 IP）");
			}
			System.out.println();
			System.out.println("格式: 逗号分隔，支持 IPv4/IPv6 和 CIDR");
			System.out.println("示例: 192.168.1.2,10.0.0.0/8,::1,fe80::/10");
			System.out.println("      0.0.0.0/0 表示允许所有 IPv4");
			System.out.println("      留空表示清除限制");

			Scanner scanner = new Scanner(System.in);
			ips = prompt(scanner, "IP 规则");
		}

		SwitchConfig config = loadOrCreateConfig();
		if (ips == null || ips.trim().isEmpty()) {
			config.setAllowIps(null);
			System.out.println("✓ 已清除 IP 限制");
		} else {
			config.setAllowIps(ips.trim());
			System.out.println("✓ 已设置 IP 规则: " + ips.trim());

			// 验证规则
			IpAccessController controller = IpAccessController.parse(ips.trim());
			System.out.println("  解析结果:");
			for (String rule : controller.getRuleStrings()) {
				System.out.println("    " + rule);
			}
		}
		config.save(DEFAULT_CONFIG);
	}

	private static void printUsage() {
		System.out.println("用法: SwitchCli <command> [options]");
		System.out.println();
		System.out.println("命令:");
		System.out.println("  start                          启动代理服务");
		System.out.println("  add-provider                   添加供应商（支持交互模式）");
		System.out.println("    --name <name>                  供应商名称");
		System.out.println("    --api-url <url>                API 地址");
		System.out.println("    --api-key <key>                API 密钥");
		System.out.println("    --format <format>              目标格式 (openai/anthropic/responses)");
		System.out.println("    --model <model>                默认模型");
		System.out.println("  add-model                      添加/切换模型");
		System.out.println("    --provider <name>              供应商名称");
		System.out.println("    --model <model>                模型名称");
		System.out.println("    --api-url <url>                (可选) 覆盖 API 地址");
		System.out.println("  use-model                      切换当前模型");
		System.out.println("    --provider <name>              供应商名称");
		System.out.println("    --model <model>                模型名称");
		System.out.println("  list                           列出所有供应商和路由");
		System.out.println();
		System.out.println("  访问控制:");
		System.out.println("  add-key                        生成 Access Key (emai-switch-uuid)");
		System.out.println("    --name <name>                  (可选) Key 描述");
		System.out.println("  list-keys                      列出所有 Access Key");
		System.out.println("  remove-key                     删除 Access Key");
		System.out.println("    --key <key>                    要删除的 Key");
		System.out.println("  allow-ip                       设置 IP 访问规则（支持交互模式）");
		System.out.println("    --ips <rules>                  逗号分隔: IP/CIDR，如 192.168.1.2,10.0.0.0/8,::1");
	}
}
