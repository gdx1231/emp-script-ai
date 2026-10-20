package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Mode 联网搜索配置测试：
 * - XML enableSearch / searchStrategy / forcedSearch 正确解析
 * - 缺省值
 * - cloneMode() 正确复制搜索配置
 */
class ModeSearchConfigTest {

	private static Element parse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
		return doc.getDocumentElement();
	}

	@Test
	void xmlSearchAttributes_parsed() throws Exception {
		Element el = parse("<mode name='m1' enableSearch='true' searchStrategy='agent' forcedSearch='true'/>");
		Mode mode = Mode.parseMode(el);
		assertTrue(mode.isEnableSearch());
		assertEquals("agent", mode.getSearchStrategy());
		assertTrue(mode.isForcedSearch());
	}

	@Test
	void xmlSearchAttributes_defaults() throws Exception {
		Element el = parse("<mode name='m1'/>");
		Mode mode = Mode.parseMode(el);
		assertFalse(mode.isEnableSearch());
		assertNull(mode.getSearchStrategy());
		assertFalse(mode.isForcedSearch());
	}

	@Test
	void setSearchStrategy_trimsAndNormalizesBlank() {
		Mode mode = new Mode("m1", "", null, null, null);
		mode.setSearchStrategy(" max ");
		assertEquals("max", mode.getSearchStrategy());
		mode.setSearchStrategy("   ");
		assertNull(mode.getSearchStrategy());
	}

	@Test
	void cloneMode_copiesSearchConfig() {
		Mode mode = new Mode("m1", "", null, null, null);
		mode.setEnableSearch(true);
		mode.setSearchStrategy("turbo");
		mode.setForcedSearch(true);

		Mode copy = mode.cloneMode();
		assertTrue(copy.isEnableSearch());
		assertEquals("turbo", copy.getSearchStrategy());
		assertTrue(copy.isForcedSearch());

		// 修改原 mode 不影响 clone
		mode.setSearchStrategy("max");
		mode.setForcedSearch(false);
		assertEquals("turbo", copy.getSearchStrategy());
		assertTrue(copy.isForcedSearch());
	}
}
