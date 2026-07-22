package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 测试 <common><apis> 公共 API 合并逻辑：
 * mode 读取 apis 时先取本 mode 下的 apis，再合并 common/apis/api，
 * 名称一致（忽略大小写）时 common 中的定义被抛弃。
 */
class ModesCommonApisTest {

	private static final String XML = "<modes>"
			+ "<mode name='M1'>"
			+ "<apis>"
			+ "<api name='weatherapi' url='http://mode-local/weather' method='get'/>"
			+ "</apis>"
			+ "</mode>"
			+ "<mode name='M2'>"
			+ "</mode>"
			+ "<common>"
			+ "<apis>"
			+ "<api name='WEATHERAPI' url='http://common/weather' method='get'/>"
			+ "<api name='otherapi' url='http://common/other' method='post'/>"
			+ "</apis>"
			+ "</common>"
			+ "</modes>";

	@Test
	void testModeLocalApiOverridesCommon() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		// M1 本地定义了 weatherapi（名称忽略大小写一致），common 中的被抛弃
		Mode m1 = Modes.getMode("M1");
		assertNotNull(m1);
		assertEquals(2, m1.getApis().size());
		Api weather = m1.getApi("weatherapi");
		assertEquals("http://mode-local/weather", weather.getUrl());
		// common 中不同名的 otherapi 被合并进来
		Api other = m1.getApi("otherapi");
		assertNotNull(other);
		assertEquals("http://common/other", other.getUrl());

		// M2 没有本地 apis，全部从 common 合并
		Mode m2 = Modes.getMode("M2");
		assertNotNull(m2);
		assertEquals(2, m2.getApis().size());
		assertEquals("http://common/weather", m2.getApi("weatherapi").getUrl());
		assertEquals("http://common/other", m2.getApi("otherapi").getUrl());
	}

	@Test
	void testNoCommonElement() throws Exception {
		String xml = "<modes><mode name='M3'><apis>"
				+ "<api name='a1' url='http://local/a1'/>"
				+ "</apis></mode></modes>";
		Modes modes = new Modes();
		modes.loadModes(xml);
		Mode m3 = Modes.getMode("M3");
		assertNotNull(m3);
		assertEquals(1, m3.getApis().size());
	}
}
