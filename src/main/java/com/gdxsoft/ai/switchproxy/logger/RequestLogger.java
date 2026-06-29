package com.gdxsoft.ai.switchproxy.logger;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.gdxsoft.ai.switchproxy.entry.RequestLogEntry;

/**
 * 基于 DOM 的 XML 日志写入。
 */
public class RequestLogger {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RequestLogger.class);

	private final Path logDir;

	public RequestLogger(Path logDir) {
		this.logDir = logDir;
	}

	/**
	 * 把 entry 结构化为 DOM 并写文件。
	 */
	public void log(RequestLogEntry entry) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.newDocument();

			// 根元素
			Element root = doc.createElement("request");
			doc.appendChild(root);

			root.setAttribute("id", entry.getId() != null ? entry.getId() : "");
			root.setAttribute("timestamp", entry.getTimestamp());
			root.setAttribute("mode", entry.getMode() != null ? entry.getMode() : "");
			root.setAttribute("target", entry.getTarget() != null ? entry.getTarget() : "");
			root.setAttribute("model", entry.getModel() != null ? entry.getModel() : "");

			if (entry.getResponseId() != null) {
				root.setAttribute("response-id", entry.getResponseId());
			}
			if (entry.getFinishReason() != null) {
				root.setAttribute("finish-reason", entry.getFinishReason());
			}
			root.setAttribute("duration-ms", String.valueOf(entry.getDurationMs()));

			// headers
			Map<String, String> headers = entry.getHeaders();
			if (headers != null && !headers.isEmpty()) {
				Element headersElem = doc.createElement("headers");
				for (Map.Entry<String, String> h : headers.entrySet()) {
					Element headerElem = doc.createElement("header");
					headerElem.setAttribute("name", h.getKey());
					headerElem.setTextContent(h.getValue());
					headersElem.appendChild(headerElem);
				}
				root.appendChild(headersElem);
			}

			// input
			if (entry.getInput() != null) {
				Element inputElem = doc.createElement("input");
				inputElem.appendChild(doc.createCDATASection(entry.getInput()));
				root.appendChild(inputElem);
			}

			// converted-input
			if (entry.getConvertedInput() != null) {
				Element convertedElem = doc.createElement("converted-input");
				convertedElem.appendChild(doc.createCDATASection(entry.getConvertedInput()));
				root.appendChild(convertedElem);
			}

			// thinking
			String thinking = entry.getThinking();
			if (thinking != null && !thinking.isEmpty()) {
				Element thinkingElem = doc.createElement("thinking");
				thinkingElem.appendChild(doc.createCDATASection(thinking));
				root.appendChild(thinkingElem);
			}

			// output
			String output = entry.getOutput();
			if (output != null && !output.isEmpty()) {
				Element outputElem = doc.createElement("output");
				outputElem.appendChild(doc.createCDATASection(output));
				root.appendChild(outputElem);
			}

			// tool-calls
			List<RequestLogEntry.ToolCallEntry> toolCalls = entry.getToolCalls();
			if (toolCalls != null && !toolCalls.isEmpty()) {
				Element toolCallsElem = doc.createElement("tool-calls");
				for (RequestLogEntry.ToolCallEntry tc : toolCalls) {
					Element tcElem = doc.createElement("tool-call");
					if (tc.getId() != null) {
						tcElem.setAttribute("id", tc.getId());
					}
					tcElem.setAttribute("name", tc.getName() != null ? tc.getName() : "");

					Element argsElem = doc.createElement("arguments");
					argsElem.appendChild(doc.createCDATASection(tc.getArguments()));
					tcElem.appendChild(argsElem);

					toolCallsElem.appendChild(tcElem);
				}
				root.appendChild(toolCallsElem);
			}

			// usage
			if (entry.getPromptTokens() > 0 || entry.getCompletionTokens() > 0) {
				Element usageElem = doc.createElement("usage");
				usageElem.setAttribute("prompt-tokens", String.valueOf(entry.getPromptTokens()));
				usageElem.setAttribute("completion-tokens", String.valueOf(entry.getCompletionTokens()));
				usageElem.setAttribute("total-tokens", String.valueOf(entry.getTotalTokens()));
				root.appendChild(usageElem);
			}

			// 序列化写入文件
			String datePath = entry.getDatePath();
			Path dateDir = logDir.resolve(datePath);
			Files.createDirectories(dateDir);

			String filename = (entry.getId() != null ? entry.getId() : "req-" + System.currentTimeMillis()) + ".xml";
			Path filePath = dateDir.resolve(filename);

			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

			StringWriter writer = new StringWriter();
			transformer.transform(new DOMSource(doc), new StreamResult(writer));
			String xmlContent = writer.toString();

			Files.writeString(filePath, xmlContent);
			LOGGER.debug("日志写入: {}", filePath);

		} catch (Exception e) {
			LOGGER.error("写入日志失败: {}", e.getMessage(), e);
		}
	}
}
