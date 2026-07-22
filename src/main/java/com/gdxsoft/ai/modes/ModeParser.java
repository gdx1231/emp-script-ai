package com.gdxsoft.ai.modes;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Mode对象的XML解析器类 包含所有与XML解析相关的静态方法
 * 
 * @author PF2023项目组
 * @since 2025-08-23
 */
public class ModeParser {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ModeParser.class);

	/**
	 * 私有构造函数，防止实例化
	 */
	private ModeParser() {
	}

	/**
	 * 解析XML <mode> 元素为Mode实例
	 * 
	 * @param root mode元素
	 * @return Mode对象
	 */
	public static Mode parseMode(Element root) {
		String modeName = root.getAttribute("name");
		String modeDescription = root.getAttribute("description");
		String temperatureAttr = root.getAttribute("temperature");
		String topPAttr = root.getAttribute("topP");
		String thinkingAttr = root.getAttribute("thinking");
		String responseFormatAttr = root.getAttribute("responseFormat");
		String debugOutputAttr = root.getAttribute("debugOutput");

		// Parse steps
		List<Step> steps = new ArrayList<>();
		NodeList stepNodes = root.getElementsByTagName("step");
		for (int i = 0; i < stepNodes.getLength(); i++) {
			Element stepElement = (Element) stepNodes.item(i);
			String stepName = stepElement.getAttribute("name");
			String stepDescription = stepElement.getAttribute("description");
			String stepAction = stepElement.getAttribute("action");
			String stepApi = stepElement.getAttribute("api");
			if (stepApi == null || stepApi.trim().length() == 0) {
				// tool 属性为 api 的别名写法
				stepApi = stepElement.getAttribute("tool");
			}
			String actionSqlRef = stepElement.getAttribute("actionSqlRef");
			String innerCallAttr = stepElement.getAttribute("innerCall");
			String multiOnlyUserMsgAttr = stepElement.getAttribute("multiOnlyUserMsg");
			String stepStreamAttr = stepElement.getAttribute("stream");
			String cachedSecondsAttr = stepElement.getAttribute("cachedSeconds");
			String cachedSecendsAttr = stepElement.getAttribute("cachedSecends"); // Typo tolerance
			boolean stepStream = true; // default true
			if (stepStreamAttr != null && stepStreamAttr.trim().length() > 0) {
				stepStream = Boolean.parseBoolean(stepStreamAttr.trim());
			}
			boolean stepInnerCall = innerCallAttr != null && innerCallAttr.trim().length() > 0
					&& Boolean.parseBoolean(innerCallAttr.trim());
			boolean stepMultiOnlyUserMsg = multiOnlyUserMsgAttr != null && multiOnlyUserMsgAttr.trim().length() > 0
					&& Boolean.parseBoolean(multiOnlyUserMsgAttr.trim());
			String validateParamsAttr = stepElement.getAttribute("validateParams");
			int stepCachedSeconds = 0; // default 0 (no cache)
			String cachedAttr = (cachedSecondsAttr != null && !cachedSecondsAttr.trim().isEmpty())
				? cachedSecondsAttr : cachedSecendsAttr;
			if (cachedAttr != null && !cachedAttr.trim().isEmpty()) {
				try {
					stepCachedSeconds = Integer.parseInt(cachedAttr.trim());
				} catch (NumberFormatException ex) {
					LOGGER.warn("Invalid cachedSeconds/cachedSecends attribute: {}", cachedAttr);
				}
			}

			// Parse prompts within step
			List<Prompt> prompts = new ArrayList<>();
			NodeList promptsNode = stepElement.getElementsByTagName("prompts");
			if (promptsNode.getLength() > 0) {
				prompts = parsePrompts((Element) promptsNode.item(0));
			}
			Step step;
			if (stepAction != null && stepAction.length() > 0) {
				step = new Step(stepName, stepDescription, prompts, stepAction);
			} else {
				step = new Step(stepName, stepDescription, prompts);
			}
			step.setStream(stepStream);
			if (actionSqlRef != null && actionSqlRef.trim().length() > 0) {
				step.setActionSqlRef(actionSqlRef.trim());
			}
			if (stepApi != null && stepApi.trim().length() > 0) {
				step.setApi(stepApi.trim());
			}
			step.setInnerCall(stepInnerCall);
			if (stepMultiOnlyUserMsg) {
				step.setMultiOnlyUserMsg(stepMultiOnlyUserMsg);
			}
			if (validateParamsAttr != null && validateParamsAttr.trim().length() > 0) {
				step.setValidateParams(validateParamsAttr.trim());
			}
			step.setCachedSeconds(stepCachedSeconds);
			steps.add(step);
		}

		// Parse SQL queries
		List<SqlQuery> sqlQueries = new ArrayList<>();
		NodeList sqlsNode = root.getElementsByTagName("sqls");
		if (sqlsNode.getLength() > 0) {
			sqlQueries = parseSqlQueries((Element) sqlsNode.item(0));
		}

		// Parse actions
		List<Action> actions = new ArrayList<>();
		NodeList actionsNodes = root.getElementsByTagName("actions");
		if (actionsNodes.getLength() > 0) {
			Element actionsElement = (Element) actionsNodes.item(0);
			NodeList actionNodes = actionsElement.getElementsByTagName("action");
			for (int i = 0; i < actionNodes.getLength(); i++) {
				Element actionElement = (Element) actionNodes.item(i);
				String actionName = actionElement.getAttribute("name");
				String actionDescription = actionElement.getAttribute("description");
				String className = actionElement.getAttribute("class");
				actions.add(new Action(actionName, actionDescription, className));
			}
		}

		// Parse APIs（<apis>/<api>）与 Tools（<tools>/<tool>，同名时 tool 整体覆盖 api）
		List<Api> apis = new ArrayList<>();
		Map<String, Integer> apiNameIndex = new HashMap<>();
		collectApis(root, "apis", "api", apis, apiNameIndex, false);
		collectApis(root, "tools", "tool", apis, apiNameIndex, true);

		// Parse paramChecks
		List<ParamCheck> paramChecks = new ArrayList<>();
		NodeList paramChecksNodes = root.getElementsByTagName("paramChecks");
		if (paramChecksNodes.getLength() > 0) {
			paramChecks = parseParamChecks((Element) paramChecksNodes.item(0));
		}

		Mode mode = new Mode(modeName, modeDescription, steps, sqlQueries, actions, apis);
		mode.setParamChecks(paramChecks);
		if (temperatureAttr != null && temperatureAttr.trim().length() > 0) {
			try {
				mode.setTemperature(Double.parseDouble(temperatureAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid temperature attribute: {}", temperatureAttr);
			}
		}
		if (topPAttr != null && topPAttr.trim().length() > 0) {
			try {
				mode.setTopP(Double.parseDouble(topPAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid topP attribute: {}", topPAttr);
			}
		}
		if (thinkingAttr != null && thinkingAttr.trim().length() > 0) {
			mode.setThinking(Boolean.parseBoolean(thinkingAttr.trim()));
		}
		if (responseFormatAttr != null && responseFormatAttr.trim().length() > 0) {
			mode.setResponseFormat(responseFormatAttr.trim());
		}
		// Parse debugOutput attribute (default false to hide technical details from
		// users)
		if (debugOutputAttr != null && debugOutputAttr.trim().length() > 0) {
			mode.setDebugOutput(Boolean.parseBoolean(debugOutputAttr.trim()));
		}
		// Parse maxHistoryMessages attribute (default 30)
		String maxHistoryMsgAttr = root.getAttribute("maxHistoryMessages");
		if (maxHistoryMsgAttr != null && maxHistoryMsgAttr.trim().length() > 0) {
			try {
				mode.setMaxHistoryMessages(Integer.parseInt(maxHistoryMsgAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid maxHistoryMessages attribute: {}", maxHistoryMsgAttr);
			}
		}
		// Parse maxHistoryTokensK attribute (default 100, unit: K tokens)
		String maxHistoryTokAttr = root.getAttribute("maxHistoryTokensK");
		if (maxHistoryTokAttr != null && maxHistoryTokAttr.trim().length() > 0) {
			try {
				mode.setMaxHistoryTokensK(Integer.parseInt(maxHistoryTokAttr.trim()));
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid maxHistoryTokensK attribute: {}", maxHistoryTokAttr);
			}
		}

		// Parse UI HTML outputs
		NodeList uiNodes = root.getElementsByTagName("ui");
		if (uiNodes.getLength() > 0) {
			Element uiElement = (Element) uiNodes.item(0);
			NodeList welcomeNodes = uiElement.getElementsByTagName("welcome");
			if (welcomeNodes.getLength() > 0) {
				String welcome = getElementContent((Element) welcomeNodes.item(0));
				if (welcome != null && welcome.length() > 0) {
					mode.setUiWelcome(welcome);
				}
			}
			NodeList completeNodes = uiElement.getElementsByTagName("complete");
			if (completeNodes.getLength() > 0) {
				Element completeElement = (Element) completeNodes.item(0);
				String complete = getElementContent(completeElement);
				if (complete != null && complete.length() > 0) {
					mode.setUiComplete(complete);
				}
				String completeTest = completeElement.getAttribute("test");
				if (completeTest != null && completeTest.length() > 0) {
					mode.setUiCompleteTest(completeTest);
				}
			}
		}

		return mode;
	}

	/**
	 * 收集 root 下第一个 &lt;blockTag&gt; 块中的 &lt;itemTag&gt; 定义到 apis 列表。
	 * <p>
	 * override=true 时（用于 &lt;tool&gt;），名称（忽略大小写）已存在的定义被原位替换
	 * （tool 整体覆盖 api）；否则 api 之间不去重，保持原有行为。
	 *
	 * @param root    包含块的元素（mode 或 common）
	 * @param blockTag 块标签名（apis / tools）
	 * @param itemTag  子项标签名（api / tool）
	 * @param apis    收集目标列表
	 * @param index   名称（小写）到列表位置的索引
	 * @param override 同名时是否整体覆盖
	 */
	public static void collectApis(Element root, String blockTag, String itemTag, List<Api> apis,
			Map<String, Integer> index, boolean override) {
		NodeList blockNodes = root.getElementsByTagName(blockTag);
		if (blockNodes.getLength() == 0) {
			return;
		}
		Element blockElement = (Element) blockNodes.item(0);
		NodeList itemNodes = blockElement.getElementsByTagName(itemTag);
		for (int i = 0; i < itemNodes.getLength(); i++) {
			Element itemElement = (Element) itemNodes.item(i);
			Api api = "tool".equals(itemTag) ? parseTool(itemElement) : parseApi(itemElement);
			String key = api.getName() == null ? "" : api.getName().toLowerCase();
			Integer pos = index.get(key);
			if (pos != null && override) {
				// tool 整体覆盖同名 api，保持原位
				apis.set(pos.intValue(), api);
			} else {
				if (pos == null) {
					index.put(key, Integer.valueOf(apis.size()));
				}
				apis.add(api);
			}
		}
	}

	/**
	 * 解析提示列表
	 * 
	 * @param promptsElement prompts元素
	 * @return 提示列表
	 */
	public static List<Prompt> parsePrompts(Element promptsElement) {
		List<Prompt> prompts = new ArrayList<>();
		NodeList promptNodes = promptsElement.getElementsByTagName("prompt");
		for (int i = 0; i < promptNodes.getLength(); i++) {
			Element promptElement = (Element) promptNodes.item(i);
			Prompt prompt = parsePrompt(promptElement);
			prompts.add(prompt);
		}
		return prompts;
	}

	/**
	 * 解析单个提示
	 * 
	 * @param promptElement prompt元素
	 * @return Prompt对象
	 */
	public static Prompt parsePrompt(Element promptElement) {
		String promptName = promptElement.getAttribute("name");
		String role = promptElement.getAttribute("role");
		String description = promptElement.getAttribute("description");
		String sqlRef = promptElement.getAttribute("sqlRef");
		String dataType = promptElement.getAttribute("dataType");
		String prefix = promptElement.getAttribute("prefix");
		String content = getElementContent(promptElement);

		String dataGroupField = promptElement.getAttribute("dataGroupField");
		String action = promptElement.getAttribute("action");
		String api = promptElement.getAttribute("api");
		if (api == null || api.trim().length() == 0) {
			// tool 属性为 api 的别名写法
			api = promptElement.getAttribute("tool");
		}
		String showInChatAttr = promptElement.getAttribute("showInChat");
		String apisCheckAttr = promptElement.getAttribute("apisCheck");
		if (apisCheckAttr == null || apisCheckAttr.trim().length() == 0) {
			// toolsCheck 属性为 apisCheck 的别名写法
			apisCheckAttr = promptElement.getAttribute("toolsCheck");
		}
		Prompt p = new Prompt(promptName, role, description, sqlRef, dataType, prefix, content, action);
		if (dataGroupField != null && dataGroupField.length() > 0) {
			p.setDataGroupField(dataGroupField);
		}
		if (showInChatAttr != null && showInChatAttr.trim().length() > 0) {
			p.setShowInChat(Boolean.parseBoolean(showInChatAttr.trim()));
		}
		if (api != null && api.trim().length() > 0) {
			p.setApi(api.trim());
		}
		if (apisCheckAttr != null && apisCheckAttr.trim().length() > 0) {
			p.setApisCheck(Boolean.parseBoolean(apisCheckAttr.trim()));
		}
		return p;
	}

	/**
	 * 解析单个SQL查询
	 * 
	 * @param sqlElement sql元素
	 * @return SqlQuery对象
	 */
	public static SqlQuery parseSqlQuery(Element sqlElement) {
		String sqlName = sqlElement.getAttribute("name");
		String sqlDescription = sqlElement.getAttribute("description");
		String sqlContent = getElementContent(sqlElement);
		return new SqlQuery(sqlName, sqlDescription, sqlContent);
	}

	/**
	 * 解析SQL查询列表
	 * 
	 * @param sqlsElement sqls元素
	 * @return SQL查询列表
	 */
	public static List<SqlQuery> parseSqlQueries(Element sqlsElement) {
		List<SqlQuery> sqlQueries = new ArrayList<>();
		NodeList sqlNodes = sqlsElement.getElementsByTagName("sql");
		for (int i = 0; i < sqlNodes.getLength(); i++) {
			Element sqlElement = (Element) sqlNodes.item(i);
			SqlQuery sql = parseSqlQuery(sqlElement);
			sqlQueries.add(sql);
		}
		return sqlQueries;
	}

	/**
	 * 获取元素内容的工具方法，处理CDATA
	 * 
	 * @param element 元素
	 * @return 元素的文本内容
	 */
	public static String getElementContent(Element element) {
		StringBuilder content = new StringBuilder();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.CDATA_SECTION_NODE || node.getNodeType() == Node.TEXT_NODE) {
				content.append(node.getTextContent().trim());
			}
		}
		return content.toString();
	}

	/**
	 * 解析API元素
	 * 
	 * @param apiElement API元素
	 * @return API对象
	 */
	public static Api parseApi(Element apiElement) {
		String name = apiElement.getAttribute("name");
		String description = apiElement.getAttribute("description");
		String url = apiElement.getAttribute("url");
		String method = apiElement.getAttribute("method");
		String timeoutStr = apiElement.getAttribute("timeout");
		String refRequestStr = apiElement.getAttribute("refRequest");
		String parameters = apiElement.getAttribute("parameters");
		String key = apiElement.getAttribute("key");

		// 设置默认值
		if (method == null || method.trim().length() == 0) {
			method = "GET";
		}
		int timeout = 5000; // 默认5秒
		if (timeoutStr != null && timeoutStr.trim().length() > 0) {
			try {
				timeout = Integer.parseInt(timeoutStr.trim());
			} catch (NumberFormatException ex) {
				LOGGER.warn("Invalid timeout attribute: {}", timeoutStr);
			}
		}
		boolean refRequest = false;
		if (refRequestStr != null && refRequestStr.trim().length() > 0) {
			refRequest = Boolean.parseBoolean(refRequestStr.trim());
		}

		Api api = new Api(name, description, url);
		api.setMethod(method);
		api.setTimeout(timeout);
		api.setRefRequest(refRequest);
		api.setParameters(parameters);
		api.setKey(key);

		// 解析元素内直接的文本/CDATA 内容作为调用说明（<body>/<headers>/<form> 子元素不受影响）
		String usage = getElementContent(apiElement);
		if (usage != null && usage.length() > 0) {
			api.setUsage(usage);
		}

		// 解析body元素
		NodeList bodyNodes = apiElement.getElementsByTagName("body");
		if (bodyNodes.getLength() > 0) {
			Element bodyElement = (Element) bodyNodes.item(0);
			String body = getElementContent(bodyElement);
			api.setBody(body);
		}

		// 解析headers元素
		NodeList headersNodes = apiElement.getElementsByTagName("headers");
		if (headersNodes.getLength() > 0) {
			Element headersElement = (Element) headersNodes.item(0);
			List<ApiHeader> headers = parseApiHeaders(headersElement);
			api.setHeaders(headers);
		}

		// 解析form元素
		NodeList formNodes = apiElement.getElementsByTagName("form");
		if (formNodes.getLength() > 0) {
			Element formElement = (Element) formNodes.item(0);
			List<ApiField> form = parseApiForm(formElement);
			api.setForm(form);
		}

		return api;
	}

	/**
	 * 解析 &lt;tool&gt; 元素为 Tool 实例（继承 Api 的全部属性，额外支持 command 本地程序命令）
	 *
	 * @param toolElement tool元素
	 * @return Tool对象
	 */
	public static Tool parseTool(Element toolElement) {
		Api base = parseApi(toolElement);
		Tool tool = new Tool();
		tool.setName(base.getName());
		tool.setDescription(base.getDescription());
		tool.setUrl(base.getUrl());
		tool.setMethod(base.getMethod());
		tool.setTimeout(base.getTimeout());
		tool.setRefRequest(base.isRefRequest());
		tool.setParameters(base.getParameters());
		tool.setKey(base.getKey());
		tool.setBody(base.getBody());
		tool.setUsage(base.getUsage());
		tool.setHeaders(base.getHeaders());
		tool.setForm(base.getForm());

		String command = toolElement.getAttribute("command");
		if (command != null && command.trim().length() > 0) {
			tool.setCommand(command.trim());
		}
		return tool;
	}

	/**
	 * 解析API请求头
	 * 
	 * @param headersElement headers元素
	 * @return 请求头列表
	 */
	public static List<ApiHeader> parseApiHeaders(Element headersElement) {
		List<ApiHeader> headers = new ArrayList<>();
		NodeList headerNodes = headersElement.getElementsByTagName("header");
		for (int i = 0; i < headerNodes.getLength(); i++) {
			Element headerElement = (Element) headerNodes.item(i);
			String name = headerElement.getAttribute("name");
			String value = headerElement.getAttribute("value");
			headers.add(new ApiHeader(name, value));
		}
		return headers;
	}

	/**
	 * 解析API表单字段
	 * 
	 * @param formElement form元素
	 * @return 表单字段列表
	 */
	public static List<ApiField> parseApiForm(Element formElement) {
		List<ApiField> form = new ArrayList<>();
		NodeList fieldNodes = formElement.getElementsByTagName("field");
		for (int i = 0; i < fieldNodes.getLength(); i++) {
			Element fieldElement = (Element) fieldNodes.item(i);
			String name = fieldElement.getAttribute("name");
			String value = fieldElement.getAttribute("value");
			form.add(new ApiField(name, value));
		}
		return form;
	}

	/**
	 * 解析paramChecks参数校验定义
	 *
	 * @param paramChecksElement paramChecks元素
	 * @return ParamCheck列表
	 */
	public static List<ParamCheck> parseParamChecks(Element paramChecksElement) {
		List<ParamCheck> paramChecks = new ArrayList<>();
		NodeList paramCheckNodes = paramChecksElement.getElementsByTagName("paramCheck");
		for (int i = 0; i < paramCheckNodes.getLength(); i++) {
			Element pcElement = (Element) paramCheckNodes.item(i);
			String name = pcElement.getAttribute("name");
			String des = pcElement.getAttribute("des");
			String type = pcElement.getAttribute("type");
			String defaultValue = pcElement.getAttribute("default");
			String options = pcElement.getAttribute("options");
			String promptRule = pcElement.getAttribute("promptRule");
			String sqlRef = pcElement.getAttribute("sqlRef");
			String sqlValueField = pcElement.getAttribute("sqlValueField");
			String sqlLabelField = pcElement.getAttribute("sqlLabelField");
			ParamCheck pc = new ParamCheck(name, des, type, defaultValue, options, promptRule);
			pc.setSqlRef(sqlRef);
			pc.setSqlValueField(sqlValueField);
			pc.setSqlLabelField(sqlLabelField);
			paramChecks.add(pc);
		}
		return paramChecks;
	}
}