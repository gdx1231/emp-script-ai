package com.gdxsoft.ai;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 文本处理工具类。
 * <p>
 * Utilities for processing AI-generated text.
 */
public class AiMessageUtils {
	/**
	 * 从 Markdown 文本中提取代码块（以 ``` 开始/结束）。
	 * <p>
	 * Extract code blocks from a Markdown string (fenced by ```).
	 *
	 * 返回的每个 Map 包含：
	 * - language: 语言（可能为空字符串）| code language (may be empty)
	 * - code: 代码内容 | code text
	 *
	 * @param markdown Markdown 文本 | markdown content
	 * @return 代码块列表 | list of code block maps
	 */
	public static List<Map<String, String>> extractCodeBlocks(String markdown) {
		List<Map<String, String>> codeBlocks = new ArrayList<>();

		// 正则表达式匹配代码块
		String regex = "```(\\w+)?\\s*([^`]*)```";
		Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
		Matcher matcher = pattern.matcher(markdown);

		while (matcher.find()) {
			String language = matcher.group(1); // 语言类型，可能为 null
			String code = matcher.group(2); // 代码内容

			Map<String, String> block = new HashMap<>();
			block.put("language", language == null ? "" : language.trim());
			block.put("code", code == null ? "" : code.trim());

			codeBlocks.add(block);
		}

		return codeBlocks;
	}

}
