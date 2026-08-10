package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Mode thinkingBudget 字段测试：
 * - XML {@code <mode thinkingBudget="N">} 正确解析
 * - 非法值/负值容错（不抛异常，值为 0）
 * - cloneMode() 正确复制 thinkingBudget
 */
class ModeThinkingBudgetTest {

	private static Element parse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
		return doc.getDocumentElement();
	}

	@Test
	void xmlThinkingBudget_parsed() throws Exception {
		Element el = parse(
				"<mode name='m1' thinking='true' thinkingBudget='4096'/>");
		Mode mode = Mode.parseMode(el);
		assertTrue(mode.isThinking());
		assertEquals(4096, mode.getThinkingBudget());
	}

	@Test
	void xmlThinkingBudget_absentDefaultsToZero() throws Exception {
		Element el = parse("<mode name='m1'/>");
		Mode mode = Mode.parseMode(el);
		assertEquals(0, mode.getThinkingBudget());
	}

	@Test
	void xmlThinkingBudget_invalidLogsAndFallsBack() throws Exception {
		Element el = parse("<mode name='m1' thinkingBudget='abc'/>");
		Mode mode = Mode.parseMode(el);
		assertEquals(0, mode.getThinkingBudget(), "非法值应降级为 0，不抛异常");
	}

	@Test
	void setThinkingBudget_negativeClampsToZero() {
		Mode mode = new Mode("m1", "", null, null, null);
		mode.setThinkingBudget(0);
		assertEquals(0, mode.getThinkingBudget());
		mode.setThinkingBudget(-1);
		assertEquals(0, mode.getThinkingBudget());
		mode.setThinkingBudget(2048);
		assertEquals(2048, mode.getThinkingBudget());
	}

	@Test
	void cloneMode_copiesThinkingBudget() {
		Mode mode = new Mode("m1", "", null, null, null);
		mode.setThinking(true);
		mode.setThinkingBudget(8192);
		Mode copy = mode.cloneMode();
		assertTrue(copy.isThinking());
		assertEquals(8192, copy.getThinkingBudget());
		// 修改原 mode 不影响 clone
		mode.setThinkingBudget(1024);
		assertEquals(8192, copy.getThinkingBudget());
	}
}
