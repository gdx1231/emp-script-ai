package com.gdxsoft.ai.switchproxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP 访问控制器，支持 IPv4/IPv6 和 CIDR 格式。
 * <p>
 * 示例规则：
 * <ul>
 *   <li>192.168.1.2 — 单个 IPv4</li>
 *   <li>192.168.2.0/24 — IPv4 CIDR</li>
 *   <li>::1 — 单个 IPv6</li>
 *   <li>fe80::/10 — IPv6 CIDR</li>
 *   <li>0.0.0.0/0 — 允许所有 IPv4</li>
 *   <li>::/0 — 允许所有 IPv6</li>
 * </ul>
 */
public class IpAccessController {
	private static final Logger LOGGER = LoggerFactory.getLogger(IpAccessController.class);

	private final List<IpRule> rules = new ArrayList<>();
	private boolean allowAll = false;

	public IpAccessController() {
	}

	/**
	 * 从逗号分隔的字符串解析规则。
	 */
	public static IpAccessController parse(String rulesStr) {
		IpAccessController controller = new IpAccessController();
		if (rulesStr == null || rulesStr.trim().isEmpty()) {
			controller.allowAll = true;
			return controller;
		}

		for (String rule : rulesStr.split(",")) {
			rule = rule.trim();
			if (rule.isEmpty()) {
				continue;
			}
			try {
				controller.addRule(rule);
			} catch (Exception e) {
				LOGGER.warn("无效的 IP 规则: {}, 错误: {}", rule, e.getMessage());
			}
		}

		if (controller.rules.isEmpty()) {
			controller.allowAll = true;
		}
		return controller;
	}

	/**
	 * 添加一条规则（单个 IP 或 CIDR）。
	 */
	public void addRule(String rule) throws UnknownHostException {
		rule = rule.trim();
		if (rule.isEmpty()) {
			return;
		}

		if (rule.contains("/")) {
			// CIDR 格式
			String[] parts = rule.split("/");
			if (parts.length != 2) {
				throw new IllegalArgumentException("无效的 CIDR 格式: " + rule);
			}
			InetAddress network = InetAddress.getByName(parts[0]);
			int prefixLength = Integer.parseInt(parts[1]);
			rules.add(new CidrRule(network, prefixLength));
		} else {
			// 单个 IP
			InetAddress addr = InetAddress.getByName(rule);
			rules.add(new SingleIpRule(addr));
		}
	}

	/**
	 * 检查 IP 是否被允许访问。
	 */
	public boolean isAllowed(String ip) {
		if (allowAll) {
			return true;
		}
		if (ip == null || ip.isEmpty()) {
			return false;
		}

		try {
			InetAddress addr = InetAddress.getByName(ip);
			for (IpRule rule : rules) {
				if (rule.matches(addr)) {
					return true;
				}
			}
		} catch (UnknownHostException e) {
			LOGGER.warn("无法解析 IP 地址: {}", ip);
		}
		return false;
	}

	/**
	 * 是否有规则（false 表示允许所有）。
	 */
	public boolean hasRules() {
		return !allowAll;
	}

	public List<String> getRuleStrings() {
		List<String> result = new ArrayList<>();
		for (IpRule rule : rules) {
			result.add(rule.toString());
		}
		return result;
	}

	// === 内部接口和实现 ===

	private interface IpRule {
		boolean matches(InetAddress addr);
	}

	private static class SingleIpRule implements IpRule {
		private final InetAddress address;

		SingleIpRule(InetAddress address) {
			this.address = address;
		}

		@Override
		public boolean matches(InetAddress addr) {
			return address.equals(addr);
		}

		@Override
		public String toString() {
			return address.getHostAddress();
		}
	}

	private static class CidrRule implements IpRule {
		private final InetAddress network;
		private final int prefixLength;
		private final byte[] networkBytes;
		private final byte[] mask;

		CidrRule(InetAddress network, int prefixLength) {
			this.network = network;
			this.prefixLength = prefixLength;
			this.networkBytes = network.getAddress();

			// 构建掩码
			int addrLen = networkBytes.length; // 4 for IPv4, 16 for IPv6
			if (prefixLength < 0 || prefixLength > addrLen * 8) {
				throw new IllegalArgumentException(
						"无效的前缀长度: " + prefixLength + " (地址长度 " + addrLen * 8 + ")");
			}
			this.mask = new byte[addrLen];
			int fullBytes = prefixLength / 8;
			int remainBits = prefixLength % 8;
			for (int i = 0; i < fullBytes; i++) {
				mask[i] = (byte) 0xFF;
			}
			if (remainBits > 0 && fullBytes < addrLen) {
				mask[fullBytes] = (byte) (0xFF << (8 - remainBits));
			}
		}

		@Override
		public boolean matches(InetAddress addr) {
			byte[] addrBytes = addr.getAddress();
			if (addrBytes.length != networkBytes.length) {
				return false; // IPv4 vs IPv6 不匹配
			}
			for (int i = 0; i < addrBytes.length; i++) {
				if ((addrBytes[i] & mask[i]) != (networkBytes[i] & mask[i])) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return network.getHostAddress() + "/" + prefixLength;
		}
	}
}
