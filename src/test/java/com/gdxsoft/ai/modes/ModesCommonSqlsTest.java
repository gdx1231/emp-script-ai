package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * 测试 {@code <common><sqls>} 公共 SQL 合并逻辑：
 * mode 的 sqls 先从 common 获取，再从本 mode 下获取，
 * 名称一致（忽略大小写）时 mode 级别覆盖 common。
 */
class ModesCommonSqlsTest {

	private static final String XML = "<modes>"
			+ "<common>"
			+ "<sqls>"
			+ "<sql name='get_users'>SELECT * FROM users</sql>"
			+ "<sql name='GET_ORDERS'>SELECT * FROM orders</sql>"
			+ "</sqls>"
			+ "</common>"
			+ "<mode name='M1'>"
			+ "<sqls>"
			+ "<sql name='Get_Users'>SELECT id, name FROM users WHERE active=1</sql>"
			+ "</sqls>"
			+ "</mode>"
			+ "<mode name='M2'>"
			+ "</mode>"
			+ "</modes>";

	@Test
	void testModeLocalSqlOverridesCommon() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		// M1 本地定义了 Get_Users（与 common 的 get_users 忽略大小写一致），mode 级别覆盖
		Mode m1 = Modes.getMode("M1");
		assertNotNull(m1);
		assertEquals(2, m1.getSqlQueries().size());

		SqlQuery usersSql = m1.findSqlQueryByRef("get_users");
		assertNotNull(usersSql);
		// mode 级别的定义优先
		assertEquals("SELECT id, name FROM users WHERE active=1", usersSql.getContent());

		// common 中不同名的 GET_ORDERS 被合并进来
		SqlQuery ordersSql = m1.findSqlQueryByRef("get_orders");
		assertNotNull(ordersSql);
		assertEquals("SELECT * FROM orders", ordersSql.getContent());
	}

	@Test
	void testModeWithNoLocalSqlsGetsAllCommon() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);

		// M2 没有本地 sqls，全部从 common 合并
		Mode m2 = Modes.getMode("M2");
		assertNotNull(m2);
		assertEquals(2, m2.getSqlQueries().size());
		assertEquals("SELECT * FROM users", m2.findSqlQueryByRef("get_users").getContent());
		assertEquals("SELECT * FROM orders", m2.findSqlQueryByRef("get_orders").getContent());
	}

	@Test
	void testNoCommonElement() throws Exception {
		String xml = "<modes><mode name='M3'><sqls>"
				+ "<sql name='local_sql'>SELECT 1</sql>"
				+ "</sqls></mode></modes>";
		Modes modes = new Modes();
		modes.loadModes(xml);
		Mode m3 = Modes.getMode("M3");
		assertNotNull(m3);
		assertEquals(1, m3.getSqlQueries().size());
		assertEquals("SELECT 1", m3.findSqlQueryByRef("local_sql").getContent());
	}

	@Test
	void testNoSqlsAtAll() throws Exception {
		String xml = "<modes><mode name='M4'></mode></modes>";
		Modes modes = new Modes();
		modes.loadModes(xml);
		Mode m4 = Modes.getMode("M4");
		assertNotNull(m4);
		assertEquals(0, m4.getSqlQueries().size());
	}
}
