package com.gdxsoft.ai.modes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gdxsoft.easyweb.data.DTTable;
import com.gdxsoft.easyweb.script.RequestValue;

public class ParamCheck {
	private static final Logger LOGGER = LoggerFactory.getLogger(ParamCheck.class);
	
	private String name;
	private String des;
	private String type;
	private String defaultValue;
	private String options;
	private String promptRule; // AI 提取规则说明
	private String sqlRef; // SQL 引用（用于动态枚举值）
	private String sqlValueField; // SQL 结果中作为值的字段
	private String sqlLabelField; // SQL 结果中作为显示标签的字段
	private Map<String, String> optionMap;

	public ParamCheck(String name, String des, String type, String defaultValue, String options) {
		this(name, des, type, defaultValue, options, null);
	}

	public ParamCheck(String name, String des, String type, String defaultValue, String options, String promptRule) {
		this.name = name;
		this.des = des;
		this.type = (type == null || type.trim().isEmpty()) ? "string" : type.trim();
		this.defaultValue = defaultValue;
		this.options = options;
		this.promptRule = promptRule;
		this.optionMap = new HashMap<>();
		if (options != null && !options.trim().isEmpty()) {
			for (String opt : options.split(",")) {
				String[] kv = opt.trim().split("=", 2);
				if (kv.length == 2) {
					optionMap.put(kv[0].trim(), kv[1].trim());
				}
			}
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDes() {
		return des;
	}

	public void setDes(String des) {
		this.des = des;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
	}

	public Map<String, String> getOptionMap() {
		return optionMap;
	}

	public List<String> getOptionKeys() {
		return new ArrayList<>(optionMap.keySet());
	}

	public boolean isValidEnumValue(String value) {
		if (optionMap.isEmpty()) {
			return true;
		}
		return optionMap.containsKey(value);
	}

	public String getEnumLabel(String value) {
		return optionMap.getOrDefault(value, value);
	}

	public String getPromptRule() {
		return promptRule;
	}

	public void setPromptRule(String promptRule) {
		this.promptRule = promptRule;
	}

	public String getSqlRef() {
		return sqlRef;
	}

	public void setSqlRef(String sqlRef) {
		this.sqlRef = sqlRef;
	}

	public String getSqlValueField() {
		return sqlValueField;
	}

	public void setSqlValueField(String sqlValueField) {
		this.sqlValueField = sqlValueField;
	}

	public String getSqlLabelField() {
		return sqlLabelField;
	}

	public void setSqlLabelField(String sqlLabelField) {
		this.sqlLabelField = sqlLabelField;
	}

	// ==================== 静态工具方法 ====================

	/**
	 * 根据参数定义列表构建提取参数的 prompt
	 *
	 * @param paramChecks 参数定义列表
	 * @param mode        模式对象（用于获取 SQL 查询定义）
	 * @param rv          请求参数容器（用于执行 SQL）
	 * @return 构建好的 prompt 字符串
	 */
	public static String buildExtractPrompt(List<ParamCheck> paramChecks, Mode mode, RequestValue rv) {
		LOGGER.debug("buildExtractPrompt: 开始构建, 参数数量={}", paramChecks.size());
		StringBuilder sb = new StringBuilder();
		sb.append("你是一个旅游参数提取专家。请从对话内容中提取以下参数，返回 JSON 格式。\n\n");
		sb.append("需要提取的参数：\n");

		for (ParamCheck pc : paramChecks) {
			sb.append("- ").append(pc.getName()).append("（").append(pc.getDes()).append("）");

			if ("int".equals(pc.getType())) {
				sb.append("，类型：整数");
			} else if ("enum".equals(pc.getType())) {
				// 枚举类型：从 SQL 或静态 options 获取值
				String enumOptions = pc.getOptions();
				String dynamicPromptRule = pc.getPromptRule();

				if (pc.getSqlRef() != null && !pc.getSqlRef().isEmpty() && mode != null && rv != null) {
					LOGGER.debug("buildExtractPrompt: 参数 {} 使用 SQL 引用: {}", pc.getName(), pc.getSqlRef());
					enumOptions = pc.loadEnumOptionsFromSql(mode, rv);
					// 根据 SQL 结果动态组合 promptRule
					dynamicPromptRule = pc.buildDynamicPromptRule(enumOptions);
					LOGGER.debug("buildExtractPrompt: 动态生成 promptRule: {}", dynamicPromptRule);
				}

				if (enumOptions != null && !enumOptions.isEmpty()) {
					sb.append("，类型：枚举，可选值：").append(enumOptions);
				}
				if (dynamicPromptRule != null && !dynamicPromptRule.isEmpty()) {
					sb.append("，提取规则：").append(dynamicPromptRule);
				}
			} else {
				// 非枚举类型，使用静态 promptRule
				if (pc.getPromptRule() != null && !pc.getPromptRule().isEmpty()) {
					sb.append("，提取规则：").append(pc.getPromptRule());
				}
			}

			if (pc.getDefaultValue() != null && !pc.getDefaultValue().isEmpty()) {
				sb.append("，默认值：").append(pc.getDefaultValue());
			}
			sb.append("\n");
		}

		sb.append("\n输出格式（仅返回 JSON，不要其他文字）：\n");
		sb.append("{");
		boolean first = true;
		for (ParamCheck pc : paramChecks) {
			if (!first) sb.append(",");
			sb.append("\"").append(pc.getName()).append("\": \"提取到的值或null\"");
			first = false;
		}
		sb.append("}\n\n");
		sb.append("规则：\n");
		sb.append("- 如果对话中未提及某参数，对应值设为 null\n");
		
		String result = sb.toString();
		LOGGER.debug("buildExtractPrompt: 构建完成, 总长度={}", result.length());
		return result;
	}

	/**
	 * 从 SQL 查询结果加载枚举选项
	 *
	 * @param mode Mode 对象（包含 SQL 定义）
	 * @param rv   请求参数容器
	 * @return 枚举选项字符串（格式：KEY1=标签1,KEY2=标签2）
	 */
	public String loadEnumOptionsFromSql(Mode mode, RequestValue rv) {
		if (sqlRef == null || sqlRef.isEmpty()) {
			return options;
		}

		try {
			// 从 Mode 获取 SQL 查询定义
			SqlQuery sqlQuery = mode.findSqlQueryByRef(sqlRef);
			if (sqlQuery == null) {
				LOGGER.warn("loadEnumOptionsFromSql: SQL 查询未找到, sqlRef={}", sqlRef);
				return options;
			}
			LOGGER.debug("loadEnumOptionsFromSql: 执行 SQL, sqlRef={}", sqlRef);

			String sql = sqlQuery.getContent();
			DTTable tb = DTTable.getJdbcTable(sql, rv);
			LOGGER.debug("loadEnumOptionsFromSql: SQL 返回 {} 行", tb.getCount());

			StringBuilder opts = new StringBuilder();
			String valueField = sqlValueField != null ? sqlValueField : "BAS_TAG";
			String labelField = sqlLabelField != null ? sqlLabelField : "BAS_TAG_NAME";

			for (int i = 0; i < tb.getCount(); i++) {
				if (i > 0) opts.append(",");
				String value = tb.getCell(i, valueField).toString();
				String label = tb.getCell(i, labelField).toString();
				opts.append(value).append("=").append(label);
			}
			LOGGER.debug("loadEnumOptionsFromSql: 生成选项: {}", opts.toString());
			return opts.toString();
		} catch (Exception ex) {
			LOGGER.warn("loadEnumOptionsFromSql 失败 [{}]: {}, 回退到硬编码选项", name, ex.getMessage());
			return options; // 回退到硬编码选项
		}
	}

	/**
	 * 根据 SQL 查询结果动态生成 promptRule
	 * 将枚举标签列表附加到 promptRule 后面
	 * 
	 * 示例：
	 * XML 配置：promptRule="根据行程内容判断最匹配的类型"
	 * SQL 结果标签：游学/访学/成本团/会议
	 * 生成结果：根据行程内容判断最匹配的类型，可选值：游学/访学/成本团/会议
	 *
	 * @param enumOptions 枚举选项字符串（格式：KEY1=标签1,KEY2=标签2）
	 * @return 动态生成的 promptRule
	 */
	public String buildDynamicPromptRule(String enumOptions) {
		if (enumOptions == null || enumOptions.isEmpty()) {
			return promptRule;
		}

		// 从枚举选项中提取标签部分（格式：KEY=标签,KEY2=标签2）
		StringBuilder labels = new StringBuilder();
		String[] pairs = enumOptions.split(",");
		int count = 0;
		for (String pair : pairs) {
			if (count >= 8) { // 最多显示 8 个，避免 prompt 过长
				break;
			}
			String[] kv = pair.split("=", 2);
			if (kv.length == 2) {
				if (labels.length() > 0) {
					labels.append("/");
				}
				labels.append(kv[1]);
				count++;
			}
		}
		if (labels.length() == 0) {
			return promptRule;
		}

		String result = (promptRule != null ? promptRule : des) + "，可选值：" + labels.toString();
		LOGGER.debug("buildDynamicPromptRule [{}]: {}", name, result);
		return result;
	}
}
