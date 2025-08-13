package com.gdxsoft.ai;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiMessageUtils {
	/**
	 * 提取MD的代码内容
	 * @param markdown
	 * @return
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
