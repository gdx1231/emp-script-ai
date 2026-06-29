package com.gdxsoft.ai.switchproxy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * IpAccessController 单元测试。
 */
public class IpAccessControllerTest {

	@Test
	void testSingleIpv4() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("192.168.1.100");

		assertTrue(controller.isAllowed("192.168.1.100"));
		assertFalse(controller.isAllowed("192.168.1.101"));
		assertFalse(controller.isAllowed("10.0.0.1"));
	}

	@Test
	void testIpv4Cidr() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("192.168.1.0/24");

		assertTrue(controller.isAllowed("192.168.1.1"));
		assertTrue(controller.isAllowed("192.168.1.100"));
		assertTrue(controller.isAllowed("192.168.1.255"));
		assertFalse(controller.isAllowed("192.168.2.1"));
		assertFalse(controller.isAllowed("10.0.0.1"));
	}

	@Test
	void testIpv4Cidr16() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("10.0.0.0/8");

		assertTrue(controller.isAllowed("10.0.0.1"));
		assertTrue(controller.isAllowed("10.255.255.255"));
		assertFalse(controller.isAllowed("11.0.0.1"));
		assertFalse(controller.isAllowed("192.168.1.1"));
	}

	@Test
	void testSingleIpv6() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("::1");

		assertTrue(controller.isAllowed("::1"));
		assertFalse(controller.isAllowed("::2"));
		assertFalse(controller.isAllowed("192.168.1.1"));
	}

	@Test
	void testIpv6Cidr() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("fe80::/10");

		assertTrue(controller.isAllowed("fe80::1"));
		assertTrue(controller.isAllowed("fe80::abcd:1234"));
		assertTrue(controller.isAllowed("fe90::1")); // fe90 在 fe80::/10 范围内 (fe80-febf)
		assertTrue(controller.isAllowed("febf::1")); // febf 是 /10 的上界
		assertFalse(controller.isAllowed("fec0::1")); // fec0 超出 fe80::/10
		assertFalse(controller.isAllowed("::1"));
	}

	@Test
	void testAllowAllIpv4() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("0.0.0.0/0");

		assertTrue(controller.isAllowed("192.168.1.1"));
		assertTrue(controller.isAllowed("10.0.0.1"));
		assertTrue(controller.isAllowed("255.255.255.255"));
		// IPv6 不匹配
		assertFalse(controller.isAllowed("::1"));
	}

	@Test
	void testAllowAllIpv6() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("::/0");

		assertTrue(controller.isAllowed("::1"));
		assertTrue(controller.isAllowed("fe80::1"));
		// IPv4 不匹配
		assertFalse(controller.isAllowed("192.168.1.1"));
	}

	@Test
	void testMultipleRules() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("192.168.1.100");
		controller.addRule("10.0.0.0/8");
		controller.addRule("::1");

		assertTrue(controller.isAllowed("192.168.1.100"));
		assertTrue(controller.isAllowed("10.1.2.3"));
		assertTrue(controller.isAllowed("::1"));
		assertFalse(controller.isAllowed("192.168.1.101"));
		assertFalse(controller.isAllowed("172.16.0.1"));
	}

	@Test
	void testParseCommaDelimited() {
		IpAccessController controller = IpAccessController
				.parse("192.168.1.2,192.168.2.0/24,::1,fe80::/10");

		assertTrue(controller.isAllowed("192.168.1.2"));
		assertTrue(controller.isAllowed("192.168.2.50"));
		assertTrue(controller.isAllowed("::1"));
		assertTrue(controller.isAllowed("fe80::1"));
		assertFalse(controller.isAllowed("192.168.3.1"));
		assertFalse(controller.isAllowed("10.0.0.1"));
	}

	@Test
	void testParseEmpty() {
		IpAccessController controller = IpAccessController.parse("");
		assertFalse(controller.hasRules());
		assertTrue(controller.isAllowed("any-ip"));
	}

	@Test
	void testParseNull() {
		IpAccessController controller = IpAccessController.parse(null);
		assertFalse(controller.hasRules());
		assertTrue(controller.isAllowed("any-ip"));
	}

	@Test
	void testParseWithSpaces() {
		IpAccessController controller = IpAccessController.parse(" 192.168.1.1 , 10.0.0.0/8 ");
		assertTrue(controller.isAllowed("192.168.1.1"));
		assertTrue(controller.isAllowed("10.1.2.3"));
	}

	@Test
	void testInvalidRuleIgnored() {
		// 无效规则应被忽略，不影响其他规则
		IpAccessController controller = IpAccessController.parse("192.168.1.1,invalid/999,10.0.0.0/8");
		assertTrue(controller.isAllowed("192.168.1.1"));
		assertTrue(controller.isAllowed("10.1.2.3"));
	}

	@Test
	void testNullIp() {
		IpAccessController controller = IpAccessController.parse("192.168.1.1");
		assertFalse(controller.isAllowed(null));
		assertFalse(controller.isAllowed(""));
	}

	@Test
	void testGetRuleStrings() throws Exception {
		IpAccessController controller = new IpAccessController();
		controller.addRule("192.168.1.0/24");
		controller.addRule("::1");

		var rules = controller.getRuleStrings();
		assertEquals(2, rules.size());
	}
}
