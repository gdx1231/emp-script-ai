package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 测试 {@link Modes#listModes()}：返回全部已加载 mode 的克隆列表，
 * 供 mode=auto 自动路由枚举候选使用。
 */
class ModesListModesTest {

	private static final String XML = "<modes>"
			+ "<mode name='chat' description='通用闲聊'/>"
			+ "<mode name='translate' description='翻译'/>"
			+ "</modes>";

	@Test
	void testListModesReturnsAll() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		List<Mode> list = Modes.listModes();
		assertTrue(list.size() >= 2);
		assertNotNull(list.stream().filter(m -> "chat".equals(m.getName())).findFirst().orElse(null));
		assertNotNull(list.stream().filter(m -> "translate".equals(m.getName())).findFirst().orElse(null));
	}

	@Test
	void testListModesReturnsClones() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		Mode fromList = Modes.listModes().stream()
				.filter(m -> "chat".equals(m.getName())).findFirst().orElse(null);
		assertNotNull(fromList);
		// 修改返回的对象不影响缓存原件
		fromList.setDescription("被外部篡改");
		assertEquals("通用闲聊", Modes.getMode("chat").getDescription());
	}
}
