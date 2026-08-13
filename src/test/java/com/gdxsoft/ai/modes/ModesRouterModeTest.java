package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 测试 <routerMode> 路由声明的解析与缓存：
 * 显式声明 mode=auto 可路由的候选 mode 集合，并支持 default 兜底。
 */
class ModesRouterModeTest {

	private static final String XML = "<modes>"
			+ "<routerMode name='auto' default='chat'>"
			+ "<route>chat</route>"
			+ "<route>translate</route>"
			+ "<reminder><![CDATA[抱歉，请换个说法描述需求。]]></reminder>"
			+ "</routerMode>"
			+ "<mode name='chat' description='通用闲聊'/>"
			+ "<mode name='translate' description='翻译'/>"
			+ "<mode name='helper' description='内部辅助'/>"
			+ "</modes>";

	@Test
	void testParseAndGetRouterMode() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		RouterMode rm = Modes.getRouterMode("auto");
		assertNotNull(rm);
		assertEquals("chat", rm.getDefaultMode());
		assertEquals(2, rm.getRoutes().size());
		assertTrue(rm.getRoutes().contains("chat"));
		assertTrue(rm.getRoutes().contains("translate"));
		// helper 未被声明，不参与路由
		assertFalse(rm.getRoutes().contains("helper"));
		assertEquals("抱歉，请换个说法描述需求。", rm.getReminder());
	}

	@Test
	void testReminderOptional() throws Exception {
		Modes modes = new Modes();
		modes.loadModes("<modes><routerMode name='r2'><route>chat</route></routerMode></modes>");
		RouterMode rm = Modes.getRouterMode("r2");
		assertNotNull(rm);
		assertNull(rm.getReminder());
		assertNull(rm.getDefaultMode());
	}

	@Test
	void testReminderApiAndCommonApis() throws Exception {
		String xml = "<modes>"
				+ "<common><apis>"
				+ "<api name='fallbackTip' url='https://example.com/tip?uid=@uid' method='GET'/>"
				+ "</apis></common>"
				+ "<routerMode name='auto_api' default='chat'>"
				+ "<route>chat</route>"
				+ "<reminder api='fallbackTip'>抱歉 @uid</reminder>"
				+ "</routerMode>"
				+ "<mode name='chat' description='x'/>"
				+ "</modes>";
		Modes modes = new Modes();
		modes.loadModes(xml);

		RouterMode rm = Modes.getRouterMode("auto_api");
		assertNotNull(rm);
		assertEquals("fallbackTip", rm.getReminderApi());
		assertEquals("抱歉 @uid", rm.getReminder());
		Api api = rm.getApi("fallbackTip");
		assertNotNull(api);
		assertEquals("https://example.com/tip?uid=@uid", api.getUrl());
		// 忽略大小写查找
		assertNotNull(rm.getApi("FALLBACKTIP"));
	}

	@Test
	void testReminderToolAlias() throws Exception {
		String xml = "<modes>"
				+ "<common><tools>"
				+ "<tool name='t1' command='echo hi'/>"
				+ "</tools></common>"
				+ "<routerMode name='auto_tool'>"
				+ "<route>chat</route>"
				+ "<reminder tool='t1'>hi</reminder>"
				+ "</routerMode>"
				+ "<mode name='chat' description='x'/>"
				+ "</modes>";
		Modes modes = new Modes();
		modes.loadModes(xml);

		RouterMode rm = Modes.getRouterMode("auto_tool");
		assertNotNull(rm);
		assertEquals("t1", rm.getReminderApi());
		Api api = rm.getApi("t1");
		assertTrue(api instanceof Tool);
		assertEquals("echo hi", ((Tool) api).getCommand());
	}

	@Test
	void testGetRouterModeCaseInsensitive() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		assertNotNull(Modes.getRouterMode("Auto"));
		assertNotNull(Modes.getRouterMode("AUTO"));
	}

	@Test
	void testGetRouterModeReturnsClone() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		RouterMode rm = Modes.getRouterMode("auto");
		rm.getRoutes().clear();
		// 修改返回对象不影响缓存原件
		assertEquals(2, Modes.getRouterMode("auto").getRoutes().size());
	}

	@Test
	void testNoRouterModeReturnsNull() throws Exception {
		Modes modes = new Modes();
		modes.loadModes("<modes><mode name='chat' description='x'/></modes>");
		// ROUTER_MODES 是静态缓存，可能已被本类其它用例填充，因此查询一个从未加载过的名字
		assertNull(Modes.getRouterMode("no_such_router"));
	}
}
