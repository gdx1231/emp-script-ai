package com.gdxsoft.ai.switchproxy.logger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;

/**
 * RequestLogger 单元测试。
 */
public class RequestLoggerTest {

	@TempDir
	Path tempDir;

	private RequestLogger logger;

	@BeforeEach
	void setUp() {
		logger = new RequestLogger(tempDir);
	}

	@Test
	void testCdataEscaping() {
		// 构造含 ]]> 的输入，验证序列化后自动拆段
		RequestLogEntry entry = createBasicEntry();
		entry.appendOutput("before ]]> after");

		logger.log(entry);

		Path logFile = findLogFile();
		assertNotNull(logFile, "日志文件应存在");

		String content = readFileContent(logFile);
		// JDK serializer 应将 ]]> 拆分为 ]]]]><![CDATA[>
		assertTrue(content.contains("]]]]><![CDATA[>"),
				"CDATA 中的 ]]> 应被自动拆段，实际内容:\n" + content);
	}

	@Test
	void testXxeProtection() {
		// 验证 XXE 注入被拒绝
		RequestLogEntry entry = createBasicEntry();
		entry.setInput("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><foo>&xxe;</foo>");

		logger.log(entry);

		// 日志应正常写入（输入作为 CDATA 内容，不会被解析为 DTD）
		Path logFile = findLogFile();
		assertNotNull(logFile);

		// 读取日志文件并验证 XML 格式正确
		Document doc = parseXmlFile(logFile);
		assertNotNull(doc, "日志 XML 应可正常解析");

		// 验证输入内容被安全地包裹在 CDATA 中
		NodeList inputNodes = doc.getElementsByTagName("input");
		assertTrue(inputNodes.getLength() > 0, "应有 <input> 节点");
		String inputContent = inputNodes.item(0).getTextContent();
		assertTrue(inputContent.contains("<!DOCTYPE"), "输入内容应原样保留");
	}

	@Test
	void testFullDocumentStructure() {
		RequestLogEntry entry = createBasicEntry();
		entry.setInput("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}");
		entry.setConvertedInput("{\"model\":\"claude\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}");
		entry.appendOutput("你好！");
		entry.appendThinking("让我想想...");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer sk-***");
		entry.setHeaders(headers);

		// 模拟 OpenAI SSE 行
		entry.appendRawSseLine("data: {\"id\":\"chatcmpl-123\",\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}");
		entry.appendRawSseLine("data: {\"id\":\"chatcmpl-123\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"total_tokens\":7}}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("openai");

		logger.log(entry);

		Path logFile = findLogFile();
		assertNotNull(logFile);

		Document doc = parseXmlFile(logFile);
		assertNotNull(doc);

		Element root = doc.getDocumentElement();
		assertEquals("request", root.getTagName());
		assertEquals("chat2anthropic", root.getAttribute("mode"));
		assertEquals("test-target", root.getAttribute("target"));
		assertEquals("test-model", root.getAttribute("model"));
		assertEquals("stop", root.getAttribute("finish-reason"));
		assertEquals("chatcmpl-123", root.getAttribute("response-id"));

		// headers
		NodeList headerNodes = doc.getElementsByTagName("header");
		assertTrue(headerNodes.getLength() >= 2, "应有至少 2 个 header");

		// input
		NodeList inputNodes = doc.getElementsByTagName("input");
		assertEquals(1, inputNodes.getLength());
		assertTrue(inputNodes.item(0).getTextContent().contains("gpt-4"));

		// converted-input
		NodeList convertedNodes = doc.getElementsByTagName("converted-input");
		assertEquals(1, convertedNodes.getLength());
		assertTrue(convertedNodes.item(0).getTextContent().contains("claude"));

		// thinking
		NodeList thinkingNodes = doc.getElementsByTagName("thinking");
		assertEquals(1, thinkingNodes.getLength());
		assertTrue(thinkingNodes.item(0).getTextContent().contains("让我想想"));

		// output
		NodeList outputNodes = doc.getElementsByTagName("output");
		assertEquals(1, outputNodes.getLength());
		assertTrue(outputNodes.item(0).getTextContent().contains("你好"));

		// usage
		NodeList usageNodes = doc.getElementsByTagName("usage");
		assertEquals(1, usageNodes.getLength());
		Element usageElem = (Element) usageNodes.item(0);
		assertEquals("5", usageElem.getAttribute("prompt-tokens"));
		assertEquals("2", usageElem.getAttribute("completion-tokens"));
		assertEquals("7", usageElem.getAttribute("total-tokens"));
	}

	@Test
	void testFinalizeOpenAi() {
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"id\":\"resp-001\",\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}");
		entry.appendRawSseLine("data: {\"id\":\"resp-001\",\"choices\":[{\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}");
		entry.appendRawSseLine("data: {\"id\":\"resp-001\",\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("openai");

		assertEquals("stop", entry.getFinishReason());
		assertEquals("resp-001", entry.getResponseId());
		assertEquals(10, entry.getPromptTokens());
		assertEquals(5, entry.getCompletionTokens());
		assertEquals(15, entry.getTotalTokens());
	}

	@Test
	void testFinalizeAnthropic() {
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01ABC\",\"usage\":{\"input_tokens\":20}}}");
		entry.appendRawSseLine("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}");
		entry.appendRawSseLine("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":3}}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("anthropic");

		assertEquals("end_turn", entry.getFinishReason());
		assertEquals("msg_01ABC", entry.getResponseId());
		assertEquals(20, entry.getPromptTokens());
		assertEquals(3, entry.getCompletionTokens());
	}

	@Test
	void testFinalizeAnthropicToolCalls() {
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_tc\",\"usage\":{\"input_tokens\":10}}}");
		entry.appendRawSseLine("data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"get_weather\"}}");
		entry.appendRawSseLine("data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"city\\\":\"}}");
		entry.appendRawSseLine("data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"北京\\\"}\"}}");
		entry.appendRawSseLine("data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":15}}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("anthropic");

		assertEquals("tool_use", entry.getFinishReason());
		assertEquals(1, entry.getToolCalls().size());
		assertEquals("toolu_01", entry.getToolCalls().get(0).getId());
		assertEquals("get_weather", entry.getToolCalls().get(0).getName());
		assertTrue(entry.getToolCalls().get(0).getArguments().contains("北京"));
	}

	@Test
	void testFinalizeResponses() {
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_abc\"}}");
		entry.appendRawSseLine("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}");
		entry.appendRawSseLine("data: {\"type\":\"response.output_text.delta\",\"delta\":\" world\"}");
		entry.appendRawSseLine("data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_abc\",\"usage\":{\"input_tokens\":8,\"output_tokens\":4,\"total_tokens\":12},\"output\":[]}}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("responses");

		assertEquals("stop", entry.getFinishReason());
		assertEquals("resp_abc", entry.getResponseId());
		assertEquals(8, entry.getPromptTokens());
		assertEquals(4, entry.getCompletionTokens());
		assertEquals(12, entry.getTotalTokens());
	}

	@Test
	void testRawSseNotPersisted() {
		// appendRawSseLine 的内容不应出现在最终日志中
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"secret\":\"should-not-appear\"}");
		entry.appendOutput("visible output");
		entry.finalize("openai");

		logger.log(entry);

		Path logFile = findLogFile();
		assertNotNull(logFile);

		String content = readFileContent(logFile);
		assertFalse(content.contains("should-not-appear"),
				"原始 SSE 行不应出现在日志文件中");
		assertFalse(content.contains("<raw-sse>"),
				"日志中不应有 <raw-sse> 节点");
		assertTrue(content.contains("visible output"),
				"输出内容应出现在日志中");
	}

	@Test
	void testEmptyToolCallsOmitted() {
		RequestLogEntry entry = createBasicEntry();
		entry.appendRawSseLine("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}");
		entry.appendRawSseLine("data: [DONE]");
		entry.finalize("openai");

		logger.log(entry);

		Path logFile = findLogFile();
		Document doc = parseXmlFile(logFile);
		NodeList toolCallsNodes = doc.getElementsByTagName("tool-calls");
		assertEquals(0, toolCallsNodes.getLength(), "零个 tool-calls 时整段应省略");
	}

	// === 辅助方法 ===

	private RequestLogEntry createBasicEntry() {
		RequestLogEntry entry = new RequestLogEntry();
		entry.setId("test-req-001");
		entry.setMode("chat2anthropic");
		entry.setTarget("test-target");
		entry.setModel("test-model");
		return entry;
	}

	private Path findLogFile() {
		try {
			return Files.walk(tempDir)
					.filter(p -> p.toString().endsWith(".xml"))
					.findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private String readFileContent(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			fail("读取文件失败: " + e.getMessage());
			return null;
		}
	}

	private Document parseXmlFile(Path path) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(path.toFile());
		} catch (Exception e) {
			fail("解析 XML 失败: " + e.getMessage());
			return null;
		}
	}
}
