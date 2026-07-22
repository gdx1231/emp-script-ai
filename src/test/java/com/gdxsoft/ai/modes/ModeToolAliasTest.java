package com.gdxsoft.ai.modes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.gdxsoft.easyweb.script.RequestValue;

/**
 * 测试 &lt;tools&gt;/&lt;tool&gt; 别名与 Tool 本地命令执行：
 * - tool 与 api 混写、同名时 tool 整体覆盖 api（忽略大小写）；
 * - tool 内 CDATA 解析为调用说明（usage），apisCheck prompt 构建时自动附加；
 * - prompt 的 tool/toolsCheck 属性等价于 api/apisCheck；
 * - common 下的 tools 参与合并，mode 本地优先；
 * - command 非空的 Tool 执行本地程序。
 */
class ModeToolAliasTest {

	private static final String XML = "<modes>"
			+ "<mode name='M1'>"
			+ "<step name='init'><prompts>"
			+ "<prompt name='check' role='user' toolsCheck='true'><![CDATA[判断并调用工具]]></prompt>"
			+ "<prompt name='data' role='user' tool='getData'/>"
			+ "</prompts></step>"
			+ "<apis>"
			+ "<api name='weatherapi' url='http://api-old/weather' method='get'/>"
			+ "<api name='getData' url='http://api/data' method='post'/>"
			+ "</apis>"
			+ "<tools>"
			+ "<tool name='WEATHERAPI' url='http://tool-new/weather' method='get'>"
			+ "<![CDATA[weatherapi(location: str):查询指定城市的天气]]>"
			+ "</tool>"
			+ "<tool name='localproc' command='echo hello @name' timeout='5000'>"
			+ "<![CDATA[localproc(name: str):本地打招呼程序]]>"
			+ "</tool>"
			+ "</tools>"
			+ "</mode>"
			+ "<mode name='M2'>"
			+ "</mode>"
			+ "<common>"
			+ "<tools>"
			+ "<tool name='commontool' url='http://common/tool'><![CDATA[commontool():公共工具]]></tool>"
			+ "<tool name='weatherapi' url='http://common/weather'/>"
			+ "</tools>"
			+ "</common>"
			+ "</modes>";

	@Test
	void testToolOverridesSameNameApi() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m1 = Modes.getMode("M1");
		assertNotNull(m1);

		// 同名（忽略大小写）时 tool 整体覆盖 api，且保持原位（第0个）
		Api weather = m1.getApis().get(0);
		assertTrue(weather instanceof Tool);
		assertEquals("http://tool-new/weather", weather.getUrl());

		// getData / localproc / commontool 均生效；common 的 weatherapi 被本地抛弃
		assertEquals(4, m1.getApis().size());
		assertEquals("http://api/data", m1.getApi("getData").getUrl());
		assertEquals("http://tool-new/weather", m1.getApi("weatherapi").getUrl());
		assertEquals("http://common/tool", m1.getApi("commontool").getUrl());
	}

	@Test
	void testUsageParsedAndAggregated() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m1 = Modes.getMode("M1");

		assertEquals("weatherapi(location: str):查询指定城市的天气", m1.getApi("weatherapi").getUsage());
		assertEquals("localproc(name: str):本地打招呼程序", m1.getApi("localproc").getUsage());
		// 无 CDATA 的 api 没有 usage
		assertNull(m1.getApi("getData").getUsage());

		// getApisUsage 逐条拼接、跳过无 usage 项
		String usage = m1.getApisUsage();
		assertEquals("weatherapi(location: str):查询指定城市的天气\n"
				+ "localproc(name: str):本地打招呼程序\n"
				+ "commontool():公共工具", usage);
	}

	@Test
	void testCommonToolsMergedToModeWithoutLocal() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m2 = Modes.getMode("M2");
		assertNotNull(m2);
		assertEquals(2, m2.getApis().size());
		assertEquals("http://common/tool", m2.getApi("commontool").getUrl());
		assertEquals("http://common/weather", m2.getApi("weatherapi").getUrl());
	}

	@Test
	void testPromptToolAndToolsCheckAttributes() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m1 = Modes.getMode("M1");
		Step step = m1.getSteps().get(0);

		Prompt check = step.getPrompts().get(0);
		assertTrue(check.isApisCheck()); // toolsCheck="true" 等价 apisCheck

		Prompt data = step.getPrompts().get(1);
		assertEquals("getData", data.getApi()); // tool="getData" 等价 api="getData"
	}

	@Test
	void testCloneKeepsToolAndUsage() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m1 = Modes.getMode("M1"); // getMode 返回克隆体
		Api weather = m1.getApi("weatherapi");
		assertTrue(weather instanceof Tool);
		assertEquals("weatherapi(location: str):查询指定城市的天气", weather.getUsage());
		Api local = m1.getApi("localproc");
		assertTrue(local instanceof Tool);
		assertEquals("echo hello @name", ((Tool) local).getCommand());
	}

	@Test
	void testLocalCommandExecution() throws Exception {
		Modes modes = new Modes();
		modes.loadModes(XML);
		Mode m1 = Modes.getMode("M1");

		RequestValue rv = new RequestValue();
		rv.addOrUpdateValue("name", "world");

		Prompt p = new Prompt();
		p.setName("runLocal");
		p.setRole("user");
		p.setApi("localproc");

		boolean ok = m1.createStepPromptByApi(p, rv, new HashMap<>());
		assertTrue(ok);
		assertEquals("echo hello world", p.getApiCurl()); // 记录实际执行的命令行
		assertEquals("hello world", p.getContent());
	}
}
