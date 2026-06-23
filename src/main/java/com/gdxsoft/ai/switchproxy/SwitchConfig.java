package com.gdxsoft.ai.switchproxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 配置解析，读取 switch.settings.xml。
 */
public class SwitchConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(SwitchConfig.class);

	private static final Path DEFAULT_CONFIG_PATH = Paths.get(System.getProperty("user.home"),
			".emp-script-ai", "switch.settings.xml");

	private String host = "0.0.0.0";
	private int port = 8180;
	private String logDir = "~/.emp-script-ai/logs";
	private String allowIps; // 逗号分隔的 IP/CIDR 规则
	private Map<String, ProfileConfig> profiles = new HashMap<>();
	private List<RouteConfig> routes = new ArrayList<>();
	private List<AccessKeyConfig> accessKeys = new ArrayList<>();

	public static SwitchConfig load() throws IOException {
		return load(DEFAULT_CONFIG_PATH);
	}

	public static SwitchConfig load(Path xmlPath) throws IOException {
		if (!Files.exists(xmlPath)) {
			throw new IOException("配置文件不存在: " + xmlPath);
		}

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(xmlPath.toFile());
			Element root = doc.getDocumentElement();

			SwitchConfig config = new SwitchConfig();

			// 解析 server
			NodeList serverNodes = root.getElementsByTagName("server");
			if (serverNodes.getLength() > 0) {
				Element server = (Element) serverNodes.item(0);
				config.host = server.getAttribute("host");
				if (config.host.isEmpty()) {
					config.host = "0.0.0.0";
				}
				String portStr = server.getAttribute("port");
				if (!portStr.isEmpty()) {
					config.port = Integer.parseInt(portStr);
				}
				// allow-ips
				config.allowIps = server.getAttribute("allow-ips");
				if (config.allowIps.isEmpty()) {
					config.allowIps = null;
				}
			}

			// 解析 log
			NodeList logNodes = root.getElementsByTagName("log");
			if (logNodes.getLength() > 0) {
				Element log = (Element) logNodes.item(0);
				String dir = log.getAttribute("dir");
				if (!dir.isEmpty()) {
					config.logDir = dir;
				}
			}

			// 解析 profiles
			NodeList profileNodes = root.getElementsByTagName("profile");
			for (int i = 0; i < profileNodes.getLength(); i++) {
				Element profileElem = (Element) profileNodes.item(i);
				ProfileConfig profile = new ProfileConfig();
				profile.setName(profileElem.getAttribute("name"));
				profile.setApiUrl(profileElem.getAttribute("api-url"));
				profile.setApiKey(profileElem.getAttribute("api-key"));
				profile.setModel(profileElem.getAttribute("model"));

				String format = profileElem.getAttribute("format");
				if (!format.isEmpty()) {
					profile.setFormat(format);
				}

				String maxTokens = profileElem.getAttribute("max-tokens");
				if (!maxTokens.isEmpty()) {
					profile.setMaxTokens(Integer.parseInt(maxTokens));
				}

				config.profiles.put(profile.getName(), profile);
			}

			// 解析 routes
			NodeList routeNodes = root.getElementsByTagName("route");
			for (int i = 0; i < routeNodes.getLength(); i++) {
				Element routeElem = (Element) routeNodes.item(i);
				RouteConfig route = new RouteConfig();
				route.setPath(routeElem.getAttribute("path"));
				route.setMode(routeElem.getAttribute("mode"));
				route.setProfile(routeElem.getAttribute("profile"));
				route.setTarget(routeElem.getAttribute("target"));
				route.setApiUrl(routeElem.getAttribute("api-url"));
				route.setApiKey(routeElem.getAttribute("api-key"));
				route.setModel(routeElem.getAttribute("model"));

				config.routes.add(route);
			}

			// 解析 access-keys
			NodeList keyNodes = root.getElementsByTagName("access-key");
			for (int i = 0; i < keyNodes.getLength(); i++) {
				Element keyElem = (Element) keyNodes.item(i);
				AccessKeyConfig keyConfig = new AccessKeyConfig();
				keyConfig.setKey(keyElem.getAttribute("key"));
				keyConfig.setName(keyElem.getAttribute("name"));
				keyConfig.setCreatedAt(keyElem.getAttribute("created-at"));
				String lastUsed = keyElem.getAttribute("last-used-at");
				if (!lastUsed.isEmpty()) {
					keyConfig.setLastUsedAt(lastUsed);
				}
				String enabled = keyElem.getAttribute("enabled");
				if (!enabled.isEmpty()) {
					keyConfig.setEnabled(Boolean.parseBoolean(enabled));
				}
				config.accessKeys.add(keyConfig);
			}

			LOGGER.info("加载配置成功: {} profiles, {} routes, {} access-keys",
					config.profiles.size(), config.routes.size(), config.accessKeys.size());
			return config;

		} catch (Exception e) {
			throw new IOException("解析配置文件失败: " + e.getMessage(), e);
		}
	}

	/**
	 * 保存配置到 XML 文件。
	 */
	public void save(Path xmlPath) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.newDocument();

			Element root = doc.createElement("switch");
			doc.appendChild(root);

			// server
			Element server = doc.createElement("server");
			server.setAttribute("host", host);
			server.setAttribute("port", String.valueOf(port));
			if (allowIps != null && !allowIps.isEmpty()) {
				server.setAttribute("allow-ips", allowIps);
			}
			root.appendChild(server);

			// log
			Element log = doc.createElement("log");
			log.setAttribute("dir", logDir);
			root.appendChild(log);

			// profiles
			Element profilesElem = doc.createElement("profiles");
			for (ProfileConfig profile : profiles.values()) {
				Element profileElem = doc.createElement("profile");
				profileElem.setAttribute("name", profile.getName());
				profileElem.setAttribute("api-url", profile.getApiUrl());
				profileElem.setAttribute("api-key", profile.getApiKey());
				profileElem.setAttribute("model", profile.getModel());
				if (profile.getFormat() != null && !profile.getFormat().isEmpty()) {
					profileElem.setAttribute("format", profile.getFormat());
				}
				if (profile.getMaxTokens() != 4096) {
					profileElem.setAttribute("max-tokens", String.valueOf(profile.getMaxTokens()));
				}
				profilesElem.appendChild(profileElem);
			}
			root.appendChild(profilesElem);

			// routes
			Element routesElem = doc.createElement("routes");
			for (RouteConfig route : routes) {
				Element routeElem = doc.createElement("route");
				routeElem.setAttribute("path", route.getPath());
				routeElem.setAttribute("mode", route.getMode());
				if (route.hasProfile()) {
					routeElem.setAttribute("profile", route.getProfile());
				} else {
					if (route.getTarget() != null) {
						routeElem.setAttribute("target", route.getTarget());
					}
					if (route.getApiUrl() != null) {
						routeElem.setAttribute("api-url", route.getApiUrl());
					}
					if (route.getApiKey() != null) {
						routeElem.setAttribute("api-key", route.getApiKey());
					}
					if (route.getModel() != null) {
						routeElem.setAttribute("model", route.getModel());
					}
				}
				routesElem.appendChild(routeElem);
			}
			root.appendChild(routesElem);

			// access-keys
			if (!accessKeys.isEmpty()) {
				Element keysElem = doc.createElement("access-keys");
				for (AccessKeyConfig keyConfig : accessKeys) {
					Element keyElem = doc.createElement("access-key");
					keyElem.setAttribute("key", keyConfig.getKey());
					if (keyConfig.getName() != null) {
						keyElem.setAttribute("name", keyConfig.getName());
					}
					if (keyConfig.getCreatedAt() != null) {
						keyElem.setAttribute("created-at", keyConfig.getCreatedAt());
					}
					if (keyConfig.getLastUsedAt() != null) {
						keyElem.setAttribute("last-used-at", keyConfig.getLastUsedAt());
					}
					keyElem.setAttribute("enabled", String.valueOf(keyConfig.isEnabled()));
					keysElem.appendChild(keyElem);
				}
				root.appendChild(keysElem);
			}

			// 写入文件
			Files.createDirectories(xmlPath.getParent());
			javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
					new javax.xml.transform.dom.DOMSource(doc),
					new javax.xml.transform.stream.StreamResult(xmlPath.toFile()));

			LOGGER.info("保存配置成功: {}", xmlPath);

		} catch (Exception e) {
			throw new IOException("保存配置文件失败: " + e.getMessage(), e);
		}
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getLogDir() {
		return logDir;
	}

	public void setLogDir(String logDir) {
		this.logDir = logDir;
	}

	public Map<String, ProfileConfig> getProfiles() {
		return profiles;
	}

	public ProfileConfig getProfile(String name) {
		return profiles.get(name);
	}

	public void addProfile(ProfileConfig profile) {
		profiles.put(profile.getName(), profile);
	}

	public List<RouteConfig> getRoutes() {
		return routes;
	}

	public void addRoute(RouteConfig route) {
		routes.add(route);
	}

	/**
	 * 解析日志目录路径（展开 ~ 为用户目录）。
	 */
	public Path resolveLogDir() {
		String dir = logDir;
		if (dir.startsWith("~")) {
			dir = System.getProperty("user.home") + dir.substring(1);
		}
		return Paths.get(dir);
	}

	public String getAllowIps() {
		return allowIps;
	}

	public void setAllowIps(String allowIps) {
		this.allowIps = allowIps;
	}

	/**
	 * 构建 IP 访问控制器。
	 */
	public IpAccessController createIpAccessController() {
		return IpAccessController.parse(allowIps);
	}

	public List<AccessKeyConfig> getAccessKeys() {
		return accessKeys;
	}

	public void addAccessKey(AccessKeyConfig key) {
		accessKeys.add(key);
	}

	public boolean removeAccessKey(String key) {
		return accessKeys.removeIf(k -> k.getKey().equals(key));
	}

	/**
	 * 验证 access key 是否有效。返回匹配的 key 配置，null 表示无效。
	 */
	public AccessKeyConfig validateAccessKey(String key) {
		if (key == null || key.isEmpty()) {
			return null;
		}
		for (AccessKeyConfig k : accessKeys) {
			if (k.isEnabled() && k.getKey().equals(key)) {
				return k;
			}
		}
		return null;
	}

	/**
	 * 是否配置了 access key 校验（有 key 时才启用校验）。
	 */
	public boolean hasAccessKeys() {
		return !accessKeys.isEmpty();
	}
}
